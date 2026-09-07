package com.push.system.mapper;

import com.push.system.model.ThirdPartyConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 第三方配置Mapper
 */
@Mapper
public interface ThirdPartyConfigMapper {

    /**
     * 根据code查询配置
     */
    @Select("SELECT id, code, name, api_url, method, connect_timeout, read_timeout, " +
            "header_template, body_template, success_condition, result_path, error_msg_path, " +
            "auth_type, auth_params, max_retry, retry_strategy, status, created_at, updated_at " +
            "FROM third_party_config WHERE code = #{code} AND status = 1")
    ThirdPartyConfig selectByCode(String code);

    /**
     * 查询所有启用的配置
     */
    @Select("SELECT id, code, name, api_url, method, connect_timeout, read_timeout, " +
            "header_template, body_template, success_condition, result_path, error_msg_path, " +
            "auth_type, auth_params, max_retry, retry_strategy, status, created_at, updated_at " +
            "FROM third_party_config WHERE status = 1 ORDER BY id")
    List<ThirdPartyConfig> selectAllEnabled();

    /**
     * 插入配置
     */
    @Insert("INSERT INTO third_party_config (code, name, api_url, method, connect_timeout, read_timeout, " +
            "header_template, body_template, success_condition, result_path, error_msg_path, " +
            "auth_type, auth_params, max_retry, retry_strategy, status, created_at, updated_at) " +
            "VALUES (#{code}, #{name}, #{apiUrl}, #{method}, #{connectTimeout}, #{readTimeout}, " +
            "#{headerTemplate}, #{bodyTemplate}, #{successCondition}, #{resultPath}, #{errorMsgPath}, " +
            "#{authType}, #{authParams}, #{maxRetry}, #{retryStrategy}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ThirdPartyConfig config);

    /**
     * 更新配置
     */
    @Update("UPDATE third_party_config SET name = #{name}, api_url = #{apiUrl}, method = #{method}, " +
            "connect_timeout = #{connectTimeout}, read_timeout = #{readTimeout}, header_template = #{headerTemplate}, " +
            "body_template = #{bodyTemplate}, success_condition = #{successCondition}, result_path = #{resultPath}, " +
            "error_msg_path = #{errorMsgPath}, auth_type = #{authType}, auth_params = #{authParams}, " +
            "max_retry = #{maxRetry}, retry_strategy = #{retryStrategy}, status = #{status}, updated_at = #{updatedAt} " +
            "WHERE id = #{id}")
    int update(ThirdPartyConfig config);

    /**
     * 删除配置
     */
    @Delete("DELETE FROM third_party_config WHERE id = #{id}")
    int deleteById(Long id);
}
