package com.push.system.api;

import com.push.system.entity.Subscription;
import com.push.system.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订阅管理API控制器
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    /**
     * 创建订阅
     */
    @PostMapping
    public SubscriptionResponse createSubscription(@RequestBody SubscriptionRequest request) {
        try {
            // 参数校验
            if (request.getSubscriberName() == null || request.getSubscriberName().isEmpty()) {
                return SubscriptionResponse.fail("订阅者名称不能为空");
            }
            if (request.getCallbackUrl() == null || request.getCallbackUrl().isEmpty()) {
                return SubscriptionResponse.fail("回调URL不能为空");
            }
            if (request.getEventTypes() == null || request.getEventTypes().isEmpty()) {
                return SubscriptionResponse.fail("事件类型不能为空");
            }

            Subscription subscription = new Subscription();
            subscription.setSubscriberName(request.getSubscriberName());
            subscription.setCallbackUrl(request.getCallbackUrl());
            subscription.setEventTypes(request.getEventTypes());
            subscription.setSecretKey(request.getSecretKey());
            subscription.setDescription(request.getDescription());

            Subscription created = subscriptionService.createSubscription(subscription);

            return SubscriptionResponse.success(created, "创建成功");
        } catch (Exception e) {
            return SubscriptionResponse.fail(e.getMessage());
        }
    }

    /**
     * 查询订阅详情
     */
    @GetMapping("/{id}")
    public SubscriptionResponse getSubscription(@PathVariable Long id) {
        Subscription subscription = subscriptionService.getSubscription(id);
        if (subscription == null) {
            return SubscriptionResponse.fail("订阅不存在");
        }
        return SubscriptionResponse.success(subscription);
    }

    /**
     * 查询所有订阅
     */
    @GetMapping
    public SubscriptionListResponse listSubscriptions() {
        List<Subscription> subscriptions = subscriptionService.listSubscriptions();
        return SubscriptionListResponse.success(subscriptions);
    }

    /**
     * 更新订阅
     */
    @PutMapping("/{id}")
    public SubscriptionResponse updateSubscription(@PathVariable Long id,
                                                     @RequestBody SubscriptionRequest request) {
        try {
            Subscription subscription = new Subscription();
            subscription.setSubscriberName(request.getSubscriberName());
            subscription.setCallbackUrl(request.getCallbackUrl());
            subscription.setEventTypes(request.getEventTypes());
            subscription.setSecretKey(request.getSecretKey());
            subscription.setStatus(request.getStatus());
            subscription.setDescription(request.getDescription());

            Subscription updated = subscriptionService.updateSubscription(id, subscription);
            return SubscriptionResponse.success(updated);
        } catch (Exception e) {
            return SubscriptionResponse.fail(e.getMessage());
        }
    }

    /**
     * 删除订阅
     */
    @DeleteMapping("/{id}")
    public BaseResponse deleteSubscription(@PathVariable Long id) {
        try {
            subscriptionService.deleteSubscription(id);
            return BaseResponse.success("删除成功");
        } catch (Exception e) {
            return BaseResponse.fail(e.getMessage());
        }
    }

    /**
     * 启用订阅
     */
    @PostMapping("/{id}/enable")
    public BaseResponse enableSubscription(@PathVariable Long id) {
        try {
            Subscription subscription = new Subscription();
            subscription.setStatus(1);
            subscriptionService.updateSubscription(id, subscription);
            return BaseResponse.success("启用成功");
        } catch (Exception e) {
            return BaseResponse.fail(e.getMessage());
        }
    }

    /**
     * 禁用订阅
     */
    @PostMapping("/{id}/disable")
    public BaseResponse disableSubscription(@PathVariable Long id) {
        try {
            Subscription subscription = new Subscription();
            subscription.setStatus(0);
            subscriptionService.updateSubscription(id, subscription);
            return BaseResponse.success("禁用成功");
        } catch (Exception e) {
            return BaseResponse.fail(e.getMessage());
        }
    }

    /**
     * 基础响应
     */
    public static class BaseResponse {
        private boolean success;
        private String message;

        public static BaseResponse success(String message) {
            BaseResponse response = new BaseResponse();
            response.success = true;
            response.message = message;
            return response;
        }

        public static BaseResponse fail(String message) {
            BaseResponse response = new BaseResponse();
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

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 订阅响应
     */
    public static class SubscriptionResponse extends BaseResponse {
        private Subscription data;

        public static SubscriptionResponse success(Subscription data) {
            SubscriptionResponse response = new SubscriptionResponse();
            response.setSuccess(true);
            response.setMessage("操作成功");
            response.data = data;
            return response;
        }

        public static SubscriptionResponse success(Subscription data, String message) {
            SubscriptionResponse response = new SubscriptionResponse();
            response.setSuccess(true);
            response.setMessage(message);
            response.data = data;
            return response;
        }

        public static SubscriptionResponse fail(String message) {
            SubscriptionResponse response = new SubscriptionResponse();
            response.setSuccess(false);
            response.setMessage(message);
            return response;
        }

        public Subscription getData() {
            return data;
        }

        public void setData(Subscription data) {
            this.data = data;
        }
    }

    /**
     * 订阅列表响应
     */
    public static class SubscriptionListResponse extends BaseResponse {
        private List<Subscription> data;

        public static SubscriptionListResponse success(List<Subscription> data) {
            SubscriptionListResponse response = new SubscriptionListResponse();
            response.setSuccess(true);
            response.setMessage("查询成功");
            response.data = data;
            return response;
        }

        public List<Subscription> getData() {
            return data;
        }

        public void setData(List<Subscription> data) {
            this.data = data;
        }
    }
}
