package com.hmdp.service.impl;

import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.SeckillVoucher;
import com.hmdp.domain.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.redis.RedisIdWorker;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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

    @Autowired
    private SeckillVoucherServiceImpl seckillVoucherService;

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
        //直接使用sql进行更新
        boolean result = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if(!result) {
            return Result.fail("库存不足！");
        }
        //创建订单对象
        VoucherOrder voucherOrder = new VoucherOrder();
        //使用IdWork生成订单ID
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
