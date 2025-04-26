package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.SeckillVoucher;
import com.hmdp.domain.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.redis.ILock;
import com.hmdp.redis.RedisIdWorker;
import com.hmdp.redis.SimpleRedisLock;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@EnableAspectJAutoProxy(exposeProxy = true)
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

    @Override
    public Result seckillVoucher(Long voucherId) {
        //下单 时间判断 库存判断 一人一单 限制
        //1. 查询优惠券详情
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        LocalDateTime beginTime = voucher.getBeginTime();
        LocalDateTime endTime = voucher.getEndTime();
        if(beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("活动还未开始! ");
        }
        if(endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束! ");
        }
        //此处不需要使用数据版本作为乐观锁的实现，太过严格，只需要保证库存大于0 不会超卖即可
        //下单操作 此处需要加锁 按照用户粒度加锁即可 即判断用户是否有下单过
        //先判断是否有下单过
        Long userId = UserHolder.getUser().getId();
        Long orderId;
        //加锁要转换为String对象 此处强制使用常量池中的对象 避免太频繁创建
        //此处涉及多个sql 要进行事务管理 比如修改和创建订单是一个事务里面的
        //分布式锁实现 JVM内置锁无法满足多个进程线程安全问题的需要
        //使用Redisson创建分布式锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        try {
            //失败 不等待
            boolean lockResult = lock.tryLock(10L, TimeUnit.SECONDS);
            if(!lockResult) {
                //加锁失败 直接返回错误
                return Result.fail("不可重复购入多次优惠券！");
            }
            Long count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
            if(count > 0) {
                //已经买过，不可再买 一人一单
                return Result.fail("不可重复购入多次优惠券！");
            }
            //事务是通过代理对象实现的此处要使用增强的代理对象
            IVoucherOrderService orderService = (IVoucherOrderService)AopContext.currentProxy();
            orderId = orderService.createOrder(voucherId);
            if(orderId == null) {
                return Result.fail("库存不足！");
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            lock.unlock();
        }
        return Result.ok(orderId);
    }

    @Transactional
    public Long createOrder(Long voucherId) {
        //直接使用sql进行更新
        boolean result = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if(!result) {
            return null;
        }
        //创建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        //使用IdWork生成订单ID
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return orderId;
    }
}
