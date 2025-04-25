package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.hmdp.constants.CacheConstants;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.redis.RedisClient;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisClient redisClient;

    public Result queryShopById(Long id) {
        return Result.ok(redisClient.getWithLogicalExpire(
                CacheConstants.SHOP_CACHE_PREFIX+id,
                CacheConstants.SHOP_LOCK_PREFIX+id,
                Shop.class,
                id,
                this::getById,
                CacheConstants.SHOP_EXPIRE_TIME,
                TimeUnit.MINUTES
        ));
    }


    //普通的缓存查询
    public Result queryShopByIdNormal(Long id) {
        //先看是否有缓存，有就直接拿
        String shopKey = CacheConstants.SHOP_CACHE_PREFIX+id;
        String shopStr = stringRedisTemplate.opsForValue().get(shopKey);
        Shop shop = null;
        //缓存拿得到就直接返回
        if (StrUtil.isNotEmpty(shopStr)) {
            shop = JSON.parseObject(shopStr, Shop.class);
            return Result.ok(shop);
        }
        //去数据库中查 然后返回
        shop = getById(id);
        if (shop == null) {
            return Result.fail("该商店不存在！");
        }
        //查得到 放到redis中更新缓存 主动更新+TTL托底
        stringRedisTemplate.opsForValue().set(CacheConstants.SHOP_CACHE_PREFIX+id, JSON.toJSONString(shop),
                30L, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    //带缓存的更新 使用删除策略 主动更新缓存+TTL托底
    @Transactional
    public Result updateShop(Shop shop) {
        //先更新数据库 再进行缓存的删除 是较为线程安全的 数据库操作慢 容易被穿插导致缓存数据库数据不一致
        Shop shopDB = this.getById(shop.getId());
        if(shopDB == null){
            return Result.fail("商户不存在!");
        }
        //校验完成，进行更新
        this.updateById(shop);
        stringRedisTemplate.delete(CacheConstants.SHOP_CACHE_PREFIX+shop.getId());
        return Result.ok();
    }

    //缓存穿透问题 解决方法：设置空对象
    //缺点： 短时不一致问题 空间占用多问题（短TTL缓解）
    public Result queryShopWithPassThrough(Long id) {
        //先看是否有缓存，有就直接拿
        String shopKey = CacheConstants.SHOP_CACHE_PREFIX+id;
        String shopStr = stringRedisTemplate.opsForValue().get(shopKey);
        Shop shop = null;
        //缓存拿得到就直接返回
        if (StrUtil.isNotEmpty(shopStr)) {
            shop = JSON.parseObject(shopStr, Shop.class);
            return Result.ok(shop);
        }
        if(shopStr != null) {
            //判断是否是空串 直接返回错误信息 避免缓存穿透 将未加载到缓存的值与不存在的值隔离开来
            return Result.fail("该商店不存在！");
        }
        //去数据库中查 然后返回
        shop = getById(id);
        if (shop == null) {
            //缓存穿透解决 设置空值 TTL 查不到
            stringRedisTemplate.opsForValue().set(CacheConstants.SHOP_CACHE_PREFIX+id,
                    "",2,TimeUnit.MINUTES);
            return Result.fail("该商店不存在！");
        }
        //查得到 放到redis中更新缓存 主动更新+TTL托底
        stringRedisTemplate.opsForValue().set(CacheConstants.SHOP_CACHE_PREFIX+id, JSON.toJSONString(shop),
                30L, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    //缓存雪崩 直接使用随机TTL和集群进行配合 限流 多级缓存等机制

    //线程池
    private final static ExecutorService REFRESH_CACHE_THREAD_POOL = Executors.newCachedThreadPool();

    //缓存击穿 使用互斥锁或者逻辑过期机制，互斥锁性能低，可能会产生死锁 此处使用逻辑过期方案
    public Result queryShopWithLogicalExpire(Long id) {
        //先看是否有缓存，有就直接拿
        String shopKey = CacheConstants.SHOP_CACHE_PREFIX+id;
        String shopStr = stringRedisTemplate.opsForValue().get(shopKey);
        Shop shop = null;
        //缓存拿得到就直接返回
        if (StrUtil.isNotEmpty(shopStr)) {
            RedisData redisData = JSON.parseObject(shopStr, RedisData.class);
            //检查是否过期
            if (redisData != null && redisData.getExpireTime().isBefore(LocalDateTime.now())) {
                //过期的key 通知更新
                //尝试获取锁
                if(tryLock(CacheConstants.SHOP_LOCK_PREFIX+id)) {
                    //获取锁成功后 通知异步线程进行更新 此处使用线程池的方式 性能高 不用自己创建线程
                    REFRESH_CACHE_THREAD_POOL.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                saveShop2Redis(id);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            } finally {
                                //解锁 一定要进行解锁操作
                                unlock(CacheConstants.SHOP_LOCK_PREFIX+id);
                            }
                        }
                    });
                }
                //返回旧值 没有获取到锁
                return Result.ok(JSONUtil.toBean(redisData.getData().toString(),Shop.class));
            }
            //没有过期的话 直接返回即可
            return Result.ok(JSONUtil.toBean(redisData.getData().toString(),Shop.class));
        }
        if(shopStr != null) {
            //判断是否是空串 直接返回错误信息 避免缓存穿透 将未加载到缓存的值与不存在的值隔离开来
            return Result.fail("该商店不存在！");
        }
        //去数据库中查 然后返回
        shop = getById(id);
        if (shop == null) {
            //缓存穿透解决 设置空值 TTL 查不到
            stringRedisTemplate.opsForValue().set(CacheConstants.SHOP_CACHE_PREFIX+id,
                    "",2,TimeUnit.MINUTES);
            return Result.fail("该商店不存在！");
        }
        //查得到 放到redis中更新缓存 主动更新+TTL托底
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(30L));
        redisData.setData(shop);
        stringRedisTemplate.opsForValue().set(CacheConstants.SHOP_CACHE_PREFIX+id, JSON.toJSONString(redisData));
        return Result.ok(shop);
    }

    private void saveShop2Redis(Long id) {
        //此处为更新逻辑
        Shop shopDB = getById(id);
        if (shopDB == null) {
            return;
        }
        RedisData tmpData = new RedisData();
        //设置过期时间
        tmpData.setExpireTime(LocalDateTime.now().plusMinutes(30L));
        tmpData.setData(shopDB);
        stringRedisTemplate.opsForValue().set(CacheConstants.SHOP_CACHE_PREFIX+id, JSON.toJSONString(tmpData));
    }

    public boolean tryLock(String key) {
        //获取锁 同时要有一个超时处理机制 这里的粒度较低 时间不要太长
        //使用BooleanUtil类 避免null值导致的异常
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10L,TimeUnit.SECONDS));
    }

    public void unlock(String key) {
        //释放锁直接删除这个key即可
        stringRedisTemplate.delete(key);
    }
}
