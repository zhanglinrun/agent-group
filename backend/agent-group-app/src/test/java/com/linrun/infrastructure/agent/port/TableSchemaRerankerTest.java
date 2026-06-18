package com.linrun.infrastructure.agent.port;

import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort.AcademicTableSchemaMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("table_rag schema 精排测试")
class TableSchemaRerankerTest {

    private AcademicTableSchemaMatch match(String modelCode, double score) {
        return new AcademicTableSchemaMatch(modelCode, score, List.of());
    }

    @Test
    @DisplayName("按 rerank 分降序重排并更新 score")
    void shouldRerankByScoresDesc() {
        List<AcademicTableSchemaMatch> matches = List.of(match("t1", 0.9), match("t2", 0.5), match("t3", 0.7));
        // rerank 给出的分数：t2 最高、t1 次之、t3 最低
        List<Double> scores = List.of(0.3, 0.95, 0.1);

        List<AcademicTableSchemaMatch> reranked = TableSchemaReranker.rerank(matches, scores, 5);

        assertThat(reranked).extracting(AcademicTableSchemaMatch::modelCode).containsExactly("t2", "t1", "t3");
        assertThat(reranked.get(0).score()).isEqualTo(0.95);
    }

    @Test
    @DisplayName("按 topN 截断")
    void shouldLimitByTopN() {
        List<AcademicTableSchemaMatch> matches = List.of(match("t1", 0.9), match("t2", 0.5), match("t3", 0.7));
        List<Double> scores = List.of(0.1, 0.2, 0.3);

        List<AcademicTableSchemaMatch> reranked = TableSchemaReranker.rerank(matches, scores, 2);

        assertThat(reranked).hasSize(2);
        assertThat(reranked.get(0).modelCode()).isEqualTo("t3");
    }

    @Test
    @DisplayName("scores 长度不匹配或为空时退回原 matches")
    void shouldFallbackWhenScoresMismatch() {
        List<AcademicTableSchemaMatch> matches = List.of(match("t1", 0.9), match("t2", 0.5));

        assertThat(TableSchemaReranker.rerank(matches, List.of(0.1), 5)).isSameAs(matches);
        assertThat(TableSchemaReranker.rerank(matches, null, 5)).isSameAs(matches);
    }

    @Test
    @DisplayName("空 matches 原样返回")
    void shouldReturnEmptyForEmptyMatches() {
        assertThat(TableSchemaReranker.rerank(List.of(), List.of(), 5)).isEmpty();
    }
}
