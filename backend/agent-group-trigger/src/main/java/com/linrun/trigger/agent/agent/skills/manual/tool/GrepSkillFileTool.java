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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class GrepSkillFileTool extends SkillFileToolSupport
        implements Function<GrepSkillFileTool.Request, GrepSkillFileTool.Result> {

    private static final Logger log = LoggerFactory.getLogger(GrepSkillFileTool.class);

    public static final String NAME = "grep_skill_files";
    public static final String DESCRIPTION = """
            在已注册技能目录内搜索文本内容。path 可以是技能内文件或目录；regex=false 时按普通关键词搜索。
            """;

    public GrepSkillFileTool(SkillRegistry skillRegistry) {
        super(skillRegistry);
    }

    public static ToolCallback create(SkillRegistry skillRegistry) {
        return FunctionToolCallback.builder(NAME, new GrepSkillFileTool(skillRegistry))
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
            Pattern pattern = buildPattern(request.pattern(), Boolean.TRUE.equals(request.regex()),
                    Boolean.TRUE.equals(request.caseSensitive()));
            int maxMatches = bounded(request.maxMatches(), DEFAULT_MAX_GREP_MATCHES, 1, MAX_GREP_MATCHES);
            List<Path> files = candidateFiles(resolvedPath);
            List<Match> matches = new ArrayList<>();

            for (Path file : files) {
                if (Files.size(file) > MAX_SEARCH_FILE_BYTES) {
                    continue;
                }
                List<String> lines = readLines(file);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (pattern.matcher(line).find()) {
                        matches.add(new Match(relativePath(resolvedPath.skillRoot(), file),
                                index + 1, truncateLine(line)));
                        if (matches.size() >= maxMatches) {
                            return new Result(true, resolvedPath.skillName(), relativePath(resolvedPath),
                                    request.pattern(), matches, true, null);
                        }
                    }
                }
            }

            return new Result(true, resolvedPath.skillName(), relativePath(resolvedPath),
                    request.pattern(), matches, false, null);
        } catch (PatternSyntaxException e) {
            return Result.failure(request.skill(), request.path(), request.pattern(), "invalid regex: " + e.getMessage());
        } catch (SkillLoadingException e) {
            log.warn("grep_skill_files rejected, skill={}, path={}, reason={}",
                    request.skill(), request.path(), e.getMessage());
            return Result.failure(request.skill(), request.path(), request.pattern(), e.getMessage());
        } catch (Exception e) {
            log.error("grep_skill_files failed, skill={}, path={}", request.skill(), request.path(), e);
            return Result.failure(request.skill(), request.path(), request.pattern(), "grep_skill_files failed");
        }
    }

    private List<Path> candidateFiles(ResolvedSkillPath resolvedPath) throws Exception {
        Path basePath = resolvedPath.path();
        if (Files.isRegularFile(basePath)) {
            return List.of(basePath);
        }
        if (!Files.isDirectory(basePath)) {
            throw new SkillLoadingException(resolvedPath.skillName(), "path is not a file or directory");
        }
        try (var pathStream = Files.walk(basePath)) {
            return pathStream
                    .filter(path -> isRegularFileInsideSkill(resolvedPath.skillRoot(), path))
                    .sorted(Comparator.comparing(path -> relativePath(resolvedPath.skillRoot(), path)))
                    .toList();
        }
    }

    private Pattern buildPattern(String rawPattern, boolean regex, boolean caseSensitive) {
        String expression = regex ? rawPattern : Pattern.quote(rawPattern);
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(expression, flags);
    }

    public record Request(
            @JsonProperty(value = "skill", required = true)
            @JsonPropertyDescription("技能名")
            String skill,

            @JsonProperty("path")
            @JsonPropertyDescription("搜索起点，默认技能根目录")
            String path,

            @JsonProperty(value = "pattern", required = true)
            @JsonPropertyDescription("关键词或正则表达式")
            String pattern,

            @JsonProperty("regex")
            @JsonPropertyDescription("是否按正则表达式搜索，默认 false")
            Boolean regex,

            @JsonProperty("case_sensitive")
            @JsonPropertyDescription("是否区分大小写，默认 false")
            Boolean caseSensitive,

            @JsonProperty("max_matches")
            @JsonPropertyDescription("最大返回命中数，默认 80，最多 200")
            Integer maxMatches
    ) {
    }

    public record Match(
            @JsonProperty("file")
            String file,
            @JsonProperty("line")
            int line,
            @JsonProperty("text")
            String text
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
            @JsonProperty("matches")
            List<Match> matches,
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















