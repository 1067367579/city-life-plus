package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

    //@Test
    void contextLoads() {
        System.out.println(System.currentTimeMillis());
    }

    //@Test
    void loadShop() {
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>());
        shops.forEach(shop -> {
            stringRedisTemplate.opsForGeo().add("shop:geo:"+shop.getTypeId()
                    ,new Point(shop.getX(),shop.getY()),
                    shop.getId().toString()
            );
        });
    }

    //UV统计方法测试
    //@Test
    void UVStatistic() {
        //说白了就是一个超级大的计数器 占用极少的空间 16kb 来实现大规模计数 并且还带有去重
        //可以用于统计用户量 去重操作
        String[] users = new String[1000];
        int index = 0;
        for(int i=0;i<100_0000;i++){
            users[index++] = "users_"+i;
            //每1000个发送一次
            if(i%1000 == 0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("userCount",users);
            }
        }
        //统计最后的用户量有多大
        Long size = stringRedisTemplate.opsForHyperLogLog().size("userCount");
        System.out.println(size);
    }

    @Test
    void caffeineTest() {
        Cache<String,String> cache = Caffeine.newBuilder().build();
        cache.put("gf","aaa");
        String gf = cache.getIfPresent("gf");
        System.out.println("gf="+gf);
        //本地缓存中没查到 就到数据库中进行查询
        String defaultGF = cache.get("defaultGF",key->{
            //数据库查询逻辑
           return "bbb";
        });
        System.out.println("defaultGF="+defaultGF);
        StringBuilder sb = new StringBuilder();
    }

}
