package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.CacheConstants;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.dto.ScrollResult;
import com.hmdp.domain.entity.Blog;
import com.hmdp.domain.entity.User;
import com.hmdp.domain.vo.UserVO;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserServiceImpl userServiceImpl;

    @Override
    public Result queryBlogById(Long id) {
        // 1.查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        // 2.查询blog有关的用户
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    public void likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        //利用redis来进行判断用户是否已经点过赞
        String key = CacheConstants.BLOG_LIKE_KEY + id;
        //去redis中获取
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //如果获取不到分数 返回空值 就说明根本没有这个key
        //不需要严格的校验
        if (score != null) { //避免null的问题
            //已经点赞就取消点赞
            boolean success = this.update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) {
                //数据库操作成功后 才更新缓存 保持一致性
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            boolean success = this.update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                //数据库操作成功后 才更新缓存 保持一致性 用时间戳作为分数 保证看到的点赞顺序是正确的
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),System.currentTimeMillis());
            }
        }
    }

    //核心 点赞使用zset类型进行维护 保证了唯一性 有序性
    @Override
    public Result queryBlogLikes(Long id) {
        //查出set集合中点赞的用户ID
        Set<String> set = stringRedisTemplate.opsForZSet().range(CacheConstants.BLOG_LIKE_KEY+id, 0, 4);
        if(CollectionUtil.isEmpty(set)){
            return Result.ok();
        }
        //按照顺序 拼接字符串 严格按照从zset中读出来的顺序进行获取 已经是有序的
        List<String> list = new ArrayList<>(set);
        //拼接 拼接的结果串是随后sql从数据库中拿出数据的依据
        String listStr = StrUtil.join(",", list);
        //获取 通过特殊的sql last
        List<UserVO> userVOList = userServiceImpl.query()
                .in("id",list).last("order by field(id,"+listStr+")").list()
                .stream().map(user -> BeanUtil.toBean(user, UserVO.class)).toList();
        return Result.ok(userVOList);
    }

    @Override
    public Result getFollowBlog(Long lastId, Integer offset) {
        //实现滚动分页查询
        Long userId = UserHolder.getUser().getId();
        //组装key
        String key = CacheConstants.FEED_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<String>> blogTuples = stringRedisTemplate.opsForZSet().
                reverseRangeByScoreWithScores(key, 0, lastId, offset, 2);
        long minTime = 0;
        int newOffset = 1;
        List<Long> ids = new ArrayList<>();
        if(CollectionUtil.isEmpty(blogTuples)){
            return Result.ok(Collections.emptyList());
        }
        for (ZSetOperations.TypedTuple<String> blogTuple : blogTuples) {
            long score = (long) Double.parseDouble(String.valueOf(blogTuple.getScore()));
            ids.add(Long.valueOf(Objects.requireNonNull(blogTuple.getValue())));
            if(minTime == score) {
                newOffset++;
            } else {
                minTime = score;
                newOffset = 1;
            }
        }
        //准备下一页的起点
        newOffset = minTime == lastId ? newOffset : offset+newOffset;
        //获取出博客ID 查询博客内容 严格按照顺序进行查询 滚动分页需求
        String blogIdStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + blogIdStr + ")").list();
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(newOffset);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    public void queryBlogUser(Blog blog) {
        User user = userMapper.selectById(blog.getUserId());
        if (user != null) {
            blog.setIcon(user.getIcon());
            blog.setName(user.getNickName());
        }
    }
}
