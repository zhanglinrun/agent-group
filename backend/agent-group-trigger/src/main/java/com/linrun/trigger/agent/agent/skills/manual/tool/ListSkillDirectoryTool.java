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

public class ListSkillDirectoryTool extends SkillFileToolSupport
        implements Function<ListSkillDirectoryTool.Request, ListSkillDirectoryTool.Result> {

    private static final Logger log = LoggerFactory.getLogger(ListSkillDirectoryTool.class);

    public static final String NAME = "list_skill_directory";
    public static final String DESCRIPTION = """
            列出已注册技能目录内的文件和子目录�?            只能浏览 skill 对应目录下的内容�?            """;

    public ListSkillDirectoryTool(SkillRegistry skillRegistry) {
        super(skillRegistry);
    }

    public static ToolCallback create(SkillRegistry skillRegistry) {
        return FunctionToolCallback.builder(NAME, new ListSkillDirectoryTool(skillRegistry))
                .description(DESCRIPTION)
                .inputType(Request.class)
                .build();
    }

    @Override
    public Result apply(Request request) {
        try {
            ResolvedSkillPath resolvedPath = resolveSkillPath(request.skill(), request.path());
            if (!Files.isDirectory(resolvedPath.path())) {
                return Result.failure(request.skill(), request.path(), "path is not a directory");
            }

            int depth = bounded(request.maxDepth(), DEFAULT_LIST_DEPTH, 1, MAX_LIST_DEPTH);
            int maxEntries = bounded(request.maxEntries(), DEFAULT_MAX_LIST_ENTRIES, 1, MAX_LIST_ENTRIES);
            try (var pathStream = Files.walk(resolvedPath.path(), depth)) {
                List<Entry> entries = pathStream
                        .filter(path -> !path.equals(resolvedPath.path()))
                        .filter(path -> isInsideSkillRoot(resolvedPath.skillRoot(), path))
                        .sorted(Comparator.comparing(path -> relativePath(resolvedPath.skillRoot(), path)))
                        .limit(maxEntries + 1L)
                        .map(path -> toEntry(resolvedPath.skillRoot(), path))
                        .toList();
                boolean truncated = entries.size() > maxEntries;
                List<Entry> displayEntries = truncated ? entries.subList(0, maxEntries) : entries;
                return new Result(true, resolvedPath.skillName(), relativePath(resolvedPath),
                        displayEntries, truncated, null);
            }
        } catch (SkillLoadingException e) {
            log.warn("list_skill_directory rejected, skill={}, path={}, reason={}",
                    request.skill(), request.path(), e.getMessage());
            return Result.failure(request.skill(), request.path(), e.getMessage());
        } catch (Exception e) {
            log.error("list_skill_directory failed, skill={}, path={}", request.skill(), request.path(), e);
            return Result.failure(request.skill(), request.path(), "list_skill_directory failed");
        }
    }

    private Entry toEntry(Path skillRoot, Path path) {
        String type = Files.isDirectory(path) ? "directory" : "file";
        Long size = null;
        if (Files.isRegularFile(path)) {
            try {
                size = Files.size(path);
            } catch (Exception ignored) {
                size = null;
            }
        }
        return new Entry(relativePath(skillRoot, path), type, size);
    }

    public record Request(
            @JsonProperty(value = "skill", required = true)
            @JsonPropertyDescription("技能名�?)
            String skill,

            @JsonProperty("path")
            @JsonPropertyDescription("要浏览的技能内目录，默认技能根目录")
            String path,

            @JsonProperty("max_depth")
            @JsonPropertyDescription("最大遍历深度，默认 2，最�?8")
            Integer maxDepth,

            @JsonProperty("max_entries")
            @JsonPropertyDescription("最大返回条目数，默�?160，最�?400")
            Integer maxEntries
    ) {
    }

    public record Entry(
            @JsonProperty("path")
            String path,
            @JsonProperty("type")
            String type,
            @JsonProperty("size")
            Long size
    ) {
    }

    public record Result(
            @JsonProperty("success")
            boolean success,
            @JsonProperty("skill")
            String skill,
            @JsonProperty("path")
            String path,
            @JsonProperty("entries")
            List<Entry> entries,
            @JsonProperty("truncated")
            boolean truncated,
            @JsonProperty("error")
            String error
    ) {
        static Result failure(String skill, String path, String error) {
            return new Result(false, skill, path, List.of(), false, error);
        }
    }
}















