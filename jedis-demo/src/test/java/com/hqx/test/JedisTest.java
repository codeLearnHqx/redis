package com.hqx.test;

import com.hqx.jedis.util.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * @Description
 * @Create by hqx
 * @Date 2023/7/16 2:43
 */

public class JedisTest {

    private Jedis jedis;

    /* 在@Test方法之前执行 */
    @BeforeEach
    void setUp() {
        // 1.建立连接
        //jedis = new Jedis("192.168.10.100", 6379);
        // 从连接池中获取连接
        jedis = JedisConnectionFactory.getJedis();
        // 2.设置密码
        jedis.auth("123456");
        // 3.选择库
        jedis.select(0); // 默认选择第一个库 即 index=0的库

    }

    @Test
    void testString() {
        // 存入数据
        String result = jedis.set("name", "黄哥");
        System.out.println("result = " + result);
        // 获取数据
        String name = jedis.get("name");
        System.out.println("name = " + name);


    }

    @Test
    void testHash() {
        // 插入hash
        jedis.hset("user:1", "name", "Jack");
        jedis.hset("user:1", "age", "18");
        // 获取
        Map<String, String> map = jedis.hgetAll("user:1");
        System.out.println(map);

    }

    @AfterEach
    void tearDown() {
        // 释放连接
        if (jedis != null) {
            jedis.close();
        }
    }


}
