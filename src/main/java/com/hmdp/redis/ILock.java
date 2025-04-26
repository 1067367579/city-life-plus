package com.hmdp.redis;

public interface ILock {
    boolean tryLock(Long expireTime);
    void unlock();
}
