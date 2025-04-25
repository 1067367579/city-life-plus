package com.hmdp;

import com.hmdp.redis.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    void contextLoads() {
        for (int i = 0; i < 300; i++) {
            Long orderId = redisIdWorker.nextId("order");
            System.out.println(orderId);
        }
    }

}
