package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.Voucher;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Voucher voucher);
}
