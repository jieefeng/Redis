package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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
    private VoucherMapper voucherMapper;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 优惠卷秒杀
     *
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //1.判断秒杀是否开始
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();

        //2.判断秒杀是否开始
        if(beginTime.isAfter(now)){
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }

        //3.判断秒杀是否结束
        if(endTime.isBefore(now)){
            //已经结束
            return Result.fail("秒杀已经结束");
        }

        //4.判断库存是否充足
        if (seckillVoucher.getStock() <= 0){
            return Result.fail("库存不足");
        }

        //5.扣减库存
        seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id",voucherId).update();

        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单id
        Long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        //6.2用户id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //6.3代金卷
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //7.返回订单id
        return Result.ok(voucherId);

    }
}



























