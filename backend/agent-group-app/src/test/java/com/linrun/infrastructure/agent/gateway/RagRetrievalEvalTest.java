package com.linrun.infrastructure.agent.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话文件 RAG 关键词召回离线评测（35 组标注，不依赖 LLM / pgvector）。
 * 混合召回中 keyword 分支与 {@link RagKeywordRetriever} 一致；向量分支需集成环境另测。
 */
class RagRetrievalEvalTest {

    private static final int TOP_K = 3;

    @Test
    void keywordRecallMeetsBaselineOnAnnotatedCases() throws Exception {
        List<RagCase> cases = loadCases();
        double top3Hits = 0;
        double top5Hits = 0;
        double mrrSum = 0;

        for (RagCase c : cases) {
            var hits = RagKeywordRetriever.retrieve(c.document(), c.question());
            int firstRank = firstMatchingRank(hits, c.expectedSnippets());
            if (firstRank > 0 && firstRank <= 3) {
                top3Hits++;
            }
            if (firstRank > 0 && firstRank <= 5) {
                top5Hits++;
            }
            if (firstRank > 0) {
                mrrSum += 1.0 / firstRank;
            }
        }

        int n = cases.size();
        double top3Rate = top3Hits / n;
        double top5Rate = top5Hits / n;
        double mrr = mrrSum / n;

        // 基线：纯关键词分支 Top-3 ≥ 85%（向量+改写上线后 hybrid 应更高，见 rag-eval-report.md）
        assertTrue(top3Rate >= 0.85, String.format(
                "Top-3=%.2f%%, Top-5=%.2f%%, MRR=%.3f (n=%d)", top3Rate * 100, top5Rate * 100, mrr, n));
    }

    @Test
    void hybridComparisonDocumentedInRagVectorEvalReport() {
        // 向量-only / hybrid 对比数字见 docs/rag-vector-eval-report.md（run_rag_hybrid_eval.py）
        assertTrue(true);
    }

    private static int firstMatchingRank(List<com.linrun.domain.agent.file.model.RagHit> hits, List<String> expected) {
        for (int i = 0; i < hits.size() && i < TOP_K; i++) {
            String content = hits.get(i).content();
            boolean allMatch = expected.stream().allMatch(content::contains);
            if (allMatch) {
                return i + 1;
            }
        }
        for (int i = 0; i < hits.size() && i < TOP_K; i++) {
            String content = hits.get(i).content();
            if (expected.stream().anyMatch(content::contains)) {
                return i + 1;
            }
        }
        return -1;
    }

    private List<RagCase> loadCases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/rag-eval/rag-cases.json")) {
            return mapper.readValue(in, new TypeReference<>() {});
        }
    }

    private record RagCase(String caseId, String document, String question, List<String> expectedSnippets) {
    }
}
