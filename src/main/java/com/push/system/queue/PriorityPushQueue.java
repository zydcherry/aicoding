package com.push.system.queue;

import com.push.system.model.PushMessage;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Comparator;

/**
 * 优先级推送队列
 * 使用PriorityBlockingQueue实现，按照优先级和创建时间排序
 */
public class PriorityPushQueue {

    private final BlockingQueue<PushMessage> queue;
    private final int capacity;

    public PriorityPushQueue(int capacity) {
        this.capacity = capacity;
        // 优先级比较器：优先级level小的优先，同优先级按创建时间先进先出
        Comparator<PushMessage> comparator = (m1, m2) -> {
            int priorityCompare = Integer.compare(
                m1.getPriority().getLevel(),
                m2.getPriority().getLevel()
            );
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return m1.getCreateTime().compareTo(m2.getCreateTime());
        };
        this.queue = new PriorityBlockingQueue<>(capacity, comparator);
    }

    /**
     * 添加消息到队列
     */
    public boolean offer(PushMessage message) {
        if (queue.size() >= capacity) {
            return false;
        }
        return queue.offer(message);
    }

    /**
     * 阻塞添加消息
     */
    public void put(PushMessage message) throws InterruptedException {
        queue.put(message);
    }

    /**
     * 从队列取出消息（阻塞）
     */
    public PushMessage take() throws InterruptedException {
        return queue.take();
    }

    /**
     * 从队列取出消息（带超时）
     */
    public PushMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /**
     * 获取队列大小
     */
    public int size() {
        return queue.size();
    }

    /**
     * 队列是否为空
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * 清空队列
     */
    public void clear() {
        queue.clear();
    }
}
