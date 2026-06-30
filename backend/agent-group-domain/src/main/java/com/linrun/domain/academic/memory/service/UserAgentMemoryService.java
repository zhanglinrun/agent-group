package com.linrun.domain.academic.memory.service;

import com.linrun.domain.academic.memory.adapter.UserAgentMemoryRepository;
import com.linrun.domain.academic.memory.model.UserAgentMemory;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserAgentMemoryService {

    public static final String DEFAULT_MEMORY_TYPE = "preference";
    private static final int MAX_TYPE_LENGTH = 32;
    private static final int MAX_CONTENT_LENGTH = 2048;

    private final UserAgentMemoryRepository memoryRepository;

    public UserAgentMemoryService(UserAgentMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    public List<UserAgentMemory> query(String userId, int limit) {
        requireUser(userId);
        return memoryRepository.queryByUser(userId.trim(), false, safeLimit(limit));
    }

    public List<UserAgentMemory> queryEnabled(String userId, int limit) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return memoryRepository.queryByUser(userId.trim(), true, safeLimit(limit));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAgentMemory save(String userId, String memoryType, String content, Boolean enabled) {
        requireUser(userId);
        if (!StringUtils.hasText(content)) {
            throw new AppException("MEMORY_0002", "记忆内容不能为空");
        }
        UserAgentMemory memory = new UserAgentMemory();
        memory.setUserId(userId.trim());
        memory.setMemoryType(memoryType(memoryType));
        memory.setContent(limit(content.trim(), MAX_CONTENT_LENGTH));
        memory.setEnabled(enabled == null || enabled);
        memory.setUpdateTime(LocalDateTime.now());
        memoryRepository.upsert(memory);
        return memoryRepository.queryByType(memory.getUserId(), memory.getMemoryType()).orElse(memory);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAgentMemory saveAuto(String userId, String memoryType, String content) {
        requireUser(userId);
        String normalizedType = memoryType(memoryType);
        UserAgentMemory existing = memoryRepository.queryByType(userId.trim(), normalizedType).orElse(null);
        if (existing != null && Boolean.FALSE.equals(existing.getEnabled())) {
            return existing;
        }
        return save(userId, normalizedType, content, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean disable(String userId, String memoryType) {
        requireUser(userId);
        return memoryRepository.disable(userId.trim(), memoryType(memoryType)) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String userId, String memoryType) {
        requireUser(userId);
        return memoryRepository.delete(userId.trim(), memoryType(memoryType)) > 0;
    }

    private void requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new AppException("MEMORY_0001", "用户编号不能为空");
        }
    }

    private String memoryType(String memoryType) {
        String normalized = StringUtils.hasText(memoryType) ? memoryType.trim() : DEFAULT_MEMORY_TYPE;
        normalized = normalized.toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = DEFAULT_MEMORY_TYPE;
        }
        return limit(normalized, MAX_TYPE_LENGTH);
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
