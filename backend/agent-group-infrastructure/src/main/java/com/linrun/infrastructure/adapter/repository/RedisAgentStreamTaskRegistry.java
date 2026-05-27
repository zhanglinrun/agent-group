package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.adapter.AgentStreamTaskRegistry;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisAgentStreamTaskRegistry implements AgentStreamTaskRegistry, InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisAgentStreamTaskRegistry.class);
    private static final Duration TASK_TTL = Duration.ofMinutes(30);
    private static final long TTL_REFRESH_INTERVAL_MINUTES = 5L;

    private final RedissonClient redissonClient;
    private final String keyPrefix;
    private final String instanceId;
    private final RTopic stopTopic;
    private final Map<String, TaskInfo> localTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService ttlRefreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "agent-stream-ttl-refresh");
        thread.setDaemon(true);
        return thread;
    });

    private int stopListenerId = -1;

    public RedisAgentStreamTaskRegistry(RedissonClient redissonClient,
                                        @Value("${agent.group.redis.key-prefix:agent-group}") String keyPrefix) {
        this.redissonClient = redissonClient;
        this.keyPrefix = StringUtils.hasText(keyPrefix) ? keyPrefix : "agent-group";
        this.instanceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        this.stopTopic = redissonClient.getTopic(stopTopic());
    }

    @Override
    public void afterPropertiesSet() {
        try {
            stopListenerId = stopTopic.addListener(String.class, (channel, sessionId) -> handleRemoteStop(sessionId));
        } catch (Exception e) {
            LOGGER.warn("agent stream stop topic subscribe fallback, reason={}", e.getClass().getSimpleName());
        }
        ttlRefreshScheduler.scheduleAtFixedRate(
                this::refreshTaskTtls,
                TTL_REFRESH_INTERVAL_MINUTES,
                TTL_REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    @Override
    public void destroy() {
        if (stopListenerId >= 0) {
            try {
                stopTopic.removeListener(stopListenerId);
            } catch (Exception e) {
                LOGGER.warn("agent stream stop topic listener remove fallback, reason={}", e.getClass().getSimpleName());
            }
        }
        ttlRefreshScheduler.shutdownNow();
        new ArrayList<>(localTasks.keySet()).forEach(sessionId -> {
            TaskInfo taskInfo = localTasks.remove(sessionId);
            if (taskInfo != null) {
                clearRedisIfOwner(sessionId, taskInfo);
            }
        });
    }

    @Override
    public boolean register(String sessionId, String requestId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(requestId)) {
            return true;
        }
        if (localTasks.containsKey(sessionId)) {
            return false;
        }
        TaskInfo taskInfo = new TaskInfo(requestId, ownerValue(requestId));
        try {
            boolean acquired = bucket(sessionId)
                    .trySet(taskInfo.ownerValue(), TASK_TTL.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                return false;
            }
        } catch (Exception e) {
            LOGGER.warn("agent stream task redis fallback, reason={}", e.getClass().getSimpleName());
        }
        TaskInfo existed = localTasks.putIfAbsent(sessionId, taskInfo);
        if (existed != null) {
            clearRedisIfOwner(sessionId, taskInfo);
        }
        return existed == null;
    }

    @Override
    public void bind(String sessionId, String requestId, Runnable cancelCallback) {
        TaskInfo taskInfo = localTasks.get(sessionId);
        if (taskInfo != null && taskInfo.requestId().equals(requestId)) {
            taskInfo.setCancelCallback(cancelCallback);
        }
    }

    @Override
    public void complete(String sessionId, String requestId) {
        TaskInfo taskInfo = localTasks.get(sessionId);
        if (taskInfo == null || !taskInfo.requestId().equals(requestId)) {
            return;
        }
        localTasks.remove(sessionId);
        clearRedisIfOwner(sessionId, taskInfo);
    }

    private void handleRemoteStop(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        TaskInfo taskInfo = localTasks.remove(sessionId);
        if (taskInfo == null) {
            return;
        }
        try {
            taskInfo.cancel();
        } catch (Exception e) {
            LOGGER.warn("agent stream remote stop fallback, reason={}", e.getClass().getSimpleName());
        } finally {
            clearRedisIfOwner(sessionId, taskInfo);
        }
    }

    private void refreshTaskTtls() {
        if (localTasks.isEmpty()) {
            return;
        }
        localTasks.forEach((sessionId, taskInfo) -> {
            try {
                RBucket<String> bucket = bucket(sessionId);
                String holder = bucket.get();
                if (taskInfo.ownerValue().equals(holder)) {
                    bucket.expire(TASK_TTL);
                } else {
                    localTasks.remove(sessionId, taskInfo);
                }
            } catch (Exception e) {
                LOGGER.warn("agent stream task ttl refresh fallback, reason={}", e.getClass().getSimpleName());
            }
        });
    }

    private void clearRedisIfOwner(String sessionId, TaskInfo taskInfo) {
        try {
            RBucket<String> bucket = bucket(sessionId);
            String holder = bucket.get();
            if (taskInfo.ownerValue().equals(holder)) {
                bucket.delete();
            }
        } catch (Exception e) {
            LOGGER.warn("agent stream task clear fallback, reason={}", e.getClass().getSimpleName());
        }
    }

    private RBucket<String> bucket(String sessionId) {
        return redissonClient.getBucket(key(sessionId));
    }

    private String ownerValue(String requestId) {
        return instanceId + ":" + requestId;
    }

    private String key(String sessionId) {
        return keyPrefix + ":guide:task:" + sessionId;
    }

    private String stopTopic() {
        return keyPrefix + ":guide:stop:topic";
    }

    private static class TaskInfo {

        private final String requestId;
        private final String ownerValue;
        private volatile Runnable cancelCallback;

        private TaskInfo(String requestId, String ownerValue) {
            this.requestId = requestId;
            this.ownerValue = ownerValue;
        }

        private String requestId() {
            return requestId;
        }

        private String ownerValue() {
            return ownerValue;
        }

        private void setCancelCallback(Runnable cancelCallback) {
            this.cancelCallback = cancelCallback;
        }

        private void cancel() {
            Runnable callback = cancelCallback;
            if (callback != null) {
                callback.run();
            }
        }
    }
}
