package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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

    // redisson分布式锁
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT; // lua脚本
    static {
        // 创建lua脚本对象
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 加载lua脚本
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // 设置返回值类型
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    // 当前类的代理对象
    private IVoucherOrderService proxy;
    // 当前类初始化完毕后执行
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 内部类
    private class VoucherOrderHandler implements Runnable {
        private String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 1. 获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"), // 组名  消费者名
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), // 读取消息的数量  阻塞时间
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()) // 队列名称  读取最近一次未消费的消息
                    );
                    // 2. 判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 2.1 如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 3. 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);// 将Map转成指定对象
                    // 4 如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    // 5. ACK确认 XACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    // 处理未正常ACK的情况
                    handlePendingList();
                }
            }
        }

        // 下单
        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            // 1. 获取用户id
            Long userId = voucherOrder.getUserId();

            // 2. 创建锁对象（这里也可以不用上锁，为了兜底） 一人一单
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            // 3. 获取锁
            boolean isLock = lock.tryLock(); // 无参表示默认不等待 -1，锁的超时时间为 30s
            // 4. 判断是否获取锁成功
            if (!isLock) {
                // 获取锁失败
                log.error("不允许重复下单");
                return;
            }
            try {
                proxy.createVoucherOrder(voucherOrder, userId);
            } finally {
                // 释放锁
                lock.unlock();
            }
        }

        // 处理pending-list中未ACK确认的异常消息
        private void handlePendingList() {
            while (true) {
                try {
                    // 1. 根据指定id从pending-list中获取已消费但未确认的消息,从pending-list中的第一个消息开始
                    // XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"), // 组名  消费者名
                            StreamReadOptions.empty().count(1), // 读取消息的数量
                            StreamOffset.create(queueName, ReadOffset.from("0")) // 队列名称  读取pending-list中的第一个消息
                    );
                    // 2. 判断异常消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 2.1 如果获取失败，说明pending-list没有异常消息，结束循环
                        break;
                    }
                    // 3. 解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);// 将Map转成指定对象
                    // 4 如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    // 5. ACK确认 XACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());

                } catch (Exception e) {
                    log.error("处理pending-list异常", e);
                    // 为防止循环过于频繁，进行短时休眠
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

    }




    // 使用lua脚本进行判断
    /**
     * 秒杀优惠劵下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // TODO 获取代理对象 （放在lua脚本后面会出现空指针异常）
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 1. 执行lua脚本（用于判断用户是否具有购买资格，发送消息到redis的stream消息队列）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        // 2. 判断结果是0
        int r = result.intValue();
        if (r != 0) {
            // 2.1 不为0，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3. 返回订单id
        return Result.ok(orderId);
    }


    /**
     * 创建订单，加锁解决同一个用户对同一优惠劵多次下单的问题
     * @param voucherOrder
     * @param userId
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder, Long userId) {
        // 1. 查询订单
        int count = this.query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 2. 判断是否存在
        if (count > 0) {
            log.error("用户已经购买过一次！");
            return;
        }
        // 3. 扣减库存（通过乐观锁来解决高并发下的秒杀卷超卖问题）
        boolean success = seckillVoucherService.update().
                setSql("stock = stock -1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock", 0).  // stock > 0
                        update();
        if (!success) {
            log.error("库存不足！");
            return;
        }

        // 4. 将订单保存到数据库
        this.save(voucherOrder);
    }





// ***********************************************************************************************************
    // 未使用lua脚本
  /*  @Override
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
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 使用redisson分布式锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock(); // 无参表示默认不等待 -1，锁的超时时间为 30s
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
    }*/

    /**
     * 创建订单，加锁解决同一个用户对同一优惠劵多次下单的问题
     * @param voucherId 优惠劵id
     * @return 优惠劵订单id
     */
    //@Transactional
    //public Result createVoucherOrder(Long voucherId, Long userId) {
    //    // 5.1 查询订单
    //    int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //    // 5.2 判断是否存在
    //    if (count > 0) {
    //        return Result.fail("用户已经购买过一次！");
    //    }
    //    // 6. 扣减库存（通过乐观锁来解决高并发下的秒杀卷超卖问题）
    //    boolean success = seckillVoucherService.update().
    //            setSql("stock = stock -1").
    //            eq("voucher_id", voucherId).
    //            gt("stock", 0).  // stock > 0
    //                    update();
    //    if (!success) {
    //        return Result.fail("库存不足！");
    //    }
    //    // 7. 创建订单
    //    VoucherOrder voucherOrder = new VoucherOrder();
    //    // 7.1 订单id
    //    long orderId = redisIdWorker.nextId("order");
    //    voucherOrder.setId(orderId);
    //    // 7.2 用户id
    //    voucherOrder.setUserId(userId);
    //    // 7.3 代金券id
    //    voucherOrder.setVoucherId(voucherId);
    //    // 7.4 保存到数据库
    //    this.save(voucherOrder);
    //    // 8. 返回订单id
    //    return Result.ok(orderId);
    //}
// ***********************************************************************************************************

}
