package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠劵下单
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 通过线程安全的方式去创建订单
     * @param voucherOrder
     * @param userId
     */
    void createVoucherOrder(VoucherOrder voucherOrder, Long userId);
}
