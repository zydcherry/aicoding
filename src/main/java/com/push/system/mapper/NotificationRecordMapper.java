package com.push.system.mapper;

import com.push.system.entity.NotificationRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知记录Mapper
 */
@Mapper
public interface NotificationRecordMapper {

    /**
     * 插入通知记录
     */
    @Insert("INSERT INTO notification_record (notification_id, event_id, subscription_id, third_party_code, " +
            "push_mode, callback_url, request_data, response_data, status, retry_count, error_message, " +
            "cost_time, created_at, updated_at, pushed_at, next_retry_at) " +
            "VALUES (#{notificationId}, #{eventId}, #{subscriptionId}, #{thirdPartyCode}, #{pushMode}, " +
            "#{callbackUrl}, #{requestData}, #{responseData}, #{status}, #{retryCount}, #{errorMessage}, " +
            "#{costTime}, #{createdAt}, #{updatedAt}, #{pushedAt}, #{nextRetryAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NotificationRecord record);

    /**
     * 根据通知ID查询
     */
    @Select("SELECT * FROM notification_record WHERE notification_id = #{notificationId}")
    NotificationRecord selectByNotificationId(String notificationId);

    /**
     * 更新推送状态
     */
    @Update("UPDATE notification_record SET status = #{status}, response_data = #{responseData}, " +
            "cost_time = #{costTime}, error_message = #{errorMessage}, pushed_at = #{pushedAt}, " +
            "updated_at = #{updatedAt} WHERE notification_id = #{notificationId}")
    int updateStatus(NotificationRecord record);

    /**
     * 更新重试信息
     */
    @Update("UPDATE notification_record SET retry_count = #{retryCount}, next_retry_at = #{nextRetryAt}, " +
            "updated_at = #{updatedAt} WHERE notification_id = #{notificationId}")
    int updateRetryInfo(@Param("notificationId") String notificationId,
                        @Param("retryCount") Integer retryCount,
                        @Param("nextRetryAt") LocalDateTime nextRetryAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 根据事件ID查询所有通知记录
     */
    @Select("SELECT * FROM notification_record WHERE event_id = #{eventId} ORDER BY created_at")
    List<NotificationRecord> selectByEventId(String eventId);

    /**
     * 查询失败的通知记录（用于重试）
     */
    @Select("SELECT * FROM notification_record WHERE status = 3 AND retry_count < 3 " +
            "AND next_retry_at <= #{now} LIMIT #{limit}")
    List<NotificationRecord> selectFailedRecordsForRetry(@Param("now") LocalDateTime now,
                                                         @Param("limit") int limit);

    /**
     * 删除历史记录（数据清理）
     */
    @Delete("DELETE FROM notification_record WHERE status IN (2, 3) AND created_at < #{beforeDate}")
    int deleteOldRecords(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 统计推送成功率
     */
    @Select("SELECT " +
            "COUNT(*) as total, " +
            "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as success, " +
            "SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) as failed " +
            "FROM notification_record " +
            "WHERE created_at >= #{startTime} AND created_at < #{endTime}")
    @MapKey("total")
    java.util.Map<String, Object> selectStatistics(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime);
}
