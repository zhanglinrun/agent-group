package com.linrun.trigger.agent.agent.skills.runtime;

import com.linrun.trigger.agent.agent.skills.runtime.tool.BilibiliFetchTool;
import com.linrun.trigger.agent.agent.skills.runtime.tool.LatexCompileTool;
import com.linrun.trigger.agent.agent.skills.runtime.tool.VideoFrameTool;
import com.linrun.trigger.agent.tool.FileSystemTools;
import com.linrun.trigger.agent.tool.ToolMergeUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SkillRuntimeTools {

    private static final String BILIBILI_RENDER_PDF_SKILL = "bilibili-render-pdf";

    private SkillRuntimeTools() {
    }

    public static ToolCallback[] create(String skillsDirectory,
                                        String projectRoot,
                                        String sessionOutputDirectory) {
        ToolCallback[] commonTools = FileSystemTools.createRestricted(sessionOutputDirectory);
        if (!skillAvailable(skillsDirectory, projectRoot, BILIBILI_RENDER_PDF_SKILL)) {
            return commonTools;
        }
        return ToolMergeUtils.mergeTools(
                commonTools,
                BilibiliFetchTool.create(projectRoot, sessionOutputDirectory),
                VideoFrameTool.create(sessionOutputDirectory),
                LatexCompileTool.create(sessionOutputDirectory)
        );
    }

    public static void prepareSessionOutput(String skillsDirectory,
                                            String projectRoot,
                                            String sessionOutputDirectory) {
        if (!skillAvailable(skillsDirectory, projectRoot, BILIBILI_RENDER_PDF_SKILL)) {
            return;
        }
        copyIfExists(skillPath(skillsDirectory, projectRoot, BILIBILI_RENDER_PDF_SKILL)
                        .resolve("assets")
                        .resolve("notes-template.tex"),
                Path.of(sessionOutputDirectory).toAbsolutePath().normalize()
                        .resolve("notes-template.tex"));
    }

    private static boolean skillAvailable(String skillsDirectory, String projectRoot, String skillName) {
        return Files.isDirectory(skillPath(skillsDirectory, projectRoot, skillName));
    }

    private static Path skillPath(String skillsDirectory, String projectRoot, String skillName) {
        Path skillsRoot;
        if (StringUtils.hasText(skillsDirectory)) {
            skillsRoot = Path.of(skillsDirectory.trim());
            if (!skillsRoot.isAbsolute()) {
                skillsRoot = Path.of(projectRoot).toAbsolutePath().normalize().resolve(skillsRoot).normalize();
            }
        } else {
            skillsRoot = Path.of(projectRoot).toAbsolutePath().normalize().resolve("skills");
        }
        return skillsRoot.resolve(skillName).normalize();
    }

    private static void copyIfExists(Path source, Path target) {
        try {
            Path normalizedSource = source.toAbsolutePath().normalize();
            Path normalizedTarget = target.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalizedSource) || normalizedTarget.getParent() == null) {
                return;
            }
            Files.createDirectories(normalizedTarget.getParent());
            Files.copy(normalizedSource, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }
}
