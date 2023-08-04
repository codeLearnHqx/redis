package com.hmdp.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    @Override
    public Result queryById(Long id) {
        // 1. 缓存穿透
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 2. 互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);

        // 3. 逻辑过期解决缓存击穿问题
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 返回
        return Result.ok(shop);
    }


    // 解决缓存穿透
    /*
    public Shop queryWithPassThrough(Long id) {
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 判断是否存在
        if (CharSequenceUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            // 将JSON字符串转化成java bean
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值 （缓存穿透问题的解决）
        if (shopJson != null) {
            return null;
        }

        // 4. 不存在，根据id查询数据库
        Shop shop = this.getById(id);
        // 5. 不存在，返回错误
        if (shop == null) {
            // 解决缓存穿透问题   方式1：缓存空对象（方式2：布隆过滤）
            // 将空值写入redis  过期时间设置为2分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        // 6. 存在，写入redis
        // 将 java bean 转成JSON字符串后存入redis中，并设置超时时间为30分钟
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7. 返回
        return shop;
    }
    */


    // 解决缓存击穿 --- 通过逻辑过期
    /*// 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 判断是否存在
        if (CharSequenceUtil.isBlank(shopJson)) {
            // 3. 不存在，直接返回
            return null;
        }

        // 4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) { // 判断过期时间是否在当前时间之后
            // 5.1 未过期，直接返回店铺信息
            return shop;
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
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            expireTime = redisData.getExpireTime();
            // 判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 未过期，直接返回店铺信息
                return shop;
            }
            // 6.3.2 开启独立线程，开启缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    this.unLock(lockKey);
                }
            });
        }
        // 6.4 返回过期的店铺信息
        return shop;
    }*/


    // 解决缓存击穿 --- 通过互斥锁
    /*public Shop queryWithMutex(Long id) {
        // 1. 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2. 判断是否存在
        if (CharSequenceUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            // 将JSON字符串转化成java bean
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值 （缓存穿透问题的解决）
        if (shopJson != null) {
            return null;
        }

        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        boolean isLock = false;
        try {
            isLock = tryLock(lockKey);
            // 4.2 判断是否获取成功
            if (!isLock) {
                // 4.3 失败，则休眠并重试
                Thread.sleep(50);
                // 递归
                return queryWithMutex(id);
            }
            // 获取锁成功时
            // DoubleCheck，如果存在无需重建缓存
            // 从redis查询商铺缓存
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            // 判断是否存在
            if (CharSequenceUtil.isNotBlank(shopJson)) {
                // 存在，直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            // 4.4 成功，根据id查询数据库
            shop = this.getById(id);
            // 5. 不存在，返回错误
            if (shop == null) {
                // 解决缓存穿透问题   方式1：缓存空对象（方式2：布隆过滤）
                // 将空值写入redis  过期时间设置为2分钟
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 6. 存在，写入redis
            // 将 java bean 转成JSON字符串后存入redis中，并设置超时时间为30分钟
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 7. 释放互斥锁 （能获取到互斥锁的线程才能释放锁）
            if (isLock) {
                unLock(lockKey);
            }
        }
        // 8. 返回
        return shop;
    }

    // 使用互斥锁来解决缓存击穿的问题
    // 获取锁
    private boolean tryLock(String key) {
        // 设置互斥锁的过期时间为 10秒
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    // 释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 设置数据的逻辑过期时间
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = this.getById(id);
        Thread.sleep(200); // 模拟操作延迟
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/


    @Override
    @Transactional // 开启事务
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1. 更新数据库
        this.updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return null;
    }
}
