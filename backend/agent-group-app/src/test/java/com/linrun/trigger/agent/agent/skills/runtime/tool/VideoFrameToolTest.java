package com.linrun.trigger.agent.agent.skills.runtime.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoFrameToolTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPreferProjectRuntimeFfmpegOnWindows() throws Exception {
        Path projectRoot = Files.createDirectories(tempDir.resolve("project"));
        Path runtimeBin = Files.createDirectories(projectRoot.resolve("tools").resolve("runtime-bin"));
        Path ffmpeg = runtimeBin.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "ffmpeg.cmd"
                : "ffmpeg");
        Files.writeString(ffmpeg, "runtime ffmpeg");

        VideoFrameTool tool = new VideoFrameTool(projectRoot.toString(), tempDir.resolve("session").toString());

        assertEquals(ffmpeg.toAbsolutePath().normalize().toString(), tool.resolveFfmpegCommand());
    }
}
