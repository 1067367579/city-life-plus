package com.hmdp.rabbit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.constants.CacheConstants;
import com.hmdp.constants.RabbitConstants;
import com.hmdp.domain.dto.CreateOrderDTO;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.OrderComparator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@EnableAspectJAutoProxy(exposeProxy = true)
@Slf4j
public class OrderConsumer {

    @Autowired
    RedissonClient redissonClient;

    @Autowired
    VoucherOrderMapper voucherOrderMapper;

    @RabbitListener(queues = RabbitConstants.SECKILL_QUEUE_NAME)
    public void consumerOrder(CreateOrderDTO createOrderDTO) {
        log.info("收到创建订单的消息: {}", createOrderDTO);
        Long userId = createOrderDTO.getUserId();
        Long voucherId = createOrderDTO.getVoucherId();
        Long orderId = createOrderDTO.getOrderId();
        //Redisson加锁兜底 避免还是出现安全问题 大概率不会出现
        RLock lock = redissonClient.getLock(CacheConstants.ORDER_LOCK_PREFIX + userId);
        try {
            //失败 不等待 此处最好不指定锁的过期时间，会自动使用看门狗机制续约
            boolean lockResult = lock.tryLock();
            if(!lockResult) {
                //加锁失败 直接返回错误
                throw new RuntimeException("处理订单异常!");
            }
            Long count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                    .eq(VoucherOrder::getVoucherId, voucherId)
                    .eq(VoucherOrder::getUserId, userId)
            );
            if(count > 0) {
                //已经买过，不可再买 一人一单
               throw new RuntimeException("重复购入多次优惠券！");
            }
            OrderConsumer consumer = (OrderConsumer)AopContext.currentProxy();
            boolean result = consumer.createOrder(voucherId, orderId, userId);
            if(!result) {
                throw new RuntimeException("库存不足！");
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean createOrder(Long voucherId,Long orderId,Long userId) {
        //直接使用sql进行更新
        int result = voucherOrderMapper.updateStock(voucherId);
        if(result == 0) {
            return false;
        }
        //创建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrderMapper.insert(voucherOrder);
        return true;
    }
}
