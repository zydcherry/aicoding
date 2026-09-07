package com.push.system.mapper;

import com.push.system.entity.Subscription;
import com.push.system.mybatis.JsonListTypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订阅Mapper
 */
@Mapper
public interface SubscriptionMapper {

    /**
     * 插入订阅
     */
    @Insert("INSERT INTO subscription (subscriber_name, callback_url, event_types, secret_key, status, description, filter_condition, created_at, updated_at) " +
            "VALUES (#{subscriberName}, #{callbackUrl}, #{eventTypes, typeHandler=com.push.system.mybatis.JsonListTypeHandler}, #{secretKey}, #{status}, #{description}, #{filterCondition}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Subscription subscription);

    /**
     * 根据ID查询订阅
     */
    @Select("SELECT id, subscriber_name, callback_url, event_types, secret_key, status, description, filter_condition, created_at, updated_at " +
            "FROM subscription WHERE id = #{id}")
    @Results({
        @Result(property = "eventTypes", column = "event_types", typeHandler = JsonListTypeHandler.class)
    })
    Subscription selectById(Long id);

    /**
     * 查询所有订阅
     */
    @Select("SELECT id, subscriber_name, callback_url, event_types, secret_key, status, description, filter_condition, created_at, updated_at " +
            "FROM subscription ORDER BY id")
    @Results({
        @Result(property = "eventTypes", column = "event_types", typeHandler = JsonListTypeHandler.class)
    })
    List<Subscription> selectAll();

    /**
     * 查询启用状态的订阅
     */
    @Select("SELECT id, subscriber_name, callback_url, event_types, secret_key, status, description, filter_condition, created_at, updated_at " +
            "FROM subscription WHERE status = 1 ORDER BY id")
    @Results({
        @Result(property = "eventTypes", column = "event_types", typeHandler = JsonListTypeHandler.class)
    })
    List<Subscription> selectByStatusEnabled();

    /**
     * 更新订阅
     */
    @Update("UPDATE subscription SET subscriber_name = #{subscriberName}, callback_url = #{callbackUrl}, " +
            "event_types = #{eventTypes, typeHandler=com.push.system.mybatis.JsonListTypeHandler}, secret_key = #{secretKey}, status = #{status}, " +
            "description = #{description}, filter_condition = #{filterCondition}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Subscription subscription);

    /**
     * 删除订阅
     */
    @Delete("DELETE FROM subscription WHERE id = #{id}")
    int deleteById(Long id);

    /**
     * 根据事件类型查询订阅（使用JSON_CONTAINS，需要MySQL 5.7+）
     */
    @Select("SELECT id, subscriber_name, callback_url, event_types, secret_key, status, description, filter_condition, created_at, updated_at " +
            "FROM subscription WHERE status = 1 AND JSON_CONTAINS(event_types, JSON_QUOTE(#{eventType}))")
    @Results({
        @Result(property = "eventTypes", column = "event_types", typeHandler = JsonListTypeHandler.class)
    })
    List<Subscription> selectByEventType(String eventType);
}
