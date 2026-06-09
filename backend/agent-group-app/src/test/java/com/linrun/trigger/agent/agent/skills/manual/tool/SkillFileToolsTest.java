package com.linrun.trigger.agent.agent.skills.manual.tool;

import com.linrun.trigger.agent.agent.skills.manual.registry.FileSystemSkillRegistry;
import com.linrun.trigger.agent.agent.skills.manual.registry.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillFileToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadAndListFilesInsideRegisteredSkill() throws IOException {
        SkillRegistry registry = prepareRegistry();

        ReadSkillFileTool.Result readResult = new ReadSkillFileTool(registry)
                .apply(new ReadSkillFileTool.Request("analysis-skill", "references/notes.md", 2, 1));
        ListSkillDirectoryTool.Result listResult = new ListSkillDirectoryTool(registry)
                .apply(new ListSkillDirectoryTool.Request("analysis-skill", ".", 3, 20));

        assertTrue(readResult.success());
        assertEquals("references/notes.md", readResult.path());
        assertTrue(readResult.content().contains("2 | beta signal"));
        assertTrue(listResult.success());
        assertTrue(listResult.entries().stream()
                .anyMatch(entry -> entry.path().equals("references/notes.md") && entry.type().equals("file")));
    }

    @Test
    void shouldGrepAndGlobFilesInsideRegisteredSkill() throws IOException {
        SkillRegistry registry = prepareRegistry();

        GrepSkillFileTool.Result grepResult = new GrepSkillFileTool(registry)
                .apply(new GrepSkillFileTool.Request("analysis-skill", ".", "signal", false, false, 10));
        GlobSkillFileTool.Result globResult = new GlobSkillFileTool(registry)
                .apply(new GlobSkillFileTool.Request("analysis-skill", ".", "**/*.md", 10));

        assertTrue(grepResult.success());
        assertEquals(1, grepResult.matches().size());
        assertEquals("references/notes.md", grepResult.matches().getFirst().file());
        assertTrue(globResult.success());
        assertTrue(globResult.files().contains("references/notes.md"));
    }

    @Test
    void shouldRejectPathEscapingSkillDirectory() throws IOException {
        SkillRegistry registry = prepareRegistry();
        Files.writeString(tempDir.resolve("outside.txt"), "secret");

        ReadSkillFileTool.Result result = new ReadSkillFileTool(registry)
                .apply(new ReadSkillFileTool.Request("analysis-skill", "../outside.txt", null, null));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("escapes skill directory"));
    }

    private SkillRegistry prepareRegistry() throws IOException {
        Path skillDir = tempDir.resolve("analysis-skill");
        Files.createDirectories(skillDir.resolve("references"));
        Files.createDirectories(skillDir.resolve("scripts"));
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: analysis skill
                ---
                # Analysis Skill
                """);
        Files.writeString(skillDir.resolve("references").resolve("notes.md"), """
                alpha
                beta signal
                gamma
                """);
        Files.writeString(skillDir.resolve("scripts").resolve("run.py"), "print('ok')");

        return FileSystemSkillRegistry.builder()
                .addDirectory(tempDir)
                .build();
    }
}















