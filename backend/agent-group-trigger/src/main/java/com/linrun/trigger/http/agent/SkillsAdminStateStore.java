package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.types.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 运营端技能启停的共享状态存储。
 *
 * 维护被禁用的技能名集合，持久化到 data/skills-admin-state.json。
 * 既被 {@link SkillsAdminHandler}（运营端读写）使用，
 * 也被 {@code SkillsRuntimeResolver}（Agent 加载时过滤）使用，
 * 抽成独立 Bean 避免循环依赖。
 */
@Service
public class SkillsAdminStateStore {

    private static final Logger log = LoggerFactory.getLogger(SkillsAdminStateStore.class);
    private static final String DEFAULT_STATE_FILE = "data/skills-admin-state.json";

    private final ObjectMapper objectMapper;
    private final Path stateFile;
    private final ConcurrentMap<String, Boolean> disabledSkills = new ConcurrentHashMap<>();

    @Autowired
    public SkillsAdminStateStore(ObjectMapper objectMapper) {
        this(objectMapper, Path.of(System.getProperty("user.dir", "."), DEFAULT_STATE_FILE)
                .toAbsolutePath().normalize());
    }

    SkillsAdminStateStore(ObjectMapper objectMapper, Path stateFile) {
        this.objectMapper = objectMapper == null ? new ObjectMapper().findAndRegisterModules() : objectMapper;
        this.stateFile = stateFile == null ? null : stateFile.toAbsolutePath().normalize();
        loadState();
    }

    public Set<String> disabledSkillNames() {
        return new LinkedHashSet<>(disabledSkills.keySet());
    }

    public boolean isDisabled(String skillName) {
        return StringUtils.hasText(skillName) && disabledSkills.containsKey(skillName.trim());
    }

    public void setDisabled(String skillName, boolean disabled) {
        String name = normalize(skillName);
        if (!StringUtils.hasText(name)) {
            throw new AppException("SKILL_ADMIN_0001", "skill name cannot be blank");
        }
        if (disabled) {
            disabledSkills.put(name, Boolean.TRUE);
        } else {
            disabledSkills.remove(name);
        }
        persistState();
    }

    private void loadState() {
        if (stateFile == null || !Files.isRegularFile(stateFile)) {
            return;
        }
        try {
            Map<?, ?> state = objectMapper.readValue(stateFile.toFile(), Map.class);
            Object disabled = state == null ? null : state.get("disabledSkills");
            if (disabled instanceof List<?> list) {
                for (Object item : list) {
                    String name = normalize(String.valueOf(item));
                    if (StringUtils.hasText(name)) {
                        disabledSkills.put(name, Boolean.TRUE);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("skills admin state load skipped, reason={}", e.getClass().getSimpleName());
        }
    }

    private void persistState() {
        if (stateFile == null) {
            return;
        }
        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("disabledSkills", disabledSkills.keySet().stream().sorted().toList());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
        } catch (IOException e) {
            throw new AppException("SKILL_ADMIN_0501", "保存技能启停状态失败", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
