package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.domain.entity.VoucherOrder;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Update("update tb_seckill_voucher set stock = stock - 1 where voucher_id = #{voucherId}" +
            " and stock > 0")
    int updateStock(Long voucherId);
}
