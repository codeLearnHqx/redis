package com.hmdp.config;

import com.hmdp.config.interceptor.LoginInterceptor;
import com.hmdp.config.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Description
 * @Create by hqx
 * @Date 2023/8/2 0:06
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     *  配置拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /*
            默认按照拦截器的添加顺序执行，也可以通过order进行优先级设置
         */

        // 后执行，拦截部分请求
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(  // 排除拦截
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/upload/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                ).order(1);
        // 先执行，拦截所有请求
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }

}
