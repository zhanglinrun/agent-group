package com.linrun.trigger.http.agent.support;

import com.linrun.trigger.agent.agent.skills.manual.SkillManager;
import com.linrun.trigger.agent.agent.skills.manual.config.SkillConfig;
import com.linrun.trigger.agent.agent.skills.manual.tool.GlobSkillFileTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.GrepSkillFileTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.ListSkillDirectoryTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.ReadSkillFileTool;
import com.linrun.trigger.agent.agent.skills.manual.tool.ReadSkillTool;
import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeTools;
import com.linrun.trigger.agent.tool.SkillsTool;
import com.linrun.trigger.http.agent.SkillsAdminStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Agent 的技能运行时解析器，从 AcademicAgentNativeService 抽出。
 * 集中负责 skills 目录定位、会话产物目录准备、技能工具回调组装，以及技能/工作区运行时提示词拼装。
 * 这些逻辑同时被流式编排（初始化各类 Agent）和能力展示（capabilities）使用，所以独立成一个共享组件。
 */
@Component
public class SkillsRuntimeResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillsRuntimeResolver.class);

    @Value("${skills.directory:skills}")
    private String skillsDirectory;

    @Value("${skills.output-directory:outputs}")
    private String skillsOutputDirectory;

    @org.springframework.beans.factory.annotation.Autowired
    private SkillsAdminStateStore skillsAdminStateStore;

    public String resolvedSkillsDirectory() {
        String configured = StringUtils.hasText(skillsDirectory) ? skillsDirectory.trim() : "skills";
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        addSkillsDirectoryCandidate(candidates, Path.of(configured));
        addSkillsDirectoryCandidate(candidates, cwd.resolve(configured));
        for (Path cursor = cwd; cursor != null; cursor = cursor.getParent()) {
            addSkillsDirectoryCandidate(candidates, cursor.resolve(configured));
            addSkillsDirectoryCandidate(candidates, cursor.resolve("skills"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        return configured;
    }

    private void addSkillsDirectoryCandidate(List<Path> candidates, Path candidate) {
        if (candidate == null) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    public String resolvedSkillsOutputDirectory() {
        String configured = StringUtils.hasText(skillsOutputDirectory) ? skillsOutputDirectory.trim() : "outputs";
        Path outputPath = Path.of(configured);
        if (!outputPath.isAbsolute()) {
            Path projectRoot = projectRoot();
            outputPath = projectRoot.resolve(configured).normalize();
        }
        try {
            Files.createDirectories(outputPath);
        } catch (Exception e) {
            LOGGER.warn("academic-agent skills output directory create failed, path={}, reason={}",
                    outputPath, e.getClass().getSimpleName());
        }
        return outputPath.toAbsolutePath().normalize().toString();
    }

    public String sessionSkillsOutputDirectory(String conversationId) {
        Path outputPath = Path.of(resolvedSkillsOutputDirectory())
                .resolve("session_" + encode(conversationId))
                .normalize();
        try {
            Files.createDirectories(outputPath);
            SkillRuntimeTools.prepareSessionOutput(resolvedSkillsDirectory(), projectRoot().toString(), outputPath.toString());
        } catch (Exception e) {
            LOGGER.warn("academic-agent session output directory prepare failed, path={}, reason={}",
                    outputPath, e.getClass().getSimpleName());
        }
        return outputPath.toAbsolutePath().normalize().toString();
    }

    public Path projectRoot() {
        String resolvedSkillsDirectory = resolvedSkillsDirectory();
        if (StringUtils.hasText(resolvedSkillsDirectory)) {
            Path skillsPath = Path.of(resolvedSkillsDirectory).toAbsolutePath().normalize();
            if (Files.isDirectory(skillsPath) && skillsPath.getParent() != null) {
                return skillsPath.getParent();
            }
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if ("backend".equalsIgnoreCase(cwd.getFileName() == null ? "" : cwd.getFileName().toString())
                && cwd.getParent() != null) {
            return cwd.getParent();
        }
        if ("agent-group-app".equalsIgnoreCase(cwd.getFileName() == null ? "" : cwd.getFileName().toString())
                && cwd.getParent() != null
                && cwd.getParent().getParent() != null) {
            return cwd.getParent().getParent();
        }
        return cwd;
    }

    public ToolCallback[] skillsToolCallbacks() {
        String directory = resolvedSkillsDirectory();
        if (!StringUtils.hasText(directory)) {
            return new ToolCallback[0];
        }
        try {
            return new ToolCallback[]{SkillsTool.builder()
                    .addSkillsDirectory(directory)
                    .excludeSkills(disabledSkillNames())
                    .build()};
        } catch (IllegalArgumentException e) {
            LOGGER.warn("academic-agent skills tool init skipped, reason={}", e.getMessage());
            return new ToolCallback[0];
        }
    }

    public SkillManager manualSkillManager() {
        String directory = resolvedSkillsDirectory();
        if (!StringUtils.hasText(directory)) {
            return null;
        }
        try {
            SkillConfig skillConfig = SkillConfig.builder()
                    .addDirectory(directory)
                    .excludedSkills(disabledSkillNames())
                    .build();
            return SkillManager.create(skillConfig);
        } catch (Exception e) {
            LOGGER.warn("academic-agent manual skills init skipped, reason={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private java.util.Set<String> disabledSkillNames() {
        return skillsAdminStateStore == null ? java.util.Set.of() : skillsAdminStateStore.disabledSkillNames();
    }

    public ToolCallback[] manualSkillToolCallbacks(SkillManager skillManager) {
        if (skillManager == null) {
            return new ToolCallback[0];
        }
        var registry = skillManager.getRegistry();
        return new ToolCallback[]{
                ReadSkillTool.create(registry),
                ReadSkillFileTool.create(registry),
                GrepSkillFileTool.create(registry),
                GlobSkillFileTool.create(registry),
                ListSkillDirectoryTool.create(registry)
        };
    }

    public String skillRuntimePrompt(String outputDirectory, boolean webSearchEnabled) {
        String webSearchRule = webSearchEnabled
                ? "- Web search is enabled. Use tools for facts, citations, or fresh information."
                : "- Web search is disabled. Do not call search tools; ask for source material when needed.";
        return """
                ## Skill runtime rules
                - Output directory: %s
                - Write generated files into the output directory and mention file names in the final answer.
                %s
                - Use registered tools for script execution; do not invent execution results.
                - For reports, tables, PPT, or images, clarify the target before calling tools.
                - If a tool fails, report the reason and provide a workable fallback.
                """.formatted(outputDirectory, webSearchRule);
    }

    public String workspaceRuntimePrompt(String outputDirectory, boolean webSearchEnabled, String workspace) {
        String base = skillRuntimePrompt(outputDirectory, webSearchEnabled);
        if ("image".equals(workspace)) {
            return ("""
                    ## Image workspace
                    - Prefer image_generation for image requests and keep prompt, size, and artifact links.
                    - Use planning first when the request needs multi-step design.
                    """ + base).trim();
        }
        if ("data".equals(workspace)) {
            return ("""
                    ## Data workspace
                    - Prefer data_analysis, table_rag, or nl2sql for tables, databases, and structured questions.
                    - Include data scope, key findings, and reproducible query or analysis steps.
                    """ + base).trim();
        }
        if ("trade".equals(workspace)) {
            return ("""
                    ## Trade data workspace
                    - Trade tasks only explain records, status, and exceptions; quota settlement is not an Agent capability.
                    - Separate group-payment success from quota arrival; unsettled groups must not be shown as credited.
                    """ + base).trim();
        }
        if ("trade-diagnosis".equals(workspace)) {
            return ("""
                    ## Trade diagnosis workspace
                    - You are a read-only trade consistency diagnostician: aggregate order, payment, refund and quota-flow facts, then return a conclusion and handling advice.
                    - Use list_trade_orders to spot abnormal orders first, then diagnose_trade_order for a deep per-order consistency check.
                    - Classify each order into one conclusion: QUOTA_GRANT_REQUIRED, REFUND_ROLLBACK_REQUIRED, WAIT_GROUP_SETTLEMENT, TRADE_STATE_CONFLICT, or QUOTA_GRANTED_CONSISTENT.
                    - Red line: never place orders, grant quota, issue refunds, or run any write-side compensation. Only report the conclusion and suggest the next manual step.
                    - A paid but unsettled group order is not yet credited; do not report it as quota arrived.
                    """ + base).trim();
        }
        return base;
    }

    private String encode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
    }
}
