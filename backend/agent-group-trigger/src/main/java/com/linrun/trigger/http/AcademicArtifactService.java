package com.linrun.trigger.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class AcademicArtifactService {

    private static final long SCAN_SKEW_MILLIS = 5000L;
    private static final int MAX_ARTIFACTS = 20;
    private static final Set<String> DOWNLOADABLE_EXTENSIONS = Set.of(
            "pdf", "tex", "srt", "vtt", "txt", "md", "docx", "pptx", "xlsx", "svg", "zip");
    private static final Pattern LOCAL_DELIVERABLE_PATH = Pattern.compile(
            "(?i)([A-Z]:\\\\[^\\r\\n`]+?\\.(pdf|tex|srt|vtt|txt|md|docx|pptx|xlsx|svg|zip))");
    private static final Pattern DELIVERABLE_FILE_NAME = Pattern.compile(
            "(?i)([^`\\r\\n<>:\"/\\\\|?*]+?\\.(pdf|tex|srt|vtt|txt|md|docx|pptx|xlsx|svg|zip))");

    private final ObjectMapper objectMapper;

    @Value("${skills.output-directory:outputs}")
    private String outputDirectory;

    @Value("${skills.directory:skills}")
    private String skillsDirectory;

    public AcademicArtifactService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AcademicSessionDetailResponse.Artifact> collectAndSave(String userId,
                                                                        String sessionId,
                                                                        long sinceMillis) {
        List<AcademicSessionDetailResponse.Artifact> artifacts = scanArtifacts(sessionId, sinceMillis);
        if (!artifacts.isEmpty()) {
            saveManifest(userId, sessionId, artifacts);
        }
        return artifacts;
    }

    public List<AcademicSessionDetailResponse.Artifact> collectFromAnswerAndSave(String userId,
                                                                                 String sessionId,
                                                                                 String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        List<AcademicSessionDetailResponse.Artifact> artifacts = new ArrayList<>();
        Matcher matcher = LOCAL_DELIVERABLE_PATH.matcher(answer);
        Path outputRoot = root();
        Path projectRoot = projectRoot().toAbsolutePath().normalize();
        Path sessionDir = outputRoot.resolve("session_" + encode(sessionId)).normalize();
        while (matcher.find() && artifacts.size() < MAX_ARTIFACTS) {
            try {
                Path source = Path.of(matcher.group(1)).toAbsolutePath().normalize();
                if (!Files.isRegularFile(source) || !source.startsWith(projectRoot)) {
                    continue;
                }
                Path target = source.startsWith(outputRoot)
                        ? source
                        : sessionDir.resolve(source.getFileName().toString()).normalize();
                if (!target.startsWith(outputRoot)) {
                    continue;
                }
                if (!source.equals(target)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                artifacts.add(toArtifact(sessionId, outputRoot.relativize(target), lastModified(target)));
            } catch (Exception ignored) {
            }
        }
        collectMentionedFileNames(sessionId, answer, artifacts);
        if (!artifacts.isEmpty()) {
            saveManifest(userId, sessionId, artifacts);
        }
        return artifacts;
    }

    private void collectMentionedFileNames(String sessionId,
                                           String answer,
                                           List<AcademicSessionDetailResponse.Artifact> artifacts) {
        if (artifacts.size() >= MAX_ARTIFACTS) {
            return;
        }
        java.util.Set<String> existingNames = new java.util.HashSet<>();
        for (AcademicSessionDetailResponse.Artifact artifact : artifacts) {
            existingNames.add(artifact.getFileName().toLowerCase(Locale.ROOT));
        }
        Matcher matcher = DELIVERABLE_FILE_NAME.matcher(answer);
        Path outputRoot = root();
        while (matcher.find() && artifacts.size() < MAX_ARTIFACTS) {
            String fileName = matcher.group(1).trim();
            String key = fileName.toLowerCase(Locale.ROOT);
            if (existingNames.contains(key)) {
                continue;
            }
            Path file = findNewestOutputFile(outputRoot, fileName);
            if (file == null) {
                continue;
            }
            artifacts.add(toArtifact(sessionId, outputRoot.relativize(file), lastModified(file)));
            existingNames.add(key);
        }
    }

    private Path findNewestOutputFile(Path outputRoot, String fileName) {
        try (Stream<Path> paths = Files.walk(outputRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> fileName.equalsIgnoreCase(path.getFileName().toString()))
                    .max(Comparator.comparingLong(this::lastModified))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public List<AcademicSessionDetailResponse.Artifact> loadManifest(String userId, String sessionId) {
        Path manifest = manifestPath(userId, sessionId);
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        try {
            List<Map<String, String>> items = objectMapper.readValue(
                    Files.readString(manifest, StandardCharsets.UTF_8),
                    new TypeReference<List<Map<String, String>>>() {});
            List<AcademicSessionDetailResponse.Artifact> artifacts = new ArrayList<>();
            for (Map<String, String> item : items) {
                String artifactId = item.get("artifactId");
                if (!StringUtils.hasText(artifactId)) {
                    continue;
                }
                Path file = resolveArtifactPath(artifactId);
                if (Files.isRegularFile(file)) {
                    artifacts.add(toArtifact(sessionId, root().relativize(file), Files.getLastModifiedTime(file).toMillis()));
                }
            }
            return artifacts;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public DownloadArtifact resolveDownload(String artifactId) {
        Path file = resolveArtifactPath(artifactId);
        if (!Files.isRegularFile(file)) {
            throw new AppException("ARTIFACT_0001", "生成文件不存在或已被清理");
        }
        try {
            String contentType = Files.probeContentType(file);
            return new DownloadArtifact(file, file.getFileName().toString(),
                    StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                    Files.size(file));
        } catch (IOException e) {
            throw new AppException("ARTIFACT_0002", "生成文件读取失败");
        }
    }

    public String sanitizeLocalPaths(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        Matcher matcher = LOCAL_DELIVERABLE_PATH.matcher(content);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rawPath = matcher.group(1);
            String fileName;
            try {
                fileName = Path.of(rawPath).getFileName().toString();
            } catch (Exception ignored) {
                fileName = "生成文件";
            }
            matcher.appendReplacement(buffer,
                    Matcher.quoteReplacement("`" + fileName + "`（可通过下方下载按钮获取）"));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public Map<String, Object> toEventPayload(AcademicSessionDetailResponse.Artifact artifact) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artifactId", artifact.getArtifactId());
        data.put("artifactType", artifact.getArtifactType());
        data.put("title", artifact.getTitle());
        data.put("fileName", artifact.getFileName());
        data.put("fileSize", artifact.getFileSize());
        data.put("content", artifact.getFileName());
        data.put("downloadUrl", artifact.getDownloadUrl());
        return data;
    }

    private List<AcademicSessionDetailResponse.Artifact> scanArtifacts(String sessionId, long sinceMillis) {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        long cutoff = Math.max(0L, sinceMillis - SCAN_SKEW_MILLIS);
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isDownloadable(path))
                    .filter(path -> modifiedAfter(path, cutoff))
                    .sorted(Comparator
                            .comparingInt((Path path) -> extensionRank(extension(path)))
                            .thenComparing((Path path) -> lastModified(path), Comparator.reverseOrder()))
                    .limit(MAX_ARTIFACTS)
                    .map(path -> toArtifact(sessionId, root.relativize(path), lastModified(path)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private AcademicSessionDetailResponse.Artifact toArtifact(String sessionId, Path relativePath, long modifiedAt) {
        String fileName = relativePath.getFileName().toString();
        String artifactId = encode(relativePath.toString().replace('\\', '/'));
        AcademicSessionDetailResponse.Artifact artifact = new AcademicSessionDetailResponse.Artifact();
        artifact.setArtifactId(artifactId);
        artifact.setArtifactType(extension(fileName).toUpperCase(Locale.ROOT));
        artifact.setTitle(title(fileName));
        artifact.setFileName(fileName);
        artifact.setDownloadUrl("/api/v1/academic/artifacts/download?sessionId="
                + encodeQuery(sessionId) + "&artifactId=" + encodeQuery(artifactId));
        artifact.setFileSize(size(root().resolve(relativePath)));
        return artifact;
    }

    private void saveManifest(String userId,
                              String sessionId,
                              List<AcademicSessionDetailResponse.Artifact> artifacts) {
        try {
            Path manifest = manifestPath(userId, sessionId);
            Files.createDirectories(manifest.getParent());
            List<ArtifactManifestItem> items = artifacts.stream()
                    .map(artifact -> new ArtifactManifestItem(artifact.getArtifactId(), Instant.now().toString()))
                    .toList();
            Files.writeString(manifest, objectMapper.writeValueAsString(items), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private Path manifestPath(String userId, String sessionId) {
        return root().resolve(".agent-artifacts").resolve(encode(userId + ":" + sessionId) + ".json");
    }

    private Path resolveArtifactPath(String artifactId) {
        String relative = decode(artifactId);
        Path root = root();
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) {
            throw new AppException("ARTIFACT_0003", "生成文件路径不合法");
        }
        return file;
    }

    private Path root() {
        String configured = StringUtils.hasText(outputDirectory) ? outputDirectory.trim() : "outputs";
        Path path = Path.of(configured);
        if (!path.isAbsolute()) {
            path = projectRoot().resolve(configured).normalize();
        }
        try {
            Files.createDirectories(path);
        } catch (IOException ignored) {
        }
        return path.toAbsolutePath().normalize();
    }

    private Path projectRoot() {
        if (StringUtils.hasText(skillsDirectory)) {
            Path skillsPath = Path.of(skillsDirectory.trim());
            if (!skillsPath.isAbsolute()) {
                skillsPath = Path.of("").toAbsolutePath().normalize().resolve(skillsPath).normalize();
            }
            if (Files.isDirectory(skillsPath) && skillsPath.getParent() != null) {
                return skillsPath.getParent().toAbsolutePath().normalize();
            }
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        if ("agent-group-app".equalsIgnoreCase(fileName(cwd))
                && cwd.getParent() != null
                && cwd.getParent().getParent() != null) {
            return cwd.getParent().getParent();
        }
        if ("backend".equalsIgnoreCase(fileName(cwd)) && cwd.getParent() != null) {
            return cwd.getParent();
        }
        return cwd;
    }

    private boolean isDownloadable(Path path) {
        return DOWNLOADABLE_EXTENSIONS.contains(extension(path));
    }

    private boolean modifiedAfter(Path path, long cutoff) {
        return lastModified(path) >= cutoff;
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private String title(String fileName) {
        String ext = extension(fileName).toUpperCase(Locale.ROOT);
        return switch (ext) {
            case "PDF" -> "PDF 笔记";
            case "TEX" -> "LaTeX 源文件";
            case "SRT", "VTT" -> "字幕文件";
            case "TXT", "MD" -> "文本资料";
            case "PPTX" -> "PPT 文件";
            case "DOCX" -> "Word 文件";
            case "XLSX" -> "Excel 文件";
            case "ZIP" -> "打包文件";
            default -> fileName;
        };
    }

    private int extensionRank(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "pdf" -> 0;
            case "tex" -> 1;
            case "srt", "vtt" -> 2;
            case "txt", "md" -> 3;
            case "pptx", "docx", "xlsx", "svg" -> 4;
            case "zip" -> 5;
            default -> 9;
        };
    }

    private String extension(Path path) {
        return extension(path.getFileName().toString());
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 && index + 1 < fileName.length()
                ? fileName.substring(index + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private String fileName(Path path) {
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new AppException("ARTIFACT_0003", "生成文件路径不合法");
        }
    }

    private String encodeQuery(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ArtifactManifestItem(String artifactId, String createdAt) {
    }

    public record DownloadArtifact(Path path, String fileName, String contentType, long size) {
    }
}
