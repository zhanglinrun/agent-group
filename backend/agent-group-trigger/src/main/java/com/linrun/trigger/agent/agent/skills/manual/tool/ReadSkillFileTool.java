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
import java.util.List;
import java.util.function.Function;

public class ReadSkillFileTool extends SkillFileToolSupport
        implements Function<ReadSkillFileTool.Request, ReadSkillFileTool.Result> {

    private static final Logger log = LoggerFactory.getLogger(ReadSkillFileTool.class);

    public static final String NAME = "read_skill_file";
    public static final String DESCRIPTION = """
            读取已注册技能目录内的文本文件，支持按行截取。
            只能访问 skill 对应目录下已经存在的文件，不能读取技能目录外的路径。
            """;

    public ReadSkillFileTool(SkillRegistry skillRegistry) {
        super(skillRegistry);
    }

    public static ToolCallback create(SkillRegistry skillRegistry) {
        return FunctionToolCallback.builder(NAME, new ReadSkillFileTool(skillRegistry))
                .description(DESCRIPTION)
                .inputType(Request.class)
                .build();
    }

    @Override
    public Result apply(Request request) {
        try {
            ResolvedSkillPath resolvedPath = resolveSkillPath(request.skill(), request.path());
            if (!Files.isRegularFile(resolvedPath.path())) {
                return Result.failure(request.skill(), request.path(), "path is not a regular file");
            }

            List<String> lines = readLines(resolvedPath.path());
            int startLine = bounded(request.startLine(), 1, 1, Math.max(lines.size(), 1));
            int lineCount = bounded(request.lineCount(), DEFAULT_READ_LINE_COUNT, 1, MAX_READ_LINE_COUNT);
            int fromIndex = Math.min(lines.size(), startLine - 1);
            int toIndex = Math.min(lines.size(), fromIndex + lineCount);

            StringBuilder content = new StringBuilder();
            for (int index = fromIndex; index < toIndex; index++) {
                content.append(index + 1).append(" | ").append(lines.get(index)).append("\n");
            }
            boolean truncatedByLines = toIndex < lines.size();
            String finalContent = truncate(content.toString(), MAX_READ_CHARS);
            boolean truncatedByChars = finalContent.length() < content.length();

            return new Result(true, resolvedPath.skillName(), relativePath(resolvedPath), startLine,
                    toIndex, lines.size(), finalContent, truncatedByLines || truncatedByChars, null);
        } catch (SkillLoadingException e) {
            log.warn("read_skill_file rejected, skill={}, path={}, reason={}",
                    request.skill(), request.path(), e.getMessage());
            return Result.failure(request.skill(), request.path(), e.getMessage());
        } catch (Exception e) {
            log.error("read_skill_file failed, skill={}, path={}", request.skill(), request.path(), e);
            return Result.failure(request.skill(), request.path(), "read_skill_file failed");
        }
    }

    public record Request(
            @JsonProperty(value = "skill", required = true)
            @JsonPropertyDescription("技能名称")
            String skill,

            @JsonProperty(value = "path", required = true)
            @JsonPropertyDescription("技能目录内的相对路径，也兼容技能目录下的绝对路径")
            String path,

            @JsonProperty("start_line")
            @JsonPropertyDescription("起始行号，默认 1")
            Integer startLine,

            @JsonProperty("line_count")
            @JsonPropertyDescription("读取行数，默认 80，最多 300")
            Integer lineCount
    ) {
    }

    public record Result(
            @JsonProperty("success")
            boolean success,
            @JsonProperty("skill")
            String skill,
            @JsonProperty("path")
            String path,
            @JsonProperty("start_line")
            int startLine,
            @JsonProperty("end_line")
            int endLine,
            @JsonProperty("total_lines")
            int totalLines,
            @JsonProperty("content")
            String content,
            @JsonProperty("truncated")
            boolean truncated,
            @JsonProperty("error")
            String error
    ) {
        static Result failure(String skill, String path, String error) {
            return new Result(false, skill, path, 0, 0, 0, null, false, error);
        }
    }
}
