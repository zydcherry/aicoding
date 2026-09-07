package com.push.system.id;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 分布式唯一ID生成器（基于雪花算法改进）
 * 64位结构：1位符号位 + 41位时间戳 + 10位机器ID + 11位序列号 + 1位接入方式标识
 */
@Component
public class IdGenerator {

    // 起始时间戳 (2024-01-01)
    private static final long START_TIMESTAMP = 1704067200000L;

    // 各部分占用的位数
    private static final long SEQUENCE_BITS = 11L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long TIMESTAMP_BITS = 41L;
    private static final long ACCESS_TYPE_BITS = 1L;

    // 各部分的最大值
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    // 各部分向左的位移
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS + ACCESS_TYPE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_ID_SHIFT + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public IdGenerator(long workerId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                String.format("Worker ID 必须在 0 到 %d 之间", MAX_WORKER_ID));
        }
        this.workerId = workerId;
    }

    /**
     * 自动获取机器ID的构造函数
     */
    public IdGenerator() {
        this.workerId = getDefaultWorkerId();
    }

    /**
     * 生成唯一ID
     * @param accessType 接入方式类型 (0: API直接/API+Kafka, 1: Kafka直接消费)
     * @return 唯一ID
     */
    public synchronized long generateId(AccessType accessType) {
        long timestamp = getCurrentTimestamp();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                String.format("时钟回拨，拒绝生成ID，上次时间戳: %d, 当前时间戳: %d",
                    lastTimestamp, timestamp));
        }

        // 同一毫秒内
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组装64位ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | (sequence << ACCESS_TYPE_BITS)
                | accessType.getCode();
    }

    /**
     * 等待下一毫秒
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 根据机器IP自动生成WorkerId
     */
    private long getDefaultWorkerId() {
        try {
            InetAddress ip = InetAddress.getLocalHost();
            byte[] ipBytes = ip.getAddress();
            return ((ipBytes[ipBytes.length - 2] & 0xFF) << 2)
                    | (ipBytes[ipBytes.length - 1] & 0x03);
        } catch (UnknownHostException e) {
            // 如果获取失败，使用随机值
            return (long) (Math.random() * MAX_WORKER_ID);
        }
    }

    /**
     * 接入方式枚举
     */
    public enum AccessType {
        API(0),           // API直接调用或API+Kafka
        KAFKA_DIRECT(1);  // Kafka直接消费

        private final int code;

        AccessType(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    /**
     * 解析ID中的接入方式
     */
    public static AccessType parseAccessType(long id) {
        int type = (int) (id & 0x01);
        return type == 0 ? AccessType.API : AccessType.KAFKA_DIRECT;
    }

    /**
     * 解析ID中的时间戳
     */
    public static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
    }
}
