package com.push.system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 推送系统配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "push")
public class PushSystemProperties {

    private SystemConfig system = new SystemConfig();
    private KafkaConfig kafka = new KafkaConfig();
    private HttpConfig http = new HttpConfig();
    private SubscriptionConfig subscription = new SubscriptionConfig();

    public SystemConfig getSystem() {
        return system;
    }

    public void setSystem(SystemConfig system) {
        this.system = system;
    }

    public KafkaConfig getKafka() {
        return kafka;
    }

    public void setKafka(KafkaConfig kafka) {
        this.kafka = kafka;
    }

    public HttpConfig getHttp() {
        return http;
    }

    public void setHttp(HttpConfig http) {
        this.http = http;
    }

    public SubscriptionConfig getSubscription() {
        return subscription;
    }

    public void setSubscription(SubscriptionConfig subscription) {
        this.subscription = subscription;
    }

    /**
     * 系统配置
     */
    public static class SystemConfig {
        private Integer queueCapacity = 10000;
        private Integer workerThreadCount = 10;
        private Integer pollTimeoutSeconds = 3;
        private Integer defaultMaxRetry = 3;

        public Integer getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(Integer queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public Integer getWorkerThreadCount() {
            return workerThreadCount;
        }

        public void setWorkerThreadCount(Integer workerThreadCount) {
            this.workerThreadCount = workerThreadCount;
        }

        public Integer getPollTimeoutSeconds() {
            return pollTimeoutSeconds;
        }

        public void setPollTimeoutSeconds(Integer pollTimeoutSeconds) {
            this.pollTimeoutSeconds = pollTimeoutSeconds;
        }

        public Integer getDefaultMaxRetry() {
            return defaultMaxRetry;
        }

        public void setDefaultMaxRetry(Integer defaultMaxRetry) {
            this.defaultMaxRetry = defaultMaxRetry;
        }
    }

    /**
     * Kafka配置
     */
    public static class KafkaConfig {
        private TopicsConfig topics = new TopicsConfig();

        public TopicsConfig getTopics() {
            return topics;
        }

        public void setTopics(TopicsConfig topics) {
            this.topics = topics;
        }

        public static class TopicsConfig {
            private String pushMessage = "push-message-topic";
            private String externalPush = "external-push-topic";
            private String pushHighPriority = "push-high-priority";
            private String pushNormalPriority = "push-normal-priority";
            private String pushLowPriority = "push-low-priority";

            public String getPushMessage() {
                return pushMessage;
            }

            public void setPushMessage(String pushMessage) {
                this.pushMessage = pushMessage;
            }

            public String getExternalPush() {
                return externalPush;
            }

            public void setExternalPush(String externalPush) {
                this.externalPush = externalPush;
            }

            public String getPushHighPriority() {
                return pushHighPriority;
            }

            public void setPushHighPriority(String pushHighPriority) {
                this.pushHighPriority = pushHighPriority;
            }

            public String getPushNormalPriority() {
                return pushNormalPriority;
            }

            public void setPushNormalPriority(String pushNormalPriority) {
                this.pushNormalPriority = pushNormalPriority;
            }

            public String getPushLowPriority() {
                return pushLowPriority;
            }

            public void setPushLowPriority(String pushLowPriority) {
                this.pushLowPriority = pushLowPriority;
            }
        }
    }

    /**
     * HTTP配置
     */
    public static class HttpConfig {
        private Integer defaultConnectTimeout = 3000;
        private Integer defaultReadTimeout = 5000;
        private Integer defaultMaxRetry = 3;

        public Integer getDefaultConnectTimeout() {
            return defaultConnectTimeout;
        }

        public void setDefaultConnectTimeout(Integer defaultConnectTimeout) {
            this.defaultConnectTimeout = defaultConnectTimeout;
        }

        public Integer getDefaultReadTimeout() {
            return defaultReadTimeout;
        }

        public void setDefaultReadTimeout(Integer defaultReadTimeout) {
            this.defaultReadTimeout = defaultReadTimeout;
        }

        public Integer getDefaultMaxRetry() {
            return defaultMaxRetry;
        }

        public void setDefaultMaxRetry(Integer defaultMaxRetry) {
            this.defaultMaxRetry = defaultMaxRetry;
        }
    }

    /**
     * 订阅配置
     */
    public static class SubscriptionConfig {
        private Boolean loadSampleData = true;
        private Integer eventTypeIndexInitialCapacity = 100;

        public Boolean getLoadSampleData() {
            return loadSampleData;
        }

        public void setLoadSampleData(Boolean loadSampleData) {
            this.loadSampleData = loadSampleData;
        }

        public Integer getEventTypeIndexInitialCapacity() {
            return eventTypeIndexInitialCapacity;
        }

        public void setEventTypeIndexInitialCapacity(Integer eventTypeIndexInitialCapacity) {
            this.eventTypeIndexInitialCapacity = eventTypeIndexInitialCapacity;
        }
    }
}
