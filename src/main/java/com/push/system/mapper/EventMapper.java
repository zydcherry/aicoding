package com.push.system.mapper;

import com.push.system.entity.Event;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 事件Mapper
 */
@Mapper
public interface EventMapper {

    /**
     * 插入事件
     */
    @Insert("INSERT INTO event (event_id, event_type, event_data, biz_id, source_system, status, subscriber_count, created_at) " +
            "VALUES (#{eventId}, #{eventType}, #{eventData}, #{bizId}, #{sourceSystem}, #{status}, #{subscriberCount}, #{createdAt})")
    int insert(Event event);

    /**
     * 根据事件ID查询
     */
    @Select("SELECT event_id, event_type, event_data, biz_id, source_system, status, subscriber_count, created_at " +
            "FROM event WHERE event_id = #{eventId}")
    Event selectByEventId(String eventId);

    /**
     * 查询最近的事件（分页）
     */
    @Select("SELECT event_id, event_type, event_data, biz_id, source_system, status, subscriber_count, created_at " +
            "FROM event ORDER BY created_at DESC LIMIT #{limit}")
    List<Event> selectRecent(int limit);

    /**
     * 根据事件类型查询
     */
    @Select("SELECT event_id, event_type, event_data, biz_id, source_system, status, subscriber_count, created_at " +
            "FROM event WHERE event_type = #{eventType} ORDER BY created_at DESC LIMIT #{limit}")
    List<Event> selectByEventType(@Param("eventType") String eventType, @Param("limit") int limit);

    /**
     * 更新事件状态
     */
    @Update("UPDATE event SET status = #{status} WHERE event_id = #{eventId}")
    int updateStatus(@Param("eventId") String eventId, @Param("status") Integer status);
}
