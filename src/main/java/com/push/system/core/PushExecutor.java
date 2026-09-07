package com.push.system.core;

import com.push.system.model.PushMessage;

/**
 * 推送执行器接口
 * 所有第三方推送渠道都需要实现此接口
 */
public interface PushExecutor {

    /**
     * 执行推送
     * @param message 推送消息
     * @return 是否成功
     */
    boolean execute(PushMessage message);

    /**
     * 获取推送类型
     * @return 推送类型标识
     */
    String getPushType();
}
