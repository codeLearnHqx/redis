package com.hmdp.config.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @Description 拦截部分请求，主要用于拦截未登录的用户访问了需要登录的页面发送的请求
 * @Create by hqx
 * @Date 2023/8/1 23:56
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 判断是否需要拦截（ThreadLocal是否有用户）
        if (UserHolder.getUser() == null) {
            // 没有用户信息，需要拦截， 设置状态码
            response.setStatus(401);
            return false;
        }
        // 2. 有用户就放行
        log.info("拦截---------login");
        return true;
    }

}
