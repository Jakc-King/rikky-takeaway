package com.fubukiss.rikky.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fubukiss.rikky.dto.DishDto;
import com.fubukiss.rikky.entity.Dish;
import com.fubukiss.rikky.entity.DishFlavor;
import com.fubukiss.rikky.mapper.DishMapper;
import com.fubukiss.rikky.service.DishFlavorService;
import com.fubukiss.rikky.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>Project: rikky-takeaway - DishServiceImpl
 * <p>Powered by river On 2023/01/06 11:14 PM
 *
 * @author Riverify
 * @version 1.0
 * @since JDK8
 */
@Service
@Slf4j
@EnableTransactionManagement
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {
    // ServiceImpl<DishMapper, Dish> 为MyBatis-Plus提供的基础实现类，<DishMapper, Dish> 为泛型，DishMapper为Mapper接口，Dish为实体类


    /**
     * 口味service
     */
    @Autowired
    private DishFlavorService dishFlavorService;

    /**
     * redis缓存
     */
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;


    /**
     * 添加菜品，同时插入口味的数据
     * <p>@Transactional 事务注解，如果添加菜品失败，口味数据也不会添加。需要在启动类上添加@EnableTransactionManagement注解。
     *
     * @param dishDto 菜品数据
     */
    @Transactional
    @Override
    public void saveWithFlavors(DishDto dishDto) {
        // 保存基本信息到菜品表
        this.save(dishDto);
        // 获取菜品id
        Long id = dishDto.getId();  // 此getId是dishDto继承自Dish的方法，返回的是菜品id,需要将dish的id与dishFlavor的dishId关联起来

        // 保存口味信息到口味表(不包括与之[dishFlavor的dishId]匹配的dish的 id)，所以需要在此之前对dishFlavor的dishId进行赋值
//        dishFlavorService.saveBatch(dishDto.getFlavors());  // saveBatch() 为MyBatis-Plus提供的批量插入方法

        // 获取菜品口味
        List<DishFlavor> flavors = dishDto.getFlavors();

        // 使用stream流对口味进行遍历，使用peek()方法对每个口味进行操作，操作内容为将口味的dishId赋值为菜品id
        flavors = flavors.stream().peek(item -> {
            // 对口味的dishId进行赋值
            item.setDishId(id);
        }).collect(Collectors.toList());    // 此时，flavors中的每个dishFlavor的dishId都已经被赋值了

        // 保存口味信息到口味表(包括与之[dishFlavor的dishId]匹配的dish的id)
        dishFlavorService.saveBatch(flavors);  // saveBatch() 为MyBatis-Plus提供的批量插入方法


        // 清理redis缓存
        Set<Object> keys = redisTemplate.keys("dish_*");    // 获取所有以dish_开头的key
        assert keys != null;
        redisTemplate.delete(keys);

    }


    /**
     * 修改菜品，同时包括口味的数据
     * <p>@Transactional 事务注解，如果添加菜品失败，口味数据也不会添加。需要在启动类上添加@EnableTransactionManagement注解。
     *
     * @param dishDto 菜品数据
     */
    @Transactional
    @Override
    public void updateWithFlavors(DishDto dishDto) {

        // 修改基本信息到菜品表
        this.updateById(dishDto);

        // 获取菜品id
        Long id = dishDto.getId();

        // 修改口味信息到口味表
        // 1.删除原有口味
        dishFlavorService.remove(new LambdaQueryWrapper<DishFlavor>().eq(DishFlavor::getDishId, id));
        // 2.添加新的口味
        // 获取菜品口味
        List<DishFlavor> flavors = dishDto.getFlavors();    // disDto中的flavors不包含dishId，dishId是，所以需要在此之前对dishFlavor的dishId进行赋值
        // 使用stream流对口味进行遍历，使用peek()方法对每个口味进行操作，操作内容为将口味的dishId赋值为菜品id
        flavors = flavors.stream().peek(item -> {
            // 对口味的dishId进行赋值
            item.setDishId(id);
        }).collect(Collectors.toList());    // 此时，flavors中的每个dishFlavor的dishId都已经被赋值了

        // 保存口味信息到口味表(包括与之[dishFlavor的dishId]匹配的dish的id)
        dishFlavorService.saveBatch(flavors);  // saveBatch() 为MyBatis-Plus提供的批量插入方法

        // 清理redis缓存
        Set<Object> keys = redisTemplate.keys("dish_*");    // 获取所有以dish_开头的key
        assert keys != null;
        redisTemplate.delete(keys);

    }


    /**
     * 根据id获得菜品和口味的数据
     *
     * @param id 菜品id
     * @return 菜品数据（包含口味数据）
     */
    @Override
    public DishDto getByIdWithFlavors(Long id) {

        // 获取菜品的基本信息，从dish表中获取
        Dish dish = this.getById(id);

        // 新建一个DishDto对象，将菜品基本信息赋值给DishDto
        DishDto dishDto = new DishDto();
        BeanUtils.copyProperties(dish, dishDto);

        // 查询当前菜品的口味信息，从dish_flavor表中获取
        LambdaQueryWrapper<DishFlavor> queryWrapper = new LambdaQueryWrapper<>();       // 条件构造器
        queryWrapper.eq(DishFlavor::getDishId, id);                         // 查询条件，查询dishId为id的口味
        List<DishFlavor> flavors = dishFlavorService.list(queryWrapper);    // 查询的口味结果

        // 将口味结果赋值给DishDto
        dishDto.setFlavors(flavors);

        return dishDto;
    }


    /**
     * 获得所有菜品和口味的数据
     *
     * @return 菜品数据List（包含口味数据）
     */
    @Override
    public List<DishDto> listWithFlavors(Dish dish) {
        // 新建一个DishDto的List
        List<DishDto> dtoList = null;

        // 动态构造key
        String key = "dish_" + dish.getCategoryId() + "_" + dish.getStatus();   // 生成redis的key

        // 先从redis中获取数据
        dtoList = (List<DishDto>) redisTemplate.opsForValue().get(key);

        // 如果存在，则直接返回
        if (dtoList != null) {
            return dtoList;
        }

        // 如果不存在，则从数据库中获取数据
        // 构造查询条件
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();

        // where status = 1 and category_id = ?  由于Dish的isDeleted字段使用了@TableLogic注解，所以这里不需要设置is_deleted = 0，MP会自动将is_deleted = 0的条件加入到查询条件中
        queryWrapper.eq(Dish::getStatus, 1);
        queryWrapper.eq(dish.getCategoryId() != null, Dish::getCategoryId, dish.getCategoryId()); // dish.getCategoryId() != null 为true则执行后面的语句

        // 添加排序条件 where category_id = ? order by sort asc , update_time desc
        queryWrapper.orderByAsc(Dish::getSort).orderByDesc(Dish::getUpdateTime);

        // 查询到的菜品列表，不包含菜品口味信息
        List<Dish> list = this.list(queryWrapper);

        // 将查询到的菜品列表转换为菜品DTO列表，包含菜品口味信息
        dtoList = list.stream().map((item) -> {

            // 1.new DishDto()是为了将dish对象转换为dishDto对象，因为dishDto对象中包含了dish对象中的所有属性，还包含了菜品口味信息
            DishDto dishDto = new DishDto();

            // 2.先进行dishDto的普通字段拷贝
            BeanUtils.copyProperties(item, dishDto);

            // 3.再进行dishDto的菜品口味字段拷贝
            Long DishId = item.getId();                                 // 获取每一个dish对象(item)的id
            LambdaQueryWrapper<DishFlavor> dishFlavorQueryWrapper = new LambdaQueryWrapper<>();    // select * from dish_flavor
            dishFlavorQueryWrapper.eq(DishFlavor::getDishId, DishId);      // where dish_id = ?

            // 4.查询到的菜品口味列表
            List<DishFlavor> dishFlavorList = dishFlavorService.list(dishFlavorQueryWrapper);

            // 5.将查询到的菜品口味列表转换为菜品口味DTO列表
            dishDto.setFlavors(dishFlavorList);

            return dishDto;

        }).collect(Collectors.toList());  // collect方法将stream转换为List，因为dishDtoRecordsList是一个List对象，所以需要将stream转换为List

        // 将查询到的数据存入redis中
        redisTemplate.opsForValue().set(key, dtoList, 1, TimeUnit.DAYS);    // 1天后过期

        return dtoList;
    }

    /**
     * 修改菜品状态，如果是在售状态则修改为下架，如果是下架状态则修改为在售
     *
     * @param ids    菜品id
     * @param status 需要修改成的状态
     */
    @Override
    public void updateDishStatus(String ids, Integer status) {

        // 将ids以逗号分隔
        String[] idArray = ids.split(",");

        // 遍历idArray，将每一个id的菜品状态修改为status
        for (String id : idArray) {
            // 条件构造器
            LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<>();
            // 设置条件 (where id = id)
            wrapper.eq(Dish::getId, id);
            // 设置要修改的字段 (set status = status)
            wrapper.set(Dish::getStatus, status);
            // 执行修改
            this.update(wrapper);
        }

        // 清理redis缓存
        Set<Object> keys = redisTemplate.keys("dish_*");    // 获取所有以dish_开头的key
        assert keys != null;
        redisTemplate.delete(keys);
    }


    /**
     * 删除菜品（逻辑删除)
     *
     * @param ids 前端传入的菜品id，可能是一个，也可能是多个，多个数据是以逗号分隔的
     */
    @Override
    public void deleteByIds(String ids) {
        // 将ids以逗号分隔
        String[] idArray = ids.split(",");
        // 遍历idArray，将每一个id的菜品状态修改为status
        for (String id : idArray) {
            // 条件构造器
            LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<>();
            // 设置条件 (where id = id)
            wrapper.eq(Dish::getId, id);
            // 修改菜品状态为停售
            wrapper.set(Dish::getStatus, 0);
            // 设置删除字段为1 (set is_deleted = 1)
            wrapper.set(Dish::getIsDeleted, 1);  // 由于Dish实体类的isDeleted使用了@TableLogic进行逻辑删除，这里还可以直接调用dishService的removeById方法，会自动将is_deleted字段设置为1
            // 执行修改
            this.update(wrapper);
        }

        // 清理redis缓存
        Set<Object> keys = redisTemplate.keys("dish_*");    // 获取所有以dish_开头的key
        assert keys != null;
        redisTemplate.delete(keys);
    }

}
