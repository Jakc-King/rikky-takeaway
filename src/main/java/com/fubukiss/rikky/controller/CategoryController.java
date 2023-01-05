package com.fubukiss.rikky.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fubukiss.rikky.common.R;
import com.fubukiss.rikky.entity.Category;
import com.fubukiss.rikky.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>Project: rikky-takeaway - CategoryController 分类管理的Controller类
 * <p>Powered by Riverify On 01-02-2023 21:48:43
 *
 * @author Riverify
 * @version 1.0
 * @since JDK8
 */
@Slf4j
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;


    /**
     * <h2>新增菜品分类<h2/>
     *
     * @param category 菜品分类实体类，@RequestBody注解用于接收前端传递的json数据
     * @return 返回R对象，R对象为自定义的返回对象，用于统一返回数据格式
     */
    @PostMapping
    public R<String> save(@RequestBody Category category) {
        log.info("新增菜品分类，category={}", category);
        categoryService.save(category); // 调用Service层的save方法，将category保存到数据库

        return R.success("新增成功");
    }


    /**
     * <h2>显示菜品分类列表<h2/>
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page, int pageSize) {
        // 构造分页构造器
        Page<Category> pageInfo = new Page<>();
        // 构造条件构造器 -- 由于要根据Category的sort字段进行排序，所以需要使用LambdaQueryWrapper
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        // 添加排序条件 使用sort字段进行排序，asc为升序，desc为降序
        queryWrapper.orderByAsc(Category::getSort); // Category::getSort 为Lambda表达式，等价于Category::getSort()，即调用Category的getSort()方法
        // 调用Service层的page方法，将分页构造器和条件构造器传入，返回分页数据
        categoryService.page(pageInfo, queryWrapper); // 调用Service层的page方法，将分页构造器和条件构造器传入，返回分页数据，得到的数据会自动填充到pageInfo中

        return R.success(pageInfo); // 返回分页数据
    }

}
