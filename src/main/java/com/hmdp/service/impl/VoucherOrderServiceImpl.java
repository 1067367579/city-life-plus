package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.domain.dto.CreateOrderDTO;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.SeckillVoucher;
import com.hmdp.domain.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.rabbit.OrderProducer;
import com.hmdp.redis.RedisIdWorker;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;

    @Autowired
    private RedisIdWorker redisIdWorker;

    //自定义分布式锁对象
    //private ILock lock;

    //Redisson的主要API
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_JUDGE_SCRIPT = new DefaultRedisScript<>();

    static {
        SECKILL_JUDGE_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_JUDGE_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private OrderProducer orderProducer;

    //秒杀优化 利用lua脚本和缓存 异步下单方式
    @Override
    public Result seckillVoucher(Long voucherId) {
        //下单 时间判断 库存判断 一人一单 限制
        //1. 查询优惠券详情 比对时间
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("活动还未开始! ");
        }
        if(endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束! ");
        }
        Long userId = UserHolder.getUser().getId();
        //2. 判断库存和一人一单 执行lua脚本
        Long exeResult = stringRedisTemplate.execute(
                SECKILL_JUDGE_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        if(exeResult == null) {
            return Result.fail("系统异常！");
        }
        if(exeResult != 0) {
            return exeResult == 1 ? Result.fail("库存不足"):Result.fail("不可重复下单");
        }
        //使用IdWork生成订单ID
        Long orderId = redisIdWorker.nextId("order");
        CreateOrderDTO createOrderDTO = new CreateOrderDTO();
        createOrderDTO.setOrderId(orderId);
        createOrderDTO.setUserId(userId);
        createOrderDTO.setVoucherId(voucherId);
        //发送消息到消息队列 异步创建订单
        orderProducer.produceOrder(createOrderDTO);
        return Result.ok(orderId);
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //下单 时间判断 库存判断 一人一单 限制
//        //1. 查询优惠券详情
//        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
//        LocalDateTime beginTime = voucher.getBeginTime();
//        LocalDateTime endTime = voucher.getEndTime();
//        if(beginTime.isAfter(LocalDateTime.now())) {
//            return Result.fail("活动还未开始! ");
//        }
//        if(endTime.isBefore(LocalDateTime.now())) {
//            return Result.fail("活动已经结束! ");
//        }
//        //此处不需要使用数据版本作为乐观锁的实现，太过严格，只需要保证库存大于0 不会超卖即可
//        //下单操作 此处需要加锁 按照用户粒度加锁即可 即判断用户是否有下单过
//        //先判断是否有下单过
//        Long userId = UserHolder.getUser().getId();
//        Long orderId;
//        //加锁要转换为String对象 此处强制使用常量池中的对象 避免太频繁创建
//        //此处涉及多个sql 要进行事务管理 比如修改和创建订单是一个事务里面的
//        //分布式锁实现 JVM内置锁无法满足多个进程线程安全问题的需要
//        //使用Redisson创建分布式锁对象
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        try {
//            //失败 不等待 此处最好不指定锁的过期时间，会自动使用看门狗机制续约
//            boolean lockResult = lock.tryLock();
//            if(!lockResult) {
//                //加锁失败 直接返回错误
//                return Result.fail("不可重复购入多次优惠券！");
//            }
//            Long count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
//            if(count > 0) {
//                //已经买过，不可再买 一人一单
//                return Result.fail("不可重复购入多次优惠券！");
//            }
//            //事务是通过代理对象实现的此处要使用增强的代理对象
//            IVoucherOrderService orderService = (IVoucherOrderService)AopContext.currentProxy();
//            orderId = orderService.createOrder(voucherId);
//            if(orderId == null) {
//                return Result.fail("库存不足！");
//            }
//        } catch (Exception ex) {
//            throw new RuntimeException(ex);
//        } finally {
//            lock.unlock();
//        }
//        return Result.ok(orderId);
//    }
}
