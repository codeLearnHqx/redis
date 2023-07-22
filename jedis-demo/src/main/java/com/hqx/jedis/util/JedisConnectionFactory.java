package com.hqx.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * @Description
 * @Create by hqx
 * @Date 2023/7/22 16:15
 */
public class JedisConnectionFactory {

    private static final JedisPool jedisPool;

    static {
        // 配置连接池
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8); // 最大连接数
        poolConfig.setMaxIdle(8); // 最大空闲连接
        poolConfig.setMinIdle(0); // 最小空闲连接
        poolConfig.setMaxWaitMillis(1000); // 获取连接的最大等待时间

        // 创建连接池对象
        jedisPool = new JedisPool(poolConfig, "192.168.10.100", 6379, 1000, "123456");
    }

    /* 连接池通过连接池来获取连接 */
    public static Jedis getJedis() {
        return jedisPool.getResource();
    }

}
