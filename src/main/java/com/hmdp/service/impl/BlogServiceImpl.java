package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private BlogServiceImpl blogService;

    @Resource
    private UserServiceImpl userService;

    /**
     * 查询博客
     * @param id
     * @return
     */
    public Result queryBolgById(Long id) {
        //1.从博客表里面查询博客
        Blog blog = blogService.getById(id);
        if(blog == null){
            return Result.fail("博客不存在");
        }
        //2.把用户表的用户字段填充到bolg
        queryBlogById(blog);
        //3.返回
        return Result.ok(blog);
    }

    private void queryBlogById(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
