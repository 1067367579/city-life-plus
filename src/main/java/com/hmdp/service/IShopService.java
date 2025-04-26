package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.domain.dto.Result;
import com.hmdp.domain.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
    Result queryShopById(Long id);
    Result updateShop(Shop shop);
}
