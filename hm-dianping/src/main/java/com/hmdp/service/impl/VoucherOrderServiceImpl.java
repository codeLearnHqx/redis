package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    /**
     * 全局id生成器
     */
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 查询优惠劵（秒杀卷表与优惠劵表共享id）
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始");
        }
        // 3. 判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 已经结束
            return Result.fail("秒杀已经结束");
        }
        // 4. 判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(1200);
        // 判断是否获取锁成功
        if (!isLock) {
            // 获取锁失败，返回错误或者重试
            return Result.fail("不允许重复下单");
        }
        // 5 一人一单（在出现业务执行时间内锁超时释放时，同一个用户在并发请求时还可能会出现一人两单的特殊情况）
        try {
            // 获取当前对象的代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            // 不能直接调用createVoucherOrder方法，因为这样是通过当前类的对象去调用的方法，没有经过spring的代理，事务注解@Transactional就不会生效
            // 所以我们需要通过代理对象去调用这个方法
            return proxy.createVoucherOrder(voucherId, userId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 创建订单，加锁解决同一个用户对同一优惠劵多次下单的问题
     * @param voucherId 优惠劵id
     * @return 优惠劵订单id
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        // 5.1 查询订单
        int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2 判断是否存在
        if (count > 0) {
            return Result.fail("用户已经购买过一次！");
        }
        // 6. 扣减库存（通过乐观锁来解决高并发下的秒杀卷超卖问题）
        boolean success = seckillVoucherService.update().
                setSql("stock = stock -1").
                eq("voucher_id", voucherId).
                gt("stock", 0).  // stock > 0
                        update();
        if (!success) {
            return Result.fail("库存不足！");
        }
        // 7. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 7.2 用户id
        voucherOrder.setUserId(userId);
        // 7.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 7.4 保存到数据库
        this.save(voucherOrder);
        // 8. 返回订单id
        return Result.ok(orderId);
    }
}
