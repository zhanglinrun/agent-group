package com.linrun.trigger.agent.agent.skills.runtime.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class RestrictedToolSupport {

    private RestrictedToolSupport() {
    }

    static Path normalizeRoot(String rootDirectory) {
        return Path.of(rootDirectory).toAbsolutePath().normalize();
    }

    static Path resolveInsideRoot(Path root, String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        Path input = Path.of(inputPath.trim());
        Path resolved = input.isAbsolute()
                ? input.toAbsolutePath().normalize()
                : root.resolve(input).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path is outside current session output directory");
        }
        return resolved;
    }

    static CommandResult runCommand(List<String> command,
                                    Path workingDirectory,
                                    Duration timeout,
                                    int maxOutputChars) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> outputFuture = executor.submit(() -> readLimited(process.getInputStream(), maxOutputChars));
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(-1, "process timeout after " + timeout.toSeconds() + " seconds");
            }
            String output = "";
            try {
                output = outputFuture.get(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                outputFuture.cancel(true);
            }
            return new CommandResult(process.exitValue(), output);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String readLimited(InputStream inputStream, int maxChars) throws IOException {
        StringBuilder builder = new StringBuilder();
        byte[] buffer = new byte[4096];
        boolean truncated = false;
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            String text = new String(buffer, 0, read, StandardCharsets.UTF_8);
            int remaining = maxChars - builder.length();
            if (remaining > 0) {
                builder.append(text, 0, Math.min(remaining, text.length()));
            }
            if (text.length() > remaining) {
                truncated = true;
            }
        }
        if (truncated) {
            builder.append("\n...[output truncated]");
        }
        return builder.toString();
    }

    record CommandResult(int exitCode, String output) {
    }
}
