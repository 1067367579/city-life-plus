package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.Follow;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long userId, Boolean isFollowed);

    Result followOrNot(Long userId);

    Result commonFollow(Long userId);
}
