package com.linrun.domain.agent.knowledge.service.splitter;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DocumentSplitterFactory {

    private final List<DocumentSplitStrategy> strategies = List.of(
            new MarkdownHeadingSplitStrategy(),
            new DelimiterSplitStrategy(),
            new ParagraphSplitStrategy(),
            new LineSplitStrategy()
    );

    public List<String> split(String content) {
        String normalized = normalize(content);
        return strategies.stream()
                .filter(strategy -> strategy.supports(normalized))
                .findFirst()
                .map(strategy -> strategy.split(normalized))
                .orElseGet(() -> List.of(normalized));
    }

    private String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static List<String> clean(String[] values) {
        return Arrays.stream(values)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private static class MarkdownHeadingSplitStrategy implements DocumentSplitStrategy {

        private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+");

        @Override
        public boolean supports(String content) {
            return HEADING.matcher(content).find();
        }

        @Override
        public List<String> split(String content) {
            return clean(content.split("(?m)(?=^#{1,6}\\s+)"));
        }
    }

    private static class DelimiterSplitStrategy implements DocumentSplitStrategy {

        @Override
        public boolean supports(String content) {
            return content.contains("\n---\n") || content.contains("\n===\n");
        }

        @Override
        public List<String> split(String content) {
            return clean(content.split("(?m)^[-=]{3,}\\s*$"));
        }
    }

    private static class ParagraphSplitStrategy implements DocumentSplitStrategy {

        @Override
        public boolean supports(String content) {
            return content.split("\\n\\s*\\n").length > 1;
        }

        @Override
        public List<String> split(String content) {
            return clean(content.split("\\n\\s*\\n"));
        }
    }

    private static class LineSplitStrategy implements DocumentSplitStrategy {

        @Override
        public boolean supports(String content) {
            return content.contains("\n");
        }

        @Override
        public List<String> split(String content) {
            return clean(content.split("\\n"));
        }
    }
}
