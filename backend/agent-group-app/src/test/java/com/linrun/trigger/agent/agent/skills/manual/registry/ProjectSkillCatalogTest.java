package com.linrun.trigger.agent.agent.skills.manual.registry;

import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSkillCatalogTest {

    @Test
    void shouldLoadProjectSkillCatalog() {
        Path skillsRoot = resolveProjectSkillsRoot();
        SkillRegistry registry = FileSystemSkillRegistry.builder()
                .addDirectory(skillsRoot)
                .build();

        List<SkillMetadata> skills = registry.listAll();
        SkillMetadata chartSkill = registry.get("chart-visualization");
        SkillMetadata dataSkill = registry.get("data-analysis");

        assertTrue(skills.size() >= 14);
        assertTrue(skills.stream().anyMatch(skill -> skill.name().equals("github-deep-research")));
        assertTrue(skills.stream().anyMatch(skill -> skill.name().equals("gpt-image-2-style-library")));
        assertTrue(chartSkill.scripts().containsKey("generate"));
        assertTrue(dataSkill.scripts().containsKey("analyze"));
    }

    private Path resolveProjectSkillsRoot() {
        List<Path> candidates = List.of(
                Path.of("skills"),
                Path.of("..", "skills"),
                Path.of("..", "..", "skills")
        );
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("project skills directory not found"));
    }
}















