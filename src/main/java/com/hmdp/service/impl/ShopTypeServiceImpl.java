package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private  StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ShopTypeMapper shopTypeMapper;

    public Result querylist() {
        //1. 从redis中查询商城缓存
        List<String> shopType = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);

        //2.判断是否存在
        if (shopType != null && !shopType.isEmpty()) {
            List<ShopType> shopTypeList = new ArrayList<>();
            //3.存在 直接返回
            for (String s : shopType) {
                ShopType type = JSONUtil.toBean(s, ShopType.class);
                shopTypeList.add(type);
            }
            return Result.ok(shopTypeList);
        }

        //4.不存在，根据id查询数据库
        List<ShopType> shopTypeList = shopTypeMapper.selectList(null);

        //5.不存在，返回错误
        if(shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail("不存在店铺类别 ");
        }

        //6.存在，写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(shop));
        for (ShopType s : shopTypeList) {
            stringRedisTemplate.opsForList().leftPush(CACHE_SHOPTYPE_KEY,JSONUtil.toJsonStr(s));
        }
        //7.设置过期时间
        stringRedisTemplate.expire(CACHE_SHOPTYPE_KEY,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //8.返回
        return Result.ok(shopTypeList);
    }
}
