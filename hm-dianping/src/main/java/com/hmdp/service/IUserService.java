package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     * @param phone 电话
     * @param session 会话
     * @return 统一的结果对象
     */
    Result sendCode(String phone);

    /**
     * 登录
     * @param loginForm 登录表单数据
     * @return
     */
    Result login(LoginFormDTO loginForm);

    Result sgin();

    Result signCount();
}
