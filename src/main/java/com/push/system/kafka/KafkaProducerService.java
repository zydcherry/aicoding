package com.push.system.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.model.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * Kafka生产者服务
 * 用于方式2：API发送消息到Kafka
 */
@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // Kafka主题配置，按优先级分离
    private static final String TOPIC_HIGH_PRIORITY = "push-high-priority";
    private static final String TOPIC_NORMAL_PRIORITY = "push-normal-priority";
    private static final String TOPIC_LOW_PRIORITY = "push-low-priority";

    /**
     * 发送消息到Kafka
     * 根据优先级发送到不同的主题
     */
    public void sendToKafka(PushMessage message) {
        try {
            String topic = getTopicByPriority(message.getPriority());
            String messageJson = objectMapper.writeValueAsString(message);

            ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                topic,
                message.getId().toString(),
                messageJson
            );

            future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
                @Override
                public void onSuccess(SendResult<String, String> result) {
                    logger.info("消息发送到Kafka成功, ID: {}, Topic: {}, Partition: {}, Offset: {}",
                        message.getId(),
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }

                @Override
                public void onFailure(Throwable ex) {
                    logger.error("消息发送到Kafka失败, ID: " + message.getId(), ex);
                    // TODO: 失败处理，可以考虑降级到直接推送
                }
            });

        } catch (Exception e) {
            logger.error("序列化消息失败, ID: " + message.getId(), e);
            throw new RuntimeException("发送消息到Kafka失败", e);
        }
    }

    /**
     * 根据优先级获取对应的Kafka主题
     */
    private String getTopicByPriority(PushMessage.Priority priority) {
        if (priority == null) {
            return TOPIC_NORMAL_PRIORITY;
        }

        switch (priority) {
            case HIGH:
                return TOPIC_HIGH_PRIORITY;
            case LOW:
                return TOPIC_LOW_PRIORITY;
            case NORMAL:
            default:
                return TOPIC_NORMAL_PRIORITY;
        }
    }
}
