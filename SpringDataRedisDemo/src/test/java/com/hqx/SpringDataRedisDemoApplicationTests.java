package com.hqx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hqx.redis.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

@SpringBootTest
class SpringDataRedisDemoApplicationTests {

    /*
    * StringRedisTemplate不用通过配置类来自定义序列化器，但需要我们手动序列化java对象
    * */

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testString() {
      // 写入一条String数据
        stringRedisTemplate.opsForValue().set("name", "黄1哥");
      // 获取String数据
        Object name = stringRedisTemplate.opsForValue().get("name");
        System.out.println("name=" + name);
    }

    @Test
    void testSaveUser() throws JsonProcessingException {
        // 创建对象
        User user = new User("黄哥", 21);
        // 手动序列化
        String json = mapper.writeValueAsString(user);
        // 写入数据
        stringRedisTemplate.opsForValue().set("user:100", json);
        // 获取数据
        String jsonUser = stringRedisTemplate.opsForValue().get("user:100");
        // 手动反序列化
        User user1 = mapper.readValue(jsonUser, User.class);
        System.out.println("o=" + user1);
    }

    @Test
    void testHash() {
        stringRedisTemplate.opsForHash().put("user:200", "name", "黄哥");
        stringRedisTemplate.opsForHash().put("user:200", "age", "22");

        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries("user:200");
        System.out.println(entries);

    }


}
