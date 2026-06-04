package com.linrun.trigger.agent.agent.skills.runtime.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliFetchTool {

    private static final Pattern BVID_PATTERN = Pattern.compile("(BV[0-9A-Za-z]+)");
    private static final Set<String> ALLOWED_WHISPER_MODELS = Set.of("tiny", "base", "small", "medium");
    private static final int MAX_OUTPUT_CHARS = 20000;

    private final Path projectRoot;
    private final Path sessionRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BilibiliFetchTool(String projectRoot, String sessionOutputDirectory) {
        this.projectRoot = RestrictedToolSupport.normalizeRoot(projectRoot);
        this.sessionRoot = RestrictedToolSupport.normalizeRoot(sessionOutputDirectory);
    }

    public static ToolCallback[] create(String projectRoot, String sessionOutputDirectory) {
        return ToolCallbacks.from(new BilibiliFetchTool(projectRoot, sessionOutputDirectory));
    }

    @Tool(name = "bilibili_fetch", description = """
            Fetch Bilibili metadata, cover, subtitles, media, and optional Whisper subtitles.
            This is a restricted replacement for running python tools/bilibili_fetch.py from bash.
            All outputs are written under the current session output directory.
            """)
    public String fetch(
            @ToolParam(description = "Bilibili URL or BV id. It must contain a BV id.") String url,
            @ToolParam(description = "Whether to run Whisper when platform subtitles are absent. Default true.", required = false) Boolean transcribe,
            @ToolParam(description = "Whisper model: tiny, base, small, or medium. Default base.", required = false) String whisperModel,
            @ToolParam(description = "Only fetch metadata, cover, and platform subtitles. Default false.", required = false) Boolean noMedia,
            @ToolParam(description = "Optional cookies.txt path under the current session output directory.", required = false) String cookiesFile) {
        try {
            String bvid = extractBvid(url);
            if (!StringUtils.hasText(bvid)) {
                return json(Map.of("ok", false, "error", "input must contain a BV id"));
            }
            Path helper = projectRoot.resolve("tools").resolve("bilibili_fetch.py").normalize();
            if (!Files.isRegularFile(helper)) {
                return json(Map.of("ok", false, "error", "project helper not found: tools/bilibili_fetch.py"));
            }
            Files.createDirectories(sessionRoot);
            Path outDir = sessionRoot.resolve("bilibili_" + bvid).normalize();
            if (!outDir.startsWith(sessionRoot)) {
                return json(Map.of("ok", false, "error", "invalid output directory"));
            }
            Files.createDirectories(outDir);

            String model = normalizeWhisperModel(whisperModel);
            boolean onlyMetadata = Boolean.TRUE.equals(noMedia);
            boolean shouldTranscribe = !onlyMetadata && (transcribe == null || Boolean.TRUE.equals(transcribe));

            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(helper.toString());
            command.add(url.trim());
            command.add("--out-dir");
            command.add(outDir.toString());
            if (onlyMetadata) {
                command.add("--no-media");
            } else if (shouldTranscribe) {
                command.add("--transcribe");
                command.add("--whisper-model");
                command.add(model);
            }
            if (StringUtils.hasText(cookiesFile)) {
                Path cookies = RestrictedToolSupport.resolveInsideRoot(sessionRoot, cookiesFile);
                if (!Files.isRegularFile(cookies)) {
                    return json(Map.of("ok", false, "error", "cookies.txt not found under current session output directory"));
                }
                command.add("--cookies");
                command.add(cookies.toString());
            }

            Duration timeout = onlyMetadata ? Duration.ofMinutes(5) : Duration.ofMinutes(45);
            RestrictedToolSupport.CommandResult result =
                    RestrictedToolSupport.runCommand(command, sessionRoot, timeout, MAX_OUTPUT_CHARS);
            Path resultFile = outDir.resolve("fetch_result.json");
            if (result.exitCode() == 0 && Files.isRegularFile(resultFile)) {
                return Files.readString(resultFile, StandardCharsets.UTF_8);
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", false);
            response.put("exitCode", result.exitCode());
            response.put("outDir", outDir.toString());
            response.put("output", result.output());
            return json(response);
        } catch (Exception e) {
            return json(Map.of("ok", false, "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private String extractBvid(String text) {
        Matcher matcher = BVID_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String normalizeWhisperModel(String whisperModel) {
        String model = StringUtils.hasText(whisperModel) ? whisperModel.trim().toLowerCase() : "base";
        return ALLOWED_WHISPER_MODELS.contains(model) ? model : "base";
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"ok\":false,\"error\":\"tool response serialization failed\"}";
        }
    }
}
