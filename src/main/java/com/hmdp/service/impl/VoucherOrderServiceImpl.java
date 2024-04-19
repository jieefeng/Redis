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
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;



    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    public Result seckillVoucher(Long voucherId) {

        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否是0
        int r = result.intValue();
        if(r != 0){
            //2.1 不为0没有购买资格
            return Result.fail(r == 1?"库存不足":"不能重复下单");
        }
        //2.2 为0 有购买资格，把下单信息保存到阻塞队列
        Long orderID = redisIdWorker.nextId("order:");
        // TODO 保存到阻塞队列




        //3.返回订单id
        return Result.ok(0);
    }








    /**
     * 优惠卷秒杀
     *
     * @param voucherId
     * @return
     */
//    public Result seckillVoucher(Long voucherId) {
//
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        //1.判断秒杀是否开始
//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//
//        //2.判断秒杀是否开始
//        if(beginTime.isAfter(now)){
//            //尚未开始
//            return Result.fail("秒杀尚未开始");
//        }
//
//        //3.判断秒杀是否结束
//        if(endTime.isBefore(now)){
//            //已经结束
//            return Result.fail("秒杀已经结束");
//        }
//
//        //4.判断库存是否充足
//        Integer stock = seckillVoucher.getStock();
//        if (stock <= 0){
//            return Result.fail("库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
////        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        boolean success = lock.tryLock();
//        if(!success){
//            //获取锁失败 返回错误信息或者重试
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        //4.一人一单
        //4.1获取用户id
        Long userId = UserHolder.getUser().getId();
        //4.2查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //4.3判断是否存在
        if(count > 0){
            return Result.fail("用户已经购买过了");
        }

        //5.扣减库存
        //5.1判断是否已经被修改过
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId).gt("stock", 0) //where id = ? and stock > 0
                .update();

        if(!success){
            //扣减失败
            return Result.fail("库存不足");
        }
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单id
        Long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        //6.2用户id
        voucherOrder.setUserId(userId);
        //6.3代金卷
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //7.返回订单id
        return Result.ok(voucherId);
    }
}



























