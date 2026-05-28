package com.linrun.domain.agent.conversation.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GuideContextCompactor {

    public static final String CONTEXT_COMPACT_MARK = "[older conversation compacted]";

    public String compact(List<String> lines, int maxContextChars) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        int safeMax = Math.max(600, maxContextChars);
        String context = String.join("\n", lines);
        if (context.length() <= safeMax) {
            return context;
        }

        List<String> recentLines = keepRecentLines(lines, safeMax);
        String microSummary = microSummary(lines.subList(0, Math.max(0, lines.size() - recentLines.size())));
        String prefix = StringUtils.hasText(microSummary)
                ? CONTEXT_COMPACT_MARK + "\n摘要：" + microSummary
                : CONTEXT_COMPACT_MARK;
        return trimToLimit(prefix + "\n" + String.join("\n", recentLines), safeMax);
    }

    private List<String> keepRecentLines(List<String> lines, int maxContextChars) {
        List<String> keptLines = new ArrayList<>();
        int currentLength = CONTEXT_COMPACT_MARK.length() + 8;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            int nextLength = currentLength + line.length() + 1;
            if (nextLength > maxContextChars) {
                break;
            }
            keptLines.add(0, line);
            currentLength = nextLength;
        }
        if (keptLines.isEmpty()) {
            String lastLine = lines.get(lines.size() - 1);
            keptLines.add(lastLine.substring(Math.max(0, lastLine.length() - maxContextChars / 2)));
        }
        return keptLines;
    }

    private String microSummary(List<String> oldLines) {
        if (oldLines == null || oldLines.isEmpty()) {
            return "";
        }
        List<String> digests = oldLines.stream()
                .map(this::digest)
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
        return String.join("；", digests);
    }

    private String digest(String line) {
        if (!StringUtils.hasText(line)) {
            return "";
        }
        String normalized = line.replace('\n', ' ').trim();
        int limit = Math.min(80, normalized.length());
        return normalized.substring(0, limit);
    }

    private String trimToLimit(String content, int maxContextChars) {
        if (content.length() <= maxContextChars) {
            return content;
        }
        int keepStart = Math.min(content.length(), CONTEXT_COMPACT_MARK.length() + 120);
        int keepEnd = Math.max(keepStart, content.length() - (maxContextChars - keepStart - 1));
        return content.substring(0, keepStart) + "\n" + content.substring(keepEnd);
    }
}
