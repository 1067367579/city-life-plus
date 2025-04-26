package com.hmdp.redis;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.constants.CacheConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class RedisClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //序列化存储string类型key 带TTL
    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit){
        String str = JSON.toJSONString(value);
        stringRedisTemplate.opsForValue().set(key,str,expireTime,timeUnit);
    }

    //设置逻辑过期 加上序列化存储string类型的key RedisData存储
    public void setWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plus(expireTime, timeUnit.toChronoUnit()));
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(redisData));
    }

    //反序列化 指定类型 存储空值解决缓存穿透问题
    public <R,ID> R get(String key, Class<R> clazz, ID id, Function<ID,R> getFromDB,Long expireTime, TimeUnit timeUnit){
        String json = stringRedisTemplate.opsForValue().get(key);
        //先看是否有缓存，有就直接拿
        if (StrUtil.isNotEmpty(json)) {
            return JSON.parseObject(json,clazz);
        } //没有缓存
        if(json != null) {
            //判断是否是空串 直接返回错误信息 避免缓存穿透 将未加载到缓存的值与不存在的值隔离开来
            return null;
        }
        //去数据库中查 然后返回
        R result = getFromDB.apply(id);
        if (result == null) {
            //缓存穿透解决 设置空值 TTL 查不到
            this.set(key,"", CacheConstants.CACHE_THROUGH_TIME,TimeUnit.MINUTES);
            return null;
        }
        //查得到 放到redis中更新缓存 主动更新+TTL托底
        this.set(key,JSON.toJSONString(result),expireTime,timeUnit);
        return result;
    }

    //线程池
    private final static ExecutorService REFRESH_CACHE_THREAD_POOL = Executors.newCachedThreadPool();

    //反序列化 缓存穿透 逻辑过期解决缓存击穿问题
    public <R,ID> R getWithLogicalExpire(String key,String lockKey, Class<R> clazz, ID id, Function<ID,R> getFromDB,Long expireTime, TimeUnit timeUnit){
        String json = stringRedisTemplate.opsForValue().get(key);
        //先看是否有缓存，有就直接拿
        if (StrUtil.isNotEmpty(json)) {
            RedisData redisData = JSON.parseObject(json, RedisData.class);
            if(redisData != null) {
                if(redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                    //过期了 抢到锁刷新
                    if(tryLock(lockKey)) {
                        REFRESH_CACHE_THREAD_POOL.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    saveObject2Redis(key,id,getFromDB,expireTime,timeUnit);
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                } finally {
                                    unlock(lockKey);
                                }
                            }
                        });
                    }
                    //有无抢到锁 都是直接返回旧值
                    return JSON.parseObject(redisData.getData().toString(),clazz);
                }
                //没过期 直接返回该值
                return JSON.parseObject(redisData.getData().toString(),clazz);
            }
        } //没有缓存
        if(json != null) {
            //判断是否是空串 直接返回错误信息 避免缓存穿透 将未加载到缓存的值与不存在的值隔离开来
            return null;
        }
        //去数据库中查 然后返回
        R result = getFromDB.apply(id);
        if (result == null) {
            //缓存穿透解决 设置空值 TTL 查不到
            this.set(key,"", CacheConstants.CACHE_THROUGH_TIME,TimeUnit.MINUTES);
            return null;
        }
        //查得到 放到redis中更新缓存 主动更新+TTL托底
        this.setWithLogicalExpire(key,JSON.toJSONString(result),expireTime,timeUnit);
        return result;
    }

    private <R,ID> void saveObject2Redis(String key,ID id,Function<ID,R> getFromDB,Long expireTime, TimeUnit timeUnit) {
        //此处为更新逻辑
        R result = getFromDB.apply(id);
        if (result == null) {
            return;
        }
        this.setWithLogicalExpire(key,result,expireTime,timeUnit);
    }

    public boolean tryLock(String key) {
        //获取锁 同时要有一个超时处理机制 这里的粒度较低 时间不要太长
        //使用BooleanUtil类 避免null值导致的异常
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(key,CacheConstants.CACHE_LOCK_FLAG,
                CacheConstants.CACHE_LOCK_TIME,TimeUnit.SECONDS));
    }

    public void unlock(String key) {
        //释放锁直接删除这个key即可
        stringRedisTemplate.delete(key);
    }
}
