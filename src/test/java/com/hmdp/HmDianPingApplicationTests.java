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
        System.out.println(System.currentTimeMillis());
    }

}
