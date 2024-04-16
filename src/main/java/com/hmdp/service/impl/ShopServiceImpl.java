package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_SHOP_PREFIX = "lock:shop";

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Resource
    private CacheClient cacheClient;

    public Result queryById(Long id) {

        // 缓存穿透
        // Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id,Shop.class, this::getById,20L,TimeUnit.SECONDS);
        //返回
        return Result.ok(shop);
    }

    private Shop queryWithLogicalExpire(Long id){
        //1. 从redis中查询商城缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.判断是否命中
        if(StrUtil.isBlank(shopJson)){
            //3.未命中 返回空
            return null;
        }

        //3.命中缓存 判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //4.未过期直接返回
            return shop;
        }

        //5.尝试获取锁
        boolean success = tryLock(LOCK_SHOP_KEY + id);
        if(!success){
            //6.获取锁失败 直接返回旧的商品信息
            return shop;
        }
        //7成功 开启独立线程 实现缓存重建
        CACHE_REBUILD_EXECUTOR.submit(() ->{
           //重建缓存
            try {
                this.saveShopService(id,20L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unlock(LOCK_SHOP_KEY + id);
            }
        });

        return shop;
    }


    private Shop queryWithMutex(Long id)  {
        //1. 从redis中查询商城缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在 直接返回
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }

        //4.判断命中的是否是空值
        if(shopJson != null){
            return null;
        }

        // 5.实现缓存重建
        // 5.1 获取互斥
        String lockKey = null;
        Shop shop = null;
        try {
            lockKey = LOCK_SHOP_PREFIX + ":" + id;
            boolean isLock = tryLock(lockKey);
            // 5.2 判断是否获取锁成功
            if(!isLock){
                // 5.3 失败则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
//            5.4 获取锁成功后双重检查 如果redis缓存已经有了 返回缓存的数据
            String shopJson1 = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
            if(shopJson1 != null){
                //5.5 返回缓存的数据,并且释放锁
                unlock(lockKey);
                return JSONUtil.toBean(shopJson1,Shop.class);
            }

            // 5.6 缓存不存在，根据i询数据库
            shop = getById(id);

            //模拟重建的延时
            Thread.sleep(200);

            //6.不存在，返回错误
            if(shop == null){
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            // 7.存在，写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 8.释放互斥锁
            unlock(lockKey);
        }
        //9.返回
        return shop;
    }


//    private Shop queryWithPassThrough(Long id){
//        //1. 从redis中查询商城缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        //2.判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3.存在 直接返回
//            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
//            return shop;
//        }
//        //4.判断命中的是否是空值
//        if(shopJson != null){
//            return null;
//        }
//
//        //5.不存在，根据id查询数据库
//        Shop shop = getById(id);
//
//        //6.不存在，返回错误
//        if(shop == null){
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//
//        //7.存在，写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        //7.返回
//        return shop;
//    }


    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }

        //1.更新数据库
        updateById(shop);

        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public void saveShopService(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }


}










