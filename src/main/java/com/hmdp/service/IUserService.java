package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.domain.dto.LoginFormDTO;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String email);

    Result login(LoginFormDTO loginForm);
}
