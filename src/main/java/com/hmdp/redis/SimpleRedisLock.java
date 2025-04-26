package com.hmdp.redis;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private final StringRedisTemplate stringRedisTemplate;
    private final String name;
    private static final String LOCK_PREFIX = "lock:";
    //进程标识号 锁的持有者标识由进程标识+线程ID组成
    private static final String PROCESS_ID = UUID.randomUUID().toString();
    private static final DefaultRedisScript<Long> script;

    static {
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("unlock.lua"));
        script.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    //加锁，返回是否成功
    public boolean tryLock(Long expireTime) {
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(
                LOCK_PREFIX+name,
                PROCESS_ID+Thread.currentThread().getId(),
                expireTime,
                TimeUnit.SECONDS
        ));
    }

    //解锁，先判断当前操作释放锁的线程是不是加锁线程，再进行解锁
    //还存在FullGC等特殊情况造成的瞬时阻塞问题，判断和删除并非原子性操作，导致又有其他线程释放掉本线程锁的情况，引发线程安全问题
    //使用lua脚本实现原子性操作
    public void unlock() {
//        String currentId = PROCESS_ID + Thread.currentThread().getId();
//        String lockerId = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + name);
//        if (lockerId != null && lockerId.equals(currentId)) {
//            stringRedisTemplate.delete(LOCK_PREFIX + name);
//            return true;
//        }
//        return false;
        stringRedisTemplate.execute(script,
                Collections.singletonList(LOCK_PREFIX+name),
                PROCESS_ID+Thread.currentThread().getId()
        );
    }

}
