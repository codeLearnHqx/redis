package com.hmdp.config.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Description 拦截所有请求，主要用于刷新已登录用户的token有效期，发送任何请求都会刷新登录有效期
 * @Create by hqx
 * @Date 2023/8/1 23:56
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    // 在配置此拦截器的时候传入进来, 自己手动new 出来的拦截器中无法使用自动注入StringRedisTemplate对象
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (CharSequenceUtil.isNotEmpty(token)) {
            // 2. 基于token获取redis中的用户
            String tokenKey = LOGIN_USER_KEY + token;
            // 获取当前hashKey对应的所有键值对，即整个对象
            Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
            // 3. 判断用户是否存在
            if (!userMap.isEmpty()) {
                // 5. 将查询到的Hash数据转成UserDTO对象
                UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
                // 6. 存在，保存用户信息到ThreadLocal
                UserHolder.saveUser(userDTO);
                // 7. 刷新token有效期（有效期为30分钟）
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
            }
        }
        // 8. 放行

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除本地线程中的用户信息（防止内存泄漏）
        UserHolder.removeUser();

    }
}
