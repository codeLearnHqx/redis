package com.hmdp.config.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @Description
 * @Create by hqx
 * @Date 2023/8/1 23:56
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
        HttpSession session = request.getSession();
        // 2. 获取session中的用户
        User user = (User)session.getAttribute("user");
        // 3. 判断用户是否存在
        if (ObjectUtil.isEmpty(user)) {
            // 4. 不存在，拦截  返回401状态码（未授权的意思）
            response.setStatus(401);
            return false;
        }
        // 5. 存在，保存用户信息到ThreadLocal
        UserDTO userDTO = new UserDTO();
        BeanUtil.copyProperties(user, userDTO);
        UserHolder.saveUser(userDTO);
        // 6. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户（防止内存泄漏）
        UserHolder.removeUser();

    }
}
