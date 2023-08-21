package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Description 在集群模式下为防止同一用户重复下单
 * @Create by hqx
 * @Date 2023/8/7 7:51
 */

public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-"; // uuid
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT; // lua脚本

    static {
        // 创建lua脚本对象
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 加载lua脚本
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 设置返回值类型
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程的标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue().
                setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 包装类自动拆箱有空指针的风险，所以手动拆箱
        return Boolean.TRUE.equals(success);
    }


    /**
     * 基于lua脚本，保证if判断和释放锁的原子性，解决 Full GC 导致的特殊情况，即锁的误删问题
     *
     */
    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT, // lua脚本
                Collections.singletonList(KEY_PREFIX + name), // 锁key
                ID_PREFIX + Thread.currentThread().getId() // 线程标识
        );

    }
    //@Override
    //public void unlock() {
    //    // 获取线程标识
    //    String threadId = ID_PREFIX + Thread.currentThread().getId();
    //    // 获取锁中的标识
    //    String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //    // 判断标识是否一致
    //    if (threadId.equals(id)) {
    //        // 释放锁
    //        stringRedisTemplate.delete(KEY_PREFIX + name);
    //    }
    //}
}
