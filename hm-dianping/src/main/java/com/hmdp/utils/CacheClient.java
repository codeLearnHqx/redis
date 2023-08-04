package com.hmdp.utils;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Description  redis缓存相关工具类
 * @Create by hqx
 * @Date 2023/8/4 23:24
 */

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param key redis中的key
     * @param value redis中的value
     * @param time 过期时间
     * @param unit 时间的单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     * @param key redis中存储所需的key
     * @param value redis中存储所需的value
     * @param time  逻辑过期时间
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // unit.toSeconds(time) 将time转换成秒数
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix redis中存储key的前缀
     * @param id 实际数据的id
     * @param type 需要返回数据的类型
     * @param dbFallback 查询数据库的回调函数
     * @param time 缓存失效时间
     * @param unit 失效时间的单位
     * @return R类型的数据
     * @param <R> 返回值类型
     * @param <ID> 查询数据库传入参数的类型
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit) {
        String key = keyPrefix + String.valueOf(id);
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (CharSequenceUtil.isNotBlank(json)) {
            // 3. 存在，直接返回
            // 将JSON字符串转化成java bean
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否是空值 （缓存穿透问题的解决）
        if (json != null) {
            // 返回空值
            return null;
        }

        // 4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        // 5. 不存在，返回错误
        if (r == null) {
            // 解决缓存穿透问题   方式1：缓存空对象（方式2：布隆过滤）
            // 将空值写入redis  过期时间设置为2分钟
            stringRedisTemplate.opsForValue().set(key, "", time, unit);

            return null;
        }
        // 6. 存在，写入redis
        // 将 java bean 转成JSON字符串后存入redis中，并设置超时时间为30分钟
        this.set(key, r, time, unit);
        // 7. 返回
        return r;
    }



    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     * @param keyPrefix redis中存储key的前缀
     * @param id 数据的id
     * @param type 需要返回的数据类型
     * @param dbFallback 根据id查询数据库的回调函数
     * @param time 缓存的逻辑过期时间
     * @param unit 过期时间的单位
     * @return 返回type类型的对象
     * @param <R> 返回数据的类型
     * @param <ID> 数据的id类型
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time, TimeUnit unit) {
        String key = keyPrefix + String.valueOf(id);
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (CharSequenceUtil.isBlank(json)) {
            // 3. 不存在，直接返回
            return null;
        }

        // 4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) { // 判断过期时间是否在当前时间之后
            // 5.1 未过期，直接返回店铺信息
            return r;
        }
        // 5.2 已过期，需要缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取成功
        if (isLock) {
            // 6.3 获取互斥锁成功
            // 6.3.1 DoubleCheck，再次检测redis缓存是否过期（存在其他线程重建缓存后释放锁的瞬间被当前线程获取到互斥锁的可能）
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            // 判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期，直接返回店铺信息
                return r;
            }
            // 6.3.2 开启独立线程，开启缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    this.unLock(lockKey);
                }
            });
        }
        // 6.4 返回过期的店铺信息
        return r;
    }

    // 使用互斥锁来解决缓存击穿的问题
    // 获取锁
    public boolean tryLock(String key) {
        // 设置互斥锁的过期时间为 10秒
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    public void unLock(String key) {
        stringRedisTemplate.delete(key);
    }







}
