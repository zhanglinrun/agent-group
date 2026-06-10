package com.linrun.trigger.agent.agent.skills.manual.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillLoadingException;
import com.linrun.trigger.agent.agent.skills.manual.registry.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class GlobSkillFileTool extends SkillFileToolSupport
        implements Function<GlobSkillFileTool.Request, GlobSkillFileTool.Result> {

    private static final Logger log = LoggerFactory.getLogger(GlobSkillFileTool.class);

    public static final String NAME = "glob_skill_files";
    public static final String DESCRIPTION = """
            在已注册技能目录内按 glob 模式查找文件。例如 pattern=references/**/*.md 或 scripts/*.py。
            """;

    public GlobSkillFileTool(SkillRegistry skillRegistry) {
        super(skillRegistry);
    }

    public static ToolCallback create(SkillRegistry skillRegistry) {
        return FunctionToolCallback.builder(NAME, new GlobSkillFileTool(skillRegistry))
                .description(DESCRIPTION)
                .inputType(Request.class)
                .build();
    }

    @Override
    public Result apply(Request request) {
        try {
            if (request.pattern() == null || request.pattern().isBlank()) {
                return Result.failure(request.skill(), request.path(), request.pattern(), "pattern is required");
            }
            ResolvedSkillPath resolvedPath = resolveSkillPath(request.skill(), request.path());
            if (!Files.isDirectory(resolvedPath.path())) {
                return Result.failure(request.skill(), request.path(), request.pattern(), "path is not a directory");
            }

            int maxResults = bounded(request.maxResults(), DEFAULT_MAX_GLOB_RESULTS, 1, MAX_GLOB_RESULTS);
            Pattern matcher = buildGlobPattern(request.pattern());
            try (var pathStream = Files.walk(resolvedPath.path())) {
                List<String> matched = pathStream
                        .filter(path -> isRegularFileInsideSkill(resolvedPath.skillRoot(), path))
                        .filter(path -> matcher.matcher(relativePath(resolvedPath.path(), path)).matches())
                        .sorted(Comparator.comparing(path -> relativePath(resolvedPath.skillRoot(), path)))
                        .limit(maxResults + 1L)
                        .map(path -> relativePath(resolvedPath.skillRoot(), path))
                        .toList();
                boolean truncated = matched.size() > maxResults;
                List<String> files = truncated ? matched.subList(0, maxResults) : matched;
                return new Result(true, resolvedPath.skillName(), relativePath(resolvedPath),
                        request.pattern(), files, truncated, null);
            }
        } catch (SkillLoadingException e) {
            log.warn("glob_skill_files rejected, skill={}, path={}, reason={}",
                    request.skill(), request.path(), e.getMessage());
            return Result.failure(request.skill(), request.path(), request.pattern(), e.getMessage());
        } catch (Exception e) {
            log.error("glob_skill_files failed, skill={}, path={}", request.skill(), request.path(), e);
            return Result.failure(request.skill(), request.path(), request.pattern(), "glob_skill_files failed");
        }
    }

    private Pattern buildGlobPattern(String pattern) {
        String normalized = pattern.trim().replace("\\", "/");
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index++) {
            char currentChar = normalized.charAt(index);
            if (currentChar == '*') {
                boolean doubleStar = index + 1 < normalized.length() && normalized.charAt(index + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = index + 2 < normalized.length() && normalized.charAt(index + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?");
                        index++;
                    } else {
                        regex.append(".*");
                    }
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (currentChar == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(currentChar) >= 0) {
                regex.append("\\").append(currentChar);
            } else {
                regex.append(currentChar);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    public record Request(
            @JsonProperty(value = "skill", required = true)
            @JsonPropertyDescription("技能名")
            String skill,

            @JsonProperty("path")
            @JsonPropertyDescription("搜索起点目录，默认技能根目录")
            String path,

            @JsonProperty(value = "pattern", required = true)
            @JsonPropertyDescription("glob 匹配模式")
            String pattern,

            @JsonProperty("max_results")
            @JsonPropertyDescription("最大返回数量，默认 120，最多 300")
            Integer maxResults
    ) {
    }

    public record Result(
            @JsonProperty("success")
            boolean success,
            @JsonProperty("skill")
            String skill,
            @JsonProperty("path")
            String path,
            @JsonProperty("pattern")
            String pattern,
            @JsonProperty("files")
            List<String> files,
            @JsonProperty("truncated")
            boolean truncated,
            @JsonProperty("error")
            String error
    ) {
        static Result failure(String skill, String path, String pattern, String error) {
            return new Result(false, skill, path, pattern, List.of(), false, error);
        }
    }
}















