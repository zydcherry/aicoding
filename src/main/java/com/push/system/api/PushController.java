package com.push.system.api;

import com.push.system.core.UnifiedPusher;
import com.push.system.kafka.KafkaProducerService;
import com.push.system.model.PushMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 推送API控制器
 * 提供方式1（API直接推送）和方式2（API发送到Kafka）的接口
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    @Autowired
    private UnifiedPusher unifiedPusher;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    /**
     * 方式1：API直接推送
     * 同步返回推送ID，消息立即进入推送队列
     */
    @PostMapping("/direct")
    public PushResponse pushDirect(@RequestBody PushRequest request) {
        try {
            // 参数校验
            if (request.getThirdPartyCode() == null || request.getThirdPartyCode().isEmpty()) {
                return PushResponse.fail("第三方系统编码不能为空");
            }

            PushMessage message = convertToMessage(request);
            Long pushId = unifiedPusher.submitForDirectPush(message);

            return PushResponse.success(pushId, "消息已提交到推送队列");
        } catch (Exception e) {
            return PushResponse.fail("提交失败: " + e.getMessage());
        }
    }

    /**
     * 方式2：API发送到Kafka
     * 同步返回推送ID，消息先发送到Kafka，由消费者异步推送
     */
    @PostMapping("/async")
    public PushResponse pushAsync(@RequestBody PushRequest request) {
        try {
            // 参数校验
            if (request.getThirdPartyCode() == null || request.getThirdPartyCode().isEmpty()) {
                return PushResponse.fail("第三方系统编码不能为空");
            }

            PushMessage message = convertToMessage(request);

            // 先生成ID
            Long pushId = unifiedPusher.submitForDirectPush(message);
            message.setId(pushId);

            // 发送到Kafka
            kafkaProducerService.sendToKafka(message);

            return PushResponse.success(pushId, "消息已发送到Kafka队列");
        } catch (Exception e) {
            return PushResponse.fail("提交失败: " + e.getMessage());
        }
    }

    /**
     * 查询推送状态
     */
    @GetMapping("/status/{pushId}")
    public PushStatusResponse queryStatus(@PathVariable Long pushId) {
        // TODO: 实现状态查询逻辑
        PushStatusResponse response = new PushStatusResponse();
        response.setPushId(pushId);
        response.setStatus("PROCESSING");
        response.setMessage("查询功能待实现");
        return response;
    }

    /**
     * 获取队列状态
     */
    @GetMapping("/queue/status")
    public QueueStatusResponse getQueueStatus() {
        UnifiedPusher.QueueStatus status = unifiedPusher.getQueueStatus();
        return new QueueStatusResponse(
            status.getCurrentSize(),
            status.getCapacity(),
            status.getUsageRate()
        );
    }

    /**
     * 转换请求为消息对象
     */
    private PushMessage convertToMessage(PushRequest request) {
        PushMessage message = new PushMessage();
        message.setBizId(request.getBizId());
        message.setPriority(request.getPriority());
        message.setThirdPartyCode(request.getThirdPartyCode());
        message.setMessageType(request.getMessageType());
        message.setContent(request.getContent());
        message.setExpireTime(request.getExpireTime());
        message.setExtInfo(request.getExtInfo());
        return message;
    }

    /**
     * 推送响应
     */
    public static class PushResponse {
        private boolean success;
        private Long pushId;
        private String message;

        public static PushResponse success(Long pushId, String message) {
            PushResponse response = new PushResponse();
            response.success = true;
            response.pushId = pushId;
            response.message = message;
            return response;
        }

        public static PushResponse fail(String message) {
            PushResponse response = new PushResponse();
            response.success = false;
            response.message = message;
            return response;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public Long getPushId() {
            return pushId;
        }

        public void setPushId(Long pushId) {
            this.pushId = pushId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 队列状态响应
     */
    public static class QueueStatusResponse {
        private int currentSize;
        private int capacity;
        private double usageRate;

        public QueueStatusResponse() {}

        public QueueStatusResponse(int currentSize, int capacity, double usageRate) {
            this.currentSize = currentSize;
            this.capacity = capacity;
            this.usageRate = usageRate;
        }

        public int getCurrentSize() {
            return currentSize;
        }

        public void setCurrentSize(int currentSize) {
            this.currentSize = currentSize;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public double getUsageRate() {
            return usageRate;
        }

        public void setUsageRate(double usageRate) {
            this.usageRate = usageRate;
        }
    }

    /**
     * 推送状态响应
     */
    public static class PushStatusResponse {
        private Long pushId;
        private String status;
        private String message;

        public Long getPushId() {
            return pushId;
        }

        public void setPushId(Long pushId) {
            this.pushId = pushId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
