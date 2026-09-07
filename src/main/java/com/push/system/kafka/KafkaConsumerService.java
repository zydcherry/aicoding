package com.push.system.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.core.UnifiedPusher;
import com.push.system.model.PushMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Kafka消费者服务
 * 消费内部Kafka消息（方式2）和外部Kafka消息（方式3）
 */
@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private UnifiedPusher unifiedPusher;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 消费高优先级消息（方式2：内部Kafka）
     */
    @KafkaListener(
        topics = "${push.kafka.topics.push-high-priority}",
        groupId = "push-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeHighPriority(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consumeInternalMessage(record, ack);
    }

    /**
     * 消费普通优先级消息（方式2：内部Kafka）
     */
    @KafkaListener(
        topics = "${push.kafka.topics.push-normal-priority}",
        groupId = "push-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNormalPriority(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consumeInternalMessage(record, ack);
    }

    /**
     * 消费低优先级消息（方式2：内部Kafka）
     */
    @KafkaListener(
        topics = "${push.kafka.topics.push-low-priority}",
        groupId = "push-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLowPriority(ConsumerRecord<String, String> record, Acknowledgment ack) {
        consumeInternalMessage(record, ack);
    }

    /**
     * 消费外部Kafka消息（方式3：外部Kafka直接推送）
     */
    @KafkaListener(
        topics = "${push.kafka.topics.external-push}",
        groupId = "external-push-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeExternalMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            logger.info("接收到外部Kafka消息, Key: {}, Partition: {}, Offset: {}",
                record.key(), record.partition(), record.offset());

            PushMessage message = objectMapper.readValue(record.value(), PushMessage.class);

            // 使用方式3提交（生成新的ID，标识为KAFKA_DIRECT）
            Long pushId = unifiedPusher.submitFromKafkaDirect(message);

            logger.info("外部Kafka消息已提交到推送队列, 新生成ID: {}", pushId);

            // 手动提交偏移量
            ack.acknowledge();

        } catch (Exception e) {
            logger.error("消费外部Kafka消息失败, Key: " + record.key(), e);
            // 不提交偏移量，消息会重新消费
        }
    }

    /**
     * 消费内部Kafka消息的通用逻辑
     */
    private void consumeInternalMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            logger.info("接收到内部Kafka消息, Topic: {}, Key: {}, Partition: {}, Offset: {}",
                record.topic(), record.key(), record.partition(), record.offset());

            PushMessage message = objectMapper.readValue(record.value(), PushMessage.class);

            // 使用方式2提交（消息中已包含ID）
            unifiedPusher.submitFromKafkaInternal(message);

            logger.info("内部Kafka消息已提交到推送队列, ID: {}", message.getId());

            // 手动提交偏移量
            ack.acknowledge();

        } catch (Exception e) {
            logger.error("消费内部Kafka消息失败, Key: " + record.key(), e);
            // 不提交偏移量，消息会重新消费
        }
    }
}
