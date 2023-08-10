package com.hmdp.utils;

/**
 * 分布式锁
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁持有的超时时间
     * @return true代表获取锁成功；false代表锁获取失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();


}
