package com.push.system.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.config.PushSystemProperties;
import com.push.system.http.UnifiedHttpClient;
import com.push.system.id.IdGenerator;
import com.push.system.model.HttpResult;
import com.push.system.model.PushMessage;
import com.push.system.model.ThirdPartyConfig;
import com.push.system.queue.PriorityPushQueue;
import com.push.system.service.ThirdPartyConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一推送执行层
 * 支持两种模式：
 * 1. 点对点推送（通过第三方配置）
 * 2. 事件驱动推送（通过订阅者回调URL）
 */
@Service
public class UnifiedPusher {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedPusher.class);

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private ThirdPartyConfigService configService;

    @Autowired
    private UnifiedHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushSystemProperties pushSystemProperties;

    private PriorityPushQueue pushQueue;
    private ExecutorService workerPool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        // 从配置中读取参数
        int queueCapacity = pushSystemProperties.getSystem().getQueueCapacity();
        int workerThreadCount = pushSystemProperties.getSystem().getWorkerThreadCount();

        this.pushQueue = new PriorityPushQueue(queueCapacity);
        this.workerPool = new ThreadPoolExecutor(
            workerThreadCount,
            workerThreadCount,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "PushWorker-" + (++counter));
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            int workerThreadCount = pushSystemProperties.getSystem().getWorkerThreadCount();
            logger.info("启动统一推送执行层，工作线程数: {}", workerThreadCount);
            for (int i = 0; i < workerThreadCount; i++) {
                workerPool.execute(new PushWorker());
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            logger.info("正在停止统一推送执行层...");
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("统一推送执行层已停止");
        }
    }

    /**
     * 提交推送消息（方式1：API直接调用）
     */
    public Long submitForDirectPush(PushMessage message) {
        long id = idGenerator.generateId(IdGenerator.AccessType.API);
        message.setId(id);
        message.setAccessType("API_DIRECT");
        enrichMessage(message);

        try {
            pushQueue.put(message);
            logger.info("提交推送消息成功 [API直接], ID: {}, 优先级: {}, 第三方: {}",
                id, message.getPriority(), message.getThirdPartyCode());
            return id;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("提交推送消息失败 [API直接], 线程被中断", e);
            throw new RuntimeException("提交推送消息失败", e);
        }
    }

    /**
     * 提交推送消息（方式2：API发送到Kafka，由消费者调用此方法）
     */
    public Long submitFromKafkaInternal(PushMessage message) {
        if (message.getId() == null) {
            long id = idGenerator.generateId(IdGenerator.AccessType.API);
            message.setId(id);
        }
        message.setAccessType("API_KAFKA");
        enrichMessage(message);

        try {
            pushQueue.put(message);
            logger.info("提交推送消息成功 [API+Kafka], ID: {}, 优先级: {}, 第三方: {}",
                message.getId(), message.getPriority(), message.getThirdPartyCode());
            return message.getId();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("提交推送消息失败 [API+Kafka], 线程被中断", e);
            throw new RuntimeException("提交推送消息失败", e);
        }
    }

    /**
     * 提交推送消息（方式3：直接消费Kafka消息）
     */
    public Long submitFromKafkaDirect(PushMessage message) {
        long id = idGenerator.generateId(IdGenerator.AccessType.KAFKA_DIRECT);
        message.setId(id);
        message.setAccessType("KAFKA_DIRECT");
        enrichMessage(message);

        try {
            pushQueue.put(message);
            logger.info("提交推送消息成功 [Kafka直接], ID: {}, 优先级: {}, 第三方: {}",
                id, message.getPriority(), message.getThirdPartyCode());
            return id;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("提交推送消息失败 [Kafka直接], 线程被中断", e);
            throw new RuntimeException("提交推送消息失败", e);
        }
    }

    /**
     * 丰富消息信息
     */
    private void enrichMessage(PushMessage message) {
        if (message.getCreateTime() == null) {
            message.setCreateTime(LocalDateTime.now());
        }
        if (message.getRetryCount() == null) {
            message.setRetryCount(0);
        }
        if (message.getMaxRetryCount() == null) {
            int defaultMaxRetry = pushSystemProperties.getSystem().getDefaultMaxRetry();
            message.setMaxRetryCount(defaultMaxRetry);
        }
    }

    /**
     * 推送工作线程
     */
    private class PushWorker implements Runnable {
        @Override
        public void run() {
            logger.info("推送工作线程启动: {}", Thread.currentThread().getName());

            while (running.get()) {
                try {
                    int pollTimeout = pushSystemProperties.getSystem().getPollTimeoutSeconds();
                    PushMessage message = pushQueue.poll(pollTimeout, TimeUnit.SECONDS);

                    if (message == null) {
                        continue;
                    }

                    // 检查消息是否过期
                    if (message.getExpireTime() != null &&
                        LocalDateTime.now().isAfter(message.getExpireTime())) {
                        logger.warn("消息已过期，跳过推送, ID: {}", message.getId());
                        continue;
                    }

                    // 执行推送
                    executePush(message);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("推送工作线程被中断: {}", Thread.currentThread().getName());
                    break;
                } catch (Exception e) {
                    logger.error("推送工作线程异常", e);
                }
            }

            logger.info("推送工作线程退出: {}", Thread.currentThread().getName());
        }
    }

    /**
     * 执行推送
     */
    private void executePush(PushMessage message) {
        String accessType = message.getAccessType();

        // 判断推送模式
        if ("EVENT".equals(accessType)) {
            // 事件驱动模式：从extInfo中提取回调URL
            executePushForEvent(message);
        } else {
            // 点对点模式：使用第三方配置
            executePushForDirect(message);
        }
    }

    /**
     * 执行事件驱动推送
     */
    private void executePushForEvent(PushMessage message) {
        try {
            // 从extInfo中解析订阅者信息
            Map<String, Object> extInfo = objectMapper.readValue(message.getExtInfo(), Map.class);
            String callbackUrl = (String) extInfo.get("callbackUrl");
            String secretKey = (String) extInfo.get("secretKey");

            if (callbackUrl == null) {
                logger.error("事件推送失败：回调URL为空, ID: {}", message.getId());
                return;
            }

            logger.info("开始执行事件推送, ID: {}, callbackUrl: {}", message.getId(), callbackUrl);

            // 构建简单的HTTP配置
            ThirdPartyConfig config = buildConfigForCallback(callbackUrl, secretKey);

            // 调用HTTP客户端
            HttpResult result = httpClient.call(message, config);

            if (result.isSuccess()) {
                logger.info("事件推送成功, ID: {}, 耗时: {}ms", message.getId(), result.getCostTime());
            } else {
                logger.warn("事件推送失败, ID: {}, 错误: {}", message.getId(), result.getErrorMessage());
                handlePushFailure(message, config, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("事件推送异常, ID: " + message.getId(), e);
        }
    }

    /**
     * 执行点对点推送
     */
    private void executePushForDirect(PushMessage message) {
        String thirdPartyCode = message.getThirdPartyCode();

        // 获取第三方配置
        ThirdPartyConfig config = configService.getConfig(thirdPartyCode);
        if (config == null) {
            logger.error("未找到第三方配置, code: {}, messageId: {}", thirdPartyCode, message.getId());
            return;
        }

        // 检查第三方是否启用
        if (config.getStatus() == null || config.getStatus() != 1) {
            logger.warn("第三方系统已停用, code: {}, messageId: {}", thirdPartyCode, message.getId());
            return;
        }

        try {
            logger.info("开始执行推送, ID: {}, 第三方: {}, 优先级: {}, 接入方式: {}",
                message.getId(), thirdPartyCode, message.getPriority(), message.getAccessType());

            // 调用HTTP客户端
            HttpResult result = httpClient.call(message, config);

            if (result.isSuccess()) {
                logger.info("推送执行成功, ID: {}, 耗时: {}ms", message.getId(), result.getCostTime());
            } else {
                logger.warn("推送执行失败, ID: {}, 错误: {}", message.getId(), result.getErrorMessage());
                handlePushFailure(message, config, result.getErrorMessage());
            }

        } catch (Exception e) {
            logger.error("推送执行异常, ID: " + message.getId(), e);
            handlePushFailure(message, config, e.getMessage());
        }
    }

    /**
     * 为回调URL构建简单配置
     */
    private ThirdPartyConfig buildConfigForCallback(String callbackUrl, String secretKey) {
        ThirdPartyConfig config = new ThirdPartyConfig();
        config.setApiUrl(callbackUrl);
        config.setMethod("POST");
        config.setConnectTimeout(3000);
        config.setReadTimeout(5000);

        // 简单的Header模板
        Map<String, String> headerTemplate = new HashMap<>();
        headerTemplate.put("Content-Type", "application/json");
        if (secretKey != null) {
            headerTemplate.put("X-Signature", "${sign}");
        }
        config.setHeaderTemplate(headerTemplate);

        // Body模板：直接使用消息内容
        config.setBodyTemplate("${message.content}");

        // 响应解析：默认HTTP 2xx即为成功
        config.setSuccessCondition("$.code == 0");
        config.setMaxRetry(3);
        config.setRetryStrategy("exponential");

        return config;
    }

    /**
     * 处理推送失败
     */
    private void handlePushFailure(PushMessage message, ThirdPartyConfig config, String errorMsg) {
        int retryCount = message.getRetryCount();
        int maxRetryCount = config.getMaxRetry() != null ? config.getMaxRetry() : message.getMaxRetryCount();

        if (retryCount < maxRetryCount) {
            message.setRetryCount(retryCount + 1);
            try {
                // 重新放入队列，采用指数退避策略
                String retryStrategy = config.getRetryStrategy();
                long delayMs;
                if ("fixed".equals(retryStrategy)) {
                    delayMs = config.getRetryInterval() != null ? config.getRetryInterval() : 1000;
                } else {
                    // 指数退避
                    delayMs = (long) Math.pow(2, retryCount) * 1000;
                }

                Thread.sleep(delayMs);
                pushQueue.put(message);
                logger.info("推送失败，重新入队, ID: {}, 重试次数: {}/{}, 延迟: {}ms",
                    message.getId(), retryCount + 1, maxRetryCount, delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("重新入队失败, ID: {}", message.getId(), e);
            }
        } else {
            logger.error("推送失败，已达最大重试次数, ID: {}, 重试次数: {}, 错误: {}",
                message.getId(), retryCount, errorMsg);
            // TODO: 记录到失败表，触发告警
        }
    }

    /**
     * 获取队列状态
     */
    public QueueStatus getQueueStatus() {
        int capacity = pushSystemProperties.getSystem().getQueueCapacity();
        return new QueueStatus(pushQueue.size(), capacity);
    }

    public static class QueueStatus {
        private final int currentSize;
        private final int capacity;

        public QueueStatus(int currentSize, int capacity) {
            this.currentSize = currentSize;
            this.capacity = capacity;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public int getCapacity() {
            return capacity;
        }

        public double getUsageRate() {
            return (double) currentSize / capacity;
        }
    }
}
