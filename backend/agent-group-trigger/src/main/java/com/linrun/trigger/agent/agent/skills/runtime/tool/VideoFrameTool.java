package com.linrun.trigger.agent.agent.skills.runtime.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class VideoFrameTool {

    private static final int MAX_OUTPUT_CHARS = 12000;
    private static final Pattern SAFE_NAME = Pattern.compile("[^A-Za-z0-9._-]+");

    private final Path projectRoot;
    private final Path sessionRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoFrameTool(String sessionOutputDirectory) {
        this(Path.of("").toAbsolutePath().normalize().toString(), sessionOutputDirectory);
    }

    public VideoFrameTool(String projectRoot, String sessionOutputDirectory) {
        this.projectRoot = RestrictedToolSupport.normalizeRoot(projectRoot);
        this.sessionRoot = RestrictedToolSupport.normalizeRoot(sessionOutputDirectory);
    }

    public static ToolCallback[] create(String sessionOutputDirectory) {
        return ToolCallbacks.from(new VideoFrameTool(sessionOutputDirectory));
    }

    public static ToolCallback[] create(String projectRoot, String sessionOutputDirectory) {
        return ToolCallbacks.from(new VideoFrameTool(projectRoot, sessionOutputDirectory));
    }

    @Tool(name = "extract_video_frames", description = """
            Extract jpg frames from a video file under the current session output directory with ffmpeg.
            This is a restricted replacement for running ffmpeg from bash.
            """)
    public String extractFrames(
            @ToolParam(description = "Relative or absolute path to a video file under the current session output directory.") String videoFile,
            @ToolParam(description = "Start time in seconds. Default 0.", required = false) Double startSeconds,
            @ToolParam(description = "End time in seconds. Default start + 1.", required = false) Double endSeconds,
            @ToolParam(description = "Seconds between frames. Default 1, minimum 0.2.", required = false) Double everySeconds,
            @ToolParam(description = "Output file prefix. Default frame.", required = false) String outputPrefix) {
        try {
            Path video = RestrictedToolSupport.resolveInsideRoot(sessionRoot, videoFile);
            if (!Files.isRegularFile(video)) {
                return json(Map.of("ok", false, "error", "video file not found under current session output directory"));
            }
            double start = Math.max(0.0d, startSeconds == null ? 0.0d : startSeconds);
            double end = endSeconds == null ? start + 1.0d : Math.max(start + 0.2d, endSeconds);
            double duration = Math.min(600.0d, end - start);
            double interval = Math.max(0.2d, everySeconds == null ? 1.0d : everySeconds);
            String prefix = safePrefix(outputPrefix);

            Path framesDir = sessionRoot.resolve("frames").normalize();
            Files.createDirectories(framesDir);
            Path outputPattern = framesDir.resolve(prefix + "_%03d.jpg").normalize();
            if (!outputPattern.startsWith(sessionRoot)) {
                return json(Map.of("ok", false, "error", "invalid frame output path"));
            }
            String fps = "fps=1/" + trimDouble(interval);
            String ffmpeg = resolveFfmpegCommand();
            RestrictedToolSupport.CommandResult result = RestrictedToolSupport.runCommand(
                    List.of(ffmpeg, "-y", "-ss", trimDouble(start), "-i", video.toString(),
                            "-t", trimDouble(duration), "-vf", fps, outputPattern.toString()),
                    sessionRoot,
                    Duration.ofMinutes(5),
                    MAX_OUTPUT_CHARS);
            List<String> frames;
            try (Stream<Path> paths = Files.list(framesDir)) {
                frames = paths
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().startsWith(prefix + "_"))
                        .sorted()
                        .limit(80)
                        .map(Path::toString)
                        .toList();
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("ok", result.exitCode() == 0 && !frames.isEmpty());
            response.put("exitCode", result.exitCode());
            response.put("frames", frames);
            response.put("output", result.output());
            return json(response);
        } catch (Exception e) {
            return json(Map.of("ok", false, "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private String safePrefix(String outputPrefix) {
        String prefix = StringUtils.hasText(outputPrefix) ? outputPrefix.trim() : "frame";
        prefix = SAFE_NAME.matcher(prefix).replaceAll("_");
        return prefix.isBlank() ? "frame" : prefix;
    }

    private String trimDouble(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    String resolveFfmpegCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        List<Path> candidates = windows
                ? List.of(
                projectRoot.resolve("tools").resolve("runtime-bin").resolve("ffmpeg.cmd"),
                projectRoot.resolve("tools").resolve("runtime-bin").resolve("ffmpeg.exe"))
                : List.of(projectRoot.resolve("tools").resolve("runtime-bin").resolve("ffmpeg"));
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized.toString();
            }
        }
        return "ffmpeg";
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"ok\":false,\"error\":\"tool response serialization failed\"}";
        }
    }
}















