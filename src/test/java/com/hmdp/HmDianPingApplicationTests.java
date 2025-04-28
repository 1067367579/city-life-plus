package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.domain.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.redis.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopMapper shopMapper;

    @Test
    void contextLoads() {
        System.out.println(System.currentTimeMillis());
    }

    @Test
    void loadShop() {
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>());
        shops.forEach(shop -> {
            stringRedisTemplate.opsForGeo().add("shop:geo:"+shop.getTypeId()
                    ,new Point(shop.getX(),shop.getY()),
                    shop.getId().toString()
            );
        });
    }

}
