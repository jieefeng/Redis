package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import com.hmdp.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.awt.image.RasterFormatException;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX = "lock:";

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+ "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryLock(Long timeoutSec) {

        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(success)){
            return false;
        } else {
            return true;
        }
    }

    public void unLock() {
        //获取线程标识
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //判断是否是自己的锁
        if(!value.equals(ID_PREFIX + Thread.currentThread().getId())){
            return;
        }
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}


































