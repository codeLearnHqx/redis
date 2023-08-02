package com.hmdp.config;

import com.hmdp.config.interceptor.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @Description
 * @Create by hqx
 * @Date 2023/8/2 0:06
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /**
     *  配置拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(  // 排除拦截
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/upload/**",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                );

    }
}
