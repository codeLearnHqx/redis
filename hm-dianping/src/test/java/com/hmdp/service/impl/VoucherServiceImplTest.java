package com.hmdp.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUnit;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;


@SpringBootTest
class VoucherServiceImplTest {
    @Resource
    private IVoucherService voucherService;

    /**
     * 新增秒杀卷
     */
    @Test
    void addSeckillVoucher() {
        Voucher voucher = new Voucher();
        voucher.setId(10L);
        voucher.setShopId(1L);
        voucher.setTitle("100元代金券");
        voucher.setSubTitle("周一至周日均可使用");
        voucher.setRules("全国通用\\n无需预约\\n可无限叠加\\不兑现、不找零\\n仅堂食");
        voucher.setPayValue(8000L);
        voucher.setActualValue(10000L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.now());
        voucher.setEndTime(LocalDateTime.now().atOffset(ZoneOffset.ofHours(10)).toLocalDateTime());

        voucherService.addSeckillVoucher(voucher);

    }

    public static void main(String[] args) {
        DateTime offset = DateTime.now().offset(DateField.HOUR, 10);
        System.out.println(offset);
    }
}