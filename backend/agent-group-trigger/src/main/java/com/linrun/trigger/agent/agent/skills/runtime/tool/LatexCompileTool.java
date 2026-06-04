package com.linrun.trigger.agent.agent.skills.runtime.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LatexCompileTool {

    private static final int MAX_OUTPUT_CHARS = 20000;

    private final Path sessionRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LatexCompileTool(String sessionOutputDirectory) {
        this.sessionRoot = RestrictedToolSupport.normalizeRoot(sessionOutputDirectory);
    }

    public static ToolCallback[] create(String sessionOutputDirectory) {
        return ToolCallbacks.from(new LatexCompileTool(sessionOutputDirectory));
    }

    @Tool(name = "compile_latex", description = """
            Compile a .tex file inside the current session output directory with xelatex.
            This is a restricted replacement for running latex commands from bash.
            """)
    public String compileLatex(
            @ToolParam(description = "Relative or absolute path to a .tex file under the current session output directory.") String texFile,
            @ToolParam(description = "Run xelatex twice. Default true.", required = false) Boolean twoPass) {
        try {
            Path tex = RestrictedToolSupport.resolveInsideRoot(sessionRoot, texFile);
            if (!Files.isRegularFile(tex) || !tex.getFileName().toString().toLowerCase().endsWith(".tex")) {
                return json(Map.of("ok", false, "error", "tex file not found under current session output directory"));
            }
            Path workDir = tex.getParent() == null ? sessionRoot : tex.getParent();
            String fileName = tex.getFileName().toString();
            boolean runTwice = twoPass == null || Boolean.TRUE.equals(twoPass);
            RestrictedToolSupport.CommandResult first = runXelatex(workDir, fileName);
            RestrictedToolSupport.CommandResult second = first;
            if (first.exitCode() == 0 && runTwice) {
                second = runXelatex(workDir, fileName);
            }
            Path pdf = workDir.resolve(fileName.substring(0, fileName.length() - 4) + ".pdf").normalize();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", second.exitCode() == 0 && Files.isRegularFile(pdf));
            response.put("exitCode", second.exitCode());
            response.put("tex", tex.toString());
            response.put("pdf", Files.isRegularFile(pdf) ? pdf.toString() : "");
            response.put("output", second.output());
            return json(response);
        } catch (Exception e) {
            return json(Map.of("ok", false, "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private RestrictedToolSupport.CommandResult runXelatex(Path workDir, String fileName) throws Exception {
        return RestrictedToolSupport.runCommand(
                List.of("xelatex", "-interaction=nonstopmode", "-halt-on-error", "-file-line-error", fileName),
                workDir,
                Duration.ofMinutes(5),
                MAX_OUTPUT_CHARS);
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"ok\":false,\"error\":\"tool response serialization failed\"}";
        }
    }
}
