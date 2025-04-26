package com.hmdp.redis;

import com.hmdp.constants.Constants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private final Long beginSecond;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        LocalDateTime beginTime = LocalDateTime.of(2024,4,26,0,0,0);
        beginSecond = beginTime.toEpochSecond(ZoneOffset.UTC);
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long nextId(String namePrefix) {
        //时间戳
        Long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        Long timestamp = nowSecond - beginSecond;
        //序列号 依赖redis的自增方法实现 分天创建主键 避免主键触及上限
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "incr:"+namePrefix+":"+date;
        Long increment = stringRedisTemplate.opsForValue().increment(key, 1); //返回当前序列号
        //拼接
        return (timestamp << Constants.ID_BIT_PER_SECOND) | increment;
    }
}
