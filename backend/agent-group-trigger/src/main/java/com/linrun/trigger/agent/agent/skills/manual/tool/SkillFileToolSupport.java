package com.linrun.trigger.agent.agent.skills.manual.tool;

import com.linrun.trigger.agent.agent.skills.manual.model.SkillLoadingException;
import com.linrun.trigger.agent.agent.skills.manual.model.SkillMetadata;
import com.linrun.trigger.agent.agent.skills.manual.registry.SkillRegistry;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

abstract class SkillFileToolSupport {

    protected static final int DEFAULT_READ_LINE_COUNT = 80;
    protected static final int MAX_READ_LINE_COUNT = 300;
    protected static final int MAX_READ_CHARS = 24_000;
    protected static final int DEFAULT_MAX_GREP_MATCHES = 80;
    protected static final int MAX_GREP_MATCHES = 200;
    protected static final int DEFAULT_MAX_GLOB_RESULTS = 120;
    protected static final int MAX_GLOB_RESULTS = 300;
    protected static final int DEFAULT_LIST_DEPTH = 2;
    protected static final int MAX_LIST_DEPTH = 8;
    protected static final int DEFAULT_MAX_LIST_ENTRIES = 160;
    protected static final int MAX_LIST_ENTRIES = 400;
    protected static final long MAX_SEARCH_FILE_BYTES = 2L * 1024L * 1024L;
    protected static final int MAX_MATCH_LINE_CHARS = 1_000;

    protected final SkillRegistry skillRegistry;

    protected SkillFileToolSupport(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    protected ResolvedSkillPath resolveSkillPath(String skillName, String path) {
        if (skillName == null || skillName.isBlank()) {
            throw new SkillLoadingException("skill is required");
        }
        SkillMetadata metadata = skillRegistry.get(skillName.trim());
        if (metadata == null) {
            throw SkillLoadingException.notFound(skillName.trim());
        }
        if (metadata.skillPath() == null) {
            throw new SkillLoadingException(metadata.name(), "skill path is missing");
        }

        Path declaredRoot = metadata.skillPath().toAbsolutePath().normalize();
        Path candidateInput = normalizeInputPath(path);
        Path candidate = candidateInput.isAbsolute()
                ? candidateInput.toAbsolutePath().normalize()
                : declaredRoot.resolve(candidateInput).normalize();

        try {
            Path realRoot = declaredRoot.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot)) {
                throw new SkillLoadingException(metadata.name(),
                        "path escapes skill directory: " + displayPath(candidate));
            }
            return new ResolvedSkillPath(metadata.name(), realRoot, realCandidate);
        } catch (NoSuchFileException e) {
            throw new SkillLoadingException(metadata.name(), "path not found: " + displayPath(candidate), e);
        } catch (IOException e) {
            throw new SkillLoadingException(metadata.name(), "path is not readable: " + displayPath(candidate), e);
        }
    }

    protected String readText(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            try {
                return Files.readString(path, Charset.forName("GBK"));
            } catch (Exception ignored) {
                return Files.readString(path, StandardCharsets.ISO_8859_1);
            }
        }
    }

    protected List<String> readLines(Path path) throws IOException {
        String content = readText(path);
        if (content.isEmpty()) {
            return List.of();
        }
        String[] rawLines = content.split("\\R", -1);
        List<String> lines = new ArrayList<>(List.of(rawLines));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    protected String relativePath(ResolvedSkillPath resolvedPath) {
        return relativePath(resolvedPath.skillRoot(), resolvedPath.path());
    }

    protected String relativePath(Path skillRoot, Path path) {
        Path relative = skillRoot.relativize(path);
        String value = relative.toString().replace("\\", "/");
        return value.isBlank() ? "." : value;
    }

    protected String displayPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    protected int bounded(Integer value, int defaultValue, int minValue, int maxValue) {
        int resolved = value == null ? defaultValue : value;
        return Math.max(minValue, Math.min(maxValue, resolved));
    }

    protected String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n[truncated, max chars=" + maxChars + "]";
    }

    protected String truncateLine(String value) {
        if (value == null || value.length() <= MAX_MATCH_LINE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_MATCH_LINE_CHARS) + "...";
    }

    protected boolean isInsideSkillRoot(Path skillRoot, Path path) {
        try {
            return path.toRealPath().startsWith(skillRoot);
        } catch (IOException e) {
            return false;
        }
    }

    protected boolean isRegularFileInsideSkill(Path skillRoot, Path path) {
        return Files.isRegularFile(path) && isInsideSkillRoot(skillRoot, path);
    }

    private Path normalizeInputPath(String path) {
        if (path == null || path.isBlank()) {
            return Path.of(".");
        }
        return Path.of(path.trim());
    }

    protected record ResolvedSkillPath(String skillName, Path skillRoot, Path path) {
    }
}















