package com.linrun.trigger.agent.agent.skills.runtime.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexCompileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldNormalizePortableLatexIssues() throws Exception {
        Path tex = tempDir.resolve("main.tex");
        Files.writeString(tex, """
                \\documentclass{article}
                \\usepackage{listings}
                \\setmainfont{Noto Serif CJK SC}
                \\lstset{breaklines=true}
                \\begin{document}
                \\begin{lstlisting}[language=nginx]
                location / {
                    try_files $uri $uri/ =404;
                }
                \\end{lstlisting}
                \\end{document}
                """);

        LatexCompileTool tool = new LatexCompileTool(tempDir.toString());

        Map<String, Object> fixes = Map.of("fixes", tool.normalizeTexForPortableCompile(tex));
        String updated = Files.readString(tex);

        assertFalse(updated.contains("Noto Serif CJK SC"));
        assertTrue(updated.contains("\\lstdefinelanguage{nginx}"));
        assertTrue(updated.contains("\\lstset{breaklines=true}"));
        assertEquals(2, ((java.util.List<?>) fixes.get("fixes")).size());
    }

    @Test
    void shouldCleanupMediaIntermediatesOnly() throws Exception {
        Path videoDir = Files.createDirectories(tempDir.resolve("bilibili_BV123"));
        Files.writeString(videoDir.resolve("source.mp4"), "video");
        Files.writeString(videoDir.resolve("audio.wav"), "audio");
        Files.writeString(videoDir.resolve("video.m4s"), "dash-video");
        Files.writeString(videoDir.resolve("audio.m4s"), "dash-audio");
        Files.writeString(videoDir.resolve("cover.jpg"), "cover");
        Files.writeString(videoDir.resolve("audio_whisper_base.srt"), "subtitle");
        Files.writeString(tempDir.resolve("notes.tex"), "tex");
        Files.writeString(tempDir.resolve("notes.pdf"), "pdf");

        LatexCompileTool tool = new LatexCompileTool(tempDir.toString());

        Map<String, Object> cleanup = tool.cleanupMediaIntermediates();

        assertEquals(4, cleanup.get("deletedCount"));
        assertTrue((Long) cleanup.get("deletedBytes") > 0);
        assertFalse(Files.exists(videoDir.resolve("source.mp4")));
        assertFalse(Files.exists(videoDir.resolve("audio.wav")));
        assertFalse(Files.exists(videoDir.resolve("video.m4s")));
        assertFalse(Files.exists(videoDir.resolve("audio.m4s")));
        assertTrue(Files.exists(videoDir.resolve("cover.jpg")));
        assertTrue(Files.exists(videoDir.resolve("audio_whisper_base.srt")));
        assertTrue(Files.exists(tempDir.resolve("notes.tex")));
        assertTrue(Files.exists(tempDir.resolve("notes.pdf")));
    }
}















