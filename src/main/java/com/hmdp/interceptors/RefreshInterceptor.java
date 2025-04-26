package com.hmdp.interceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.constants.CacheConstants;
import com.hmdp.domain.vo.UserVO;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class RefreshInterceptor implements HandlerInterceptor {

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if(StrUtil.isNotEmpty(token)) {
            //从redis中获取出 如果还有 就续签
            String key = CacheConstants.LOGIN_TOKEN_PREFIX + token;
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
            if(CollectionUtil.isEmpty(entries)) {
                return true;
            }
            UserVO userVO = BeanUtil.fillBeanWithMap(entries,new UserVO(),false);
            UserHolder.saveUser(userVO);
            stringRedisTemplate.expire(key,30L, TimeUnit.MINUTES);
        }
        //如果没有 也是直接放行
        return true;
    }
}
