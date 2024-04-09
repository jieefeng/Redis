package com.hmdp.utils;

import com.hmdp.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.awt.image.RasterFormatException;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX = "lock:";

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean tryLock(Long timeoutSec) {

        long threadId = Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(success)){
            return false;
        } else {
            return true;
        }
    }

    public void unLock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
