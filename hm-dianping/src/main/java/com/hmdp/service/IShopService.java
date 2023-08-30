package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {


    /**
     * 根据id查询商户信息
     * @param id 商户id
     * @return 统一包装结果
     */
    Result queryById(Long id);


    /**
     * 更新商铺信息
     * @param shop 商铺信息
     * @return 统一的包装结果
     */
    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
