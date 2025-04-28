package com.hmdp.controller;


import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.Follow;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    IFollowService followService;

    @PutMapping("/{userId}/{isFollow}") //关注取关二合一
    public Result follow(@PathVariable("userId") Long userId,@PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(userId,isFollow);
    }

    //检查是否关注
    @GetMapping("/or/not/{userId}")
    public Result followOrNot(@PathVariable("userId") Long userId) {
        return followService.followOrNot(userId);
    }

    //查看共同关注
    @GetMapping("/common/{userId}")
    public Result commonFollow(@PathVariable("userId") Long userId) {
        return followService.commonFollow(userId);
    }


}
