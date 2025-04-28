package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.CacheConstants;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.Follow;
import com.hmdp.domain.entity.User;
import com.hmdp.domain.vo.UserVO;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result follow(Long userId, Boolean isFollow) {
        User user = userMapper.selectById(userId);
        if(user == null){
            return Result.fail("用户不存在！");
        }
        Long id = UserHolder.getUser().getId();
        //判断是关注 还是 取关 根据isFollow变量决定
         if(isFollow){
             //关注
             Follow follow = new Follow();
             follow.setUserId(id);
             follow.setFollowUserId(userId);
             int insert = followMapper.insert(follow);
             if(insert == 1){
                 //操作redis 将关注的用户ID放到关注集合中
                 stringRedisTemplate.opsForSet().add(CacheConstants.FOLLOW_KEY_PREFIX+id,
                         String.valueOf(userId));
             }
         } else {
             //取关
             int delete = followMapper.delete(new LambdaQueryWrapper<Follow>()
                     .eq(Follow::getUserId, id)
                     .eq(Follow::getFollowUserId, userId)
             );
             if(delete == 1){
                 stringRedisTemplate.opsForSet().remove(CacheConstants.FOLLOW_KEY_PREFIX+id,userId.toString());
             }
         }
        return Result.ok();
    }

    @Override
    public Result followOrNot(Long userId) {
        Long result = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId,UserHolder.getUser().getId())
                .eq(Follow::getFollowUserId,userId)
        );
        return Result.ok(result > 0);
    }

    @Override
    public Result commonFollow(Long userId) {
        Long currentUserId = UserHolder.getUser().getId();
        //去redis中查交集即可
        String key1 = CacheConstants.FOLLOW_KEY_PREFIX+currentUserId;
        String key2 = CacheConstants.FOLLOW_KEY_PREFIX+userId;
        //查询交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(CollectionUtil.isEmpty(intersect)){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        //不空 就查询出这些用户的情况
        List<UserVO> userVOS = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, ids)
        ).stream().map(user -> BeanUtil.toBean(user, UserVO.class)).toList();
        return Result.ok(userVOS);
    }
}
