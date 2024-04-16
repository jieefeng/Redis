package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {


    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    /**
     * 实际过期
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    /**
     * 逻辑过期
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));

        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public  <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){
        //1. 从redis中查询商城缓存
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //3.存在 直接返回
            return JSONUtil.toBean(shopJson,type);
        }

        //4.判断命中的是否是空值
        if(shopJson != null){
            return null;
        }

        //5.不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //6.不存在，返回错误
        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        //7.存在，写入redis
        this.set(key,r,time,timeUnit);

        //7.返回
        return r;
    }



    public  <R,ID> R queryWithLogicalExpire(String keyPrefix,String keyLockPrefix ,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit timeUnit){
        //1. 从redis中查询商城缓存
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否命中
        if(StrUtil.isBlank(shopJson)){
            //3.未命中 返回空
            return null;
        }

        //3.命中缓存 判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //4.未过期直接返回
            return r;
        }

        //5.尝试获取锁
        String keyLock = LOCK_SHOP_KEY + id;
        boolean success = tryLock(keyLock);
        if(!success){
            //6.获取锁失败 直接返回旧的商品信息
            return r;
        }
        //7成功 开启独立线程 实现缓存重建
        CACHE_REBUILD_EXECUTOR.submit(() ->{
            //重建缓存
            try {
                //先查数据库
                R r1 = dbFallback.apply(id);
                RedisData redisData1 = new RedisData();
                redisData1.setData(r1);
                redisData1.setExpireTime(LocalDateTime.now().plusSeconds(time));

                //再写入缓存
//                stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData1));
                this.setWithLogicalExpire(key,redisData1,time,timeUnit);
                return r1;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unlock(keyLock);
            }
        });
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    public <R,ID> void saveShopService(ID id,Long expireSeconds,Function<ID,R> dbFallback) throws InterruptedException {
        //1.查询店铺信息
        R r = dbFallback.apply(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(r);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
}














