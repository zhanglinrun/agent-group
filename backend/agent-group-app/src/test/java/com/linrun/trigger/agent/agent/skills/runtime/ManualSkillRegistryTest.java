package com.linrun.trigger.agent.agent.skills.runtime;

import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.config.SkillConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualSkillRegistryTest {

    @TempDir
    Path skillsDir;

    @Test
    void deepModeLoadsOnlyMatchingEnabledManualSkill() throws Exception {
        skill("deep-report", """
                ---
                name: deep-report
                description: Deep report skill
                modes: [deep]
                taskTypes: [deep]
                inputParameters: [topic]
                outputConstraints: markdown report
                permissions: [workspace-read]
                allowedTools: [report_writer]
                version: 1.0
                enabled: true
                ---
                # Deep report
                """);
        skill("chat-only", """
                ---
                name: chat-only
                description: Chat only skill
                modes: [chat]
                enabled: true
                ---
                # Chat only
                """);
        skill("disabled-by-frontmatter", """
                ---
                name: disabled-by-frontmatter
                description: Disabled skill
                modes: [deep]
                enabled: false
                ---
                # Disabled
                """);
        skill("disabled-by-admin", """
                ---
                name: disabled-by-admin
                description: Admin disabled skill
                modes: [deep]
                enabled: true
                ---
                # Admin disabled
                """);
        SkillManager manager = SkillManager.create(SkillConfig.builder()
                .addDirectory(skillsDir)
                .excludedSkills(Set.of("disabled-by-admin"))
                .build());

        List<SkillRuntimeDescriptor> skills = new ManualSkillRegistry(manager)
                .availableSkills("deep", "deep");

        assertEquals(1, skills.size());
        assertEquals("deep-report", skills.get(0).name());
        assertEquals(List.of("topic"), skills.get(0).inputParameters());
        assertEquals("markdown report", skills.get(0).outputConstraints());
        assertEquals(List.of("workspace-read"), skills.get(0).permissions());
        assertEquals(List.of("report_writer"), skills.get(0).boundTools());
        assertTrue(skills.get(0).resources().stream().anyMatch(resource -> resource.endsWith("SKILL.md")));
        assertTrue(skills.get(0).toWorkerSummary(Set.of("report_writer")).contains("tools: report_writer"));
        assertTrue(skills.get(0).toWorkerSummary(Set.of("report_writer")).contains("status: ready"));
    }

    private void skill(String name, String content) throws Exception {
        Path dir = skillsDir.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), content);
    }
}
