package com.linrun.trigger.agent.agent.skills.runtime.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class LatexCompileTool {

    private static final int MAX_OUTPUT_CHARS = 20000;
    private static final Set<String> MEDIA_INTERMEDIATE_FILES = Set.of(
            "source.mp4",
            "audio.wav",
            "video.m4s",
            "audio.m4s"
    );
    private static final String NGINX_LISTINGS_LANGUAGE = String.join("\n",
            "\\lstdefinelanguage{nginx}{",
            "  morekeywords={server,location,root,index,try_files,listen,server_name,return,rewrite,proxy_pass,proxy_set_header},",
            "  sensitive=false,",
            "  morecomment=[l]{\\#},",
            "  morestring=[b]\",",
            "}",
            "");

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
            @ToolParam(description = "Run xelatex twice. Default true.", required = false) Boolean twoPass,
            @ToolParam(description = "Delete large temporary media files after a successful PDF compile. Default true.", required = false) Boolean cleanupMedia) {
        try {
            Path tex = RestrictedToolSupport.resolveInsideRoot(sessionRoot, texFile);
            if (!Files.isRegularFile(tex) || !tex.getFileName().toString().toLowerCase().endsWith(".tex")) {
                return json(Map.of("ok", false, "error", "tex file not found under current session output directory"));
            }
            Path workDir = tex.getParent() == null ? sessionRoot : tex.getParent();
            String fileName = tex.getFileName().toString();
            List<String> preflightFixes = normalizeTexForPortableCompile(tex);
            boolean runTwice = twoPass == null || Boolean.TRUE.equals(twoPass);
            RestrictedToolSupport.CommandResult first = runXelatex(workDir, fileName);
            RestrictedToolSupport.CommandResult second = first;
            if (first.exitCode() == 0 && runTwice) {
                second = runXelatex(workDir, fileName);
            }
            Path pdf = workDir.resolve(fileName.substring(0, fileName.length() - 4) + ".pdf").normalize();
            boolean ok = second.exitCode() == 0 && Files.isRegularFile(pdf);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", ok);
            response.put("exitCode", second.exitCode());
            response.put("tex", tex.toString());
            response.put("pdf", Files.isRegularFile(pdf) ? pdf.toString() : "");
            if (!preflightFixes.isEmpty()) {
                response.put("preflightFixes", preflightFixes);
            }
            if (ok && (cleanupMedia == null || Boolean.TRUE.equals(cleanupMedia))) {
                response.put("mediaCleanup", cleanupMediaIntermediates());
            }
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

    List<String> normalizeTexForPortableCompile(Path tex) throws IOException {
        String content = Files.readString(tex, StandardCharsets.UTF_8);
        String updated = content;
        List<String> fixes = new ArrayList<>();
        String withoutNoto = updated.replaceAll("(?m)^\\s*\\\\set(?:CJK)?mainfont\\s*\\{\\s*Noto Serif CJK SC\\s*}\\s*\\R?", "");
        if (!withoutNoto.equals(updated)) {
            updated = withoutNoto;
            fixes.add("removed unsupported Noto Serif CJK SC font override");
        }
        if (updated.contains("[language=nginx]") && !updated.contains("\\lstdefinelanguage{nginx}")) {
            int lstSetIndex = updated.indexOf("\\lstset{");
            if (lstSetIndex >= 0) {
                updated = updated.substring(0, lstSetIndex) + NGINX_LISTINGS_LANGUAGE + updated.substring(lstSetIndex);
                fixes.add("defined listings language nginx");
            }
        }
        if (!updated.equals(content)) {
            Files.writeString(tex, updated, StandardCharsets.UTF_8);
        }
        return fixes;
    }

    Map<String, Object> cleanupMediaIntermediates() throws Exception {
        List<String> deletedFiles = new ArrayList<>();
        long deletedBytes = 0L;
        try (Stream<Path> paths = Files.walk(sessionRoot)) {
            List<Path> candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isMediaIntermediate)
                    .toList();
            for (Path candidate : candidates) {
                long size = Files.size(candidate);
                if (Files.deleteIfExists(candidate)) {
                    deletedFiles.add(candidate.getFileName().toString());
                    deletedBytes += size;
                }
            }
        }
        Map<String, Object> cleanup = new LinkedHashMap<>();
        cleanup.put("deletedCount", deletedFiles.size());
        cleanup.put("deletedBytes", deletedBytes);
        cleanup.put("deletedFiles", deletedFiles);
        return cleanup;
    }

    private boolean isMediaIntermediate(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        return MEDIA_INTERMEDIATE_FILES.contains(name);
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"ok\":false,\"error\":\"tool response serialization failed\"}";
        }
    }
}
