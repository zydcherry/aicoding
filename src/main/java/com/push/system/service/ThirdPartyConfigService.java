package com.push.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.mapper.ThirdPartyConfigMapper;
import com.push.system.model.ThirdPartyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第三方配置服务
 * 负责加载和管理第三方系统配置
 */
@Service
public class ThirdPartyConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ThirdPartyConfigService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ThirdPartyConfigMapper thirdPartyConfigMapper;

    // 本地缓存: code -> config (作为Redis的备用)
    private final Map<String, ThirdPartyConfig> localCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化时加载配置到本地缓存
        loadConfigsToLocalCache();
    }

    /**
     * 获取第三方配置（使用Redis缓存）
     */
    @Cacheable(value = "thirdPartyConfig", key = "#code", unless = "#result == null")
    public ThirdPartyConfig getConfig(String code) {
        // 优先从数据库查询（Redis缓存会自动生效）
        ThirdPartyConfig config = thirdPartyConfigMapper.selectByCode(code);

        if (config == null) {
            // 如果数据库没有，从本地缓存获取（向后兼容）
            config = localCache.get(code);
        }

        return config;
    }

    /**
     * 加载所有配置到本地缓存
     */
    private void loadConfigsToLocalCache() {
        logger.info("开始从数据库加载第三方配置到本地缓存...");

        try {
            List<ThirdPartyConfig> configs = thirdPartyConfigMapper.selectAllEnabled();
            for (ThirdPartyConfig config : configs) {
                localCache.put(config.getCode(), config);
            }
            logger.info("从数据库加载了{}个第三方配置", configs.size());
        } catch (Exception e) {
            logger.error("从数据库加载配置失败", e);
        }
    }

    /**
     * 刷新配置（清除Redis缓存并重新加载）
     */
    @CacheEvict(value = "thirdPartyConfig", allEntries = true)
    public void refreshConfigs() {
        localCache.clear();
        loadConfigsToLocalCache();
        logger.info("配置刷新完成");
    }

    /**
     * 添加或更新配置
     */
    @CacheEvict(value = "thirdPartyConfig", key = "#config.code")
    public void putConfig(ThirdPartyConfig config) {
        if (config.getId() == null) {
            // 新增
            thirdPartyConfigMapper.insert(config);
        } else {
            // 更新
            thirdPartyConfigMapper.update(config);
        }
        localCache.put(config.getCode(), config);
        logger.info("更新配置: code={}", config.getCode());
    }

    /**
     * 删除配置
     */
    @CacheEvict(value = "thirdPartyConfig", key = "#code")
    public void removeConfig(String code) {
        ThirdPartyConfig config = thirdPartyConfigMapper.selectByCode(code);
        if (config != null) {
            thirdPartyConfigMapper.deleteById(config.getId());
        }
        localCache.remove(code);
        logger.info("删除配置: code={}", code);
    }
}
