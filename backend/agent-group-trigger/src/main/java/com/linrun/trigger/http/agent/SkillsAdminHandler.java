package com.linrun.trigger.http.agent;

import com.linrun.trigger.http.agent.support.SkillsRuntimeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端技能管理：扫描 skills 目录列出技能，支持启用/禁用。
 *
 * 列表从 skills 目录的子目录扫描 SKILL.md 得到；
 * 启停状态由 {@link SkillsAdminStateStore} 持久化，Agent 加载时按此过滤。
 */
@Service
public class SkillsAdminHandler {

    private static final Logger log = LoggerFactory.getLogger(SkillsAdminHandler.class);
    private static final String SKILL_MD_FILE = "SKILL.md";

    private final SkillsRuntimeResolver skillsRuntimeResolver;
    private final SkillsAdminStateStore stateStore;

    public SkillsAdminHandler(SkillsRuntimeResolver skillsRuntimeResolver, SkillsAdminStateStore stateStore) {
        this.skillsRuntimeResolver = skillsRuntimeResolver;
        this.stateStore = stateStore;
    }

    public List<Map<String, Object>> listSkills() {
        Path directory = resolvedSkillsDirectory();
        List<Map<String, Object>> skills = new ArrayList<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return skills;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isDirectory).forEach(subDir -> {
                Path skillFile = subDir.resolve(SKILL_MD_FILE);
                if (!Files.isRegularFile(skillFile)) {
                    return;
                }
                String name = subDir.getFileName().toString();
                skills.add(skillEntry(name, descriptionOf(name, skillFile), !stateStore.isDisabled(name)));
            });
        } catch (IOException e) {
            log.warn("skills list failed, reason={}", e.getClass().getSimpleName());
        }
        skills.sort(Comparator.comparing(item -> String.valueOf(item.get("name"))));
        return skills;
    }

    public Map<String, Object> setEnabled(String skillName, boolean enabled) {
        stateStore.setDisabled(skillName, !enabled);
        return skillEntry(skillName, "", enabled);
    }

    private Path resolvedSkillsDirectory() {
        try {
            String directory = skillsRuntimeResolver.resolvedSkillsDirectory();
            return StringUtils.hasText(directory) ? Path.of(directory).toAbsolutePath().normalize() : null;
        } catch (Exception e) {
            log.warn("skills directory resolve failed, reason={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String descriptionOf(String name, Path skillFile) {
        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String stripped = stripFrontmatter(content);
            for (String line : stripped.split("\n")) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                return text;
            }
            return "Skill: " + name;
        } catch (Exception e) {
            return "Skill: " + name;
        }
    }

    private String stripFrontmatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return content == null ? "" : content;
        }
        int endIndex = content.indexOf("\n---", 3);
        if (endIndex < 0) {
            return content;
        }
        return content.substring(endIndex + 4).trim();
    }

    private Map<String, Object> skillEntry(String name, String description, boolean enabled) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("description", description);
        result.put("enabled", enabled);
        return result;
    }
}
