package com.linrun.infrastructure.agent.gateway;

import com.linrun.domain.agent.file.model.RagHit;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

final class RagKeywordRetriever {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final int TOP_K = 3;

    private RagKeywordRetriever() {
    }

    static List<RagHit> retrieve(String extractedText, String question) {
        if (!StringUtils.hasText(extractedText) || !StringUtils.hasText(question)) {
            return List.of();
        }
        List<String> terms = extractTerms(question);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> scored = new ArrayList<>();
        List<String> chunks = splitChunks(extractedText);
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String lower = chunk.toLowerCase(Locale.ROOT);
            int score = 0;
            for (String term : terms) {
                if (lower.contains(term)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new ScoredChunk(i, chunk, score));
            }
        }
        scored.sort(Comparator.comparingInt(ScoredChunk::score).reversed());
        List<RagHit> hits = new ArrayList<>();
        int rank = 1;
        for (ScoredChunk chunk : scored.stream().limit(TOP_K).toList()) {
            hits.add(new RagHit(
                    rank++,
                    "keyword-" + chunk.index(),
                    chunk.text(),
                    new LinkedHashMap<>(java.util.Map.of("source", "keyword", "score", chunk.score()))));
        }
        return hits;
    }

    private static List<String> extractTerms(String question) {
        return Arrays.stream(question.toLowerCase(Locale.ROOT).split("[\\s,，。！？；;、]+"))
                .map(String::trim)
                .filter(term -> term.length() >= 2)
                .distinct()
                .limit(8)
                .toList();
    }

    private static List<String> splitChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_SIZE);
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private record ScoredChunk(int index, String text, int score) {
    }
}
