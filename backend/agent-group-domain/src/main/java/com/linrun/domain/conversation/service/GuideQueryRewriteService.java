package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.model.GuideReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GuideQueryRewriteService {

    private static final int MAX_QUERY_LENGTH = 220;

    public List<String> rewrite(String question) {
        String normalized = compact(question);
        String currentTurn = currentTurn(question);
        List<String> queries = new ArrayList<>();
        addQuery(queries, currentTurn);
        addQuery(queries, normalized);
        if (containsAny(currentTurn, "\u62fc\u56e2", "\u6210\u56e2", "\u56e2\u8d2d", "group")) {
            addQuery(queries, currentTurn + " \u62fc\u56e2 \u6210\u56e2 \u652f\u4ed8 \u9000\u6b3e \u4ea4\u6613\u89c4\u5219");
        }
        if (containsAny(currentTurn, "\u9000\u6b3e", "\u9000\u8d27", "\u552e\u540e", "\u672a\u6210\u56e2", "refund")) {
            addQuery(queries, currentTurn + " \u9000\u6b3e \u9000\u8d27 \u552e\u540e\u653f\u7b56 \u672a\u6210\u56e2");
        }
        if (containsAny(currentTurn, "\u8ba2\u5355", "\u652f\u4ed8", "\u5e93\u5b58", "\u9501\u5355", "order", "payment")) {
            addQuery(queries, currentTurn + " \u8ba2\u5355\u72b6\u6001 \u652f\u4ed8\u72b6\u6001 \u9501\u5355 \u5e93\u5b58");
        }
        return queries.stream().limit(4).toList();
    }

    public List<GuideReference> mergeAndRank(String question, List<GuideReference> references, int limit) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        String normalized = compact(question).toLowerCase(Locale.ROOT);
        Map<String, GuideReference> dedup = new LinkedHashMap<>();
        for (GuideReference reference : references) {
            if (reference == null) {
                continue;
            }
            dedup.putIfAbsent(referenceKey(reference), reference);
        }
        return dedup.values().stream()
                .sorted(Comparator.comparingInt(reference -> -score(normalized, reference)))
                .limit(Math.max(1, limit))
                .toList();
    }

    private int score(String question, GuideReference reference) {
        int score = 100 - nullToLargeRank(reference.getRank());
        String content = nullToBlank(reference.getContent()).toLowerCase(Locale.ROOT);
        String documentType = nullToBlank(reference.getDocumentType()).toLowerCase(Locale.ROOT);
        for (String token : question.split("\\s+")) {
            if (StringUtils.hasText(token) && content.contains(token)) {
                score += 4;
            }
        }
        if (containsAny(question, "\u9000\u6b3e", "\u9000\u8d27", "\u552e\u540e", "refund")
                && containsAny(documentType + content, "\u552e\u540e", "\u9000\u6b3e", "\u9000\u8d27", "refund")) {
            score += 30;
        }
        if (containsAny(question, "\u62fc\u56e2", "\u6210\u56e2", "group")
                && containsAny(documentType + content, "\u62fc\u56e2", "\u6210\u56e2", "\u8425\u9500", "\u4ea4\u6613", "group")) {
            score += 24;
        }
        if (containsAny(question, "\u652f\u4ed8", "\u8ba2\u5355", "\u9501\u5355", "payment", "order")
                && containsAny(documentType + content, "\u652f\u4ed8", "\u8ba2\u5355", "\u9501\u5355", "\u4ea4\u6613", "payment", "order")) {
            score += 18;
        }
        return score;
    }

    private String currentTurn(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        String[] lines = question.split("\\R+");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = compact(lines[i]);
            if (StringUtils.hasText(line)) {
                return line;
            }
        }
        return question;
    }

    private String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= MAX_QUERY_LENGTH) {
            return compacted;
        }
        return compacted.substring(compacted.length() - MAX_QUERY_LENGTH);
    }

    private void addQuery(List<String> queries, String query) {
        String compacted = compact(query);
        if (StringUtils.hasText(compacted) && !queries.contains(compacted)) {
            queries.add(compacted);
        }
    }

    private String referenceKey(GuideReference reference) {
        if (StringUtils.hasText(reference.getFragmentId())) {
            return "fragment:" + reference.getFragmentId();
        }
        return "content:" + nullToBlank(reference.getContent());
    }

    private int nullToLargeRank(Integer rank) {
        return rank == null ? 1000 : rank;
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        String normalized = source.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
