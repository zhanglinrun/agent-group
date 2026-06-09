package com.linrun.trigger.agent.agent.skills.manual.registry;

import com.linrun.trigger.agent.agent.skills.manual.SkillPromptFormatter;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillLoadingException;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemSkillRegistryScriptTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDiscoverSkillScriptsFromDirectoryAndYaml() throws IOException {
        Path skillDir = tempDir.resolve("sql-analysis");
        Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: 分析 SQL 指标并生成报�?                allowedTools:
                  - script_runner
                ---
                # SQL Analysis
                """);
        Files.writeString(skillDir.resolve("scripts").resolve("summarize.py"), "print('summary')");
        Files.writeString(skillDir.resolve("scripts").resolve("report.py"), "print('report')");
        Files.writeString(skillDir.resolve("scripts.yaml"), """
                scripts:
                  build_report:
                    path: scripts/report.py
                    runtime: python
                    description: 生成分析报告
                    timeout: 30
                """);

        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .addDirectory(tempDir)
                .build();

        SkillMetadata metadata = registry.get("sql-analysis");

        assertEquals("分析 SQL 指标并生成报�?, metadata.description());
        assertTrue(metadata.scripts().containsKey("summarize"));
        assertTrue(metadata.scripts().containsKey("build_report"));
        assertTrue(metadata.buildScriptSummaries().stream().anyMatch(line -> line.contains("build_report")));
        assertTrue(SkillPromptFormatter.format(List.of(metadata)).contains("可用脚本"));
    }

    @Test
    void shouldRejectScriptPathEscapingSkillDirectory() throws IOException {
        Path skillDir = tempDir.resolve("unsafe-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: 不安全脚�?                ---
                # Unsafe
                """);
        Files.writeString(tempDir.resolve("outside.py"), "print('outside')");
        Files.writeString(skillDir.resolve("scripts.yaml"), """
                scripts:
                  outside:
                    path: ../outside.py
                    runtime: python
                """);

        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .addDirectory(tempDir)
                .build();

        assertThrows(SkillLoadingException.class, registry::listAll);
    }
}















