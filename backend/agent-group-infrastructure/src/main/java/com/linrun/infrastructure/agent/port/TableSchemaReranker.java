package com.linrun.infrastructure.agent.port;

import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort.AcademicTableSchemaMatch;

import java.util.ArrayList;
import java.util.List;

/**
 * table_rag schema 精排：按 rerank 相关性分对召回的多张表重排、更新分数、按 topN 截断。
 * 纯逻辑，便于单测；与 DashScope 调用解耦。
 */
final class TableSchemaReranker {

    private TableSchemaReranker() {
    }

    /**
     * @param matches 外部 table_rag 召回的表（顺序为召回顺序）
     * @param scores  rerank 给出的相关性分，与 matches 一一对应
     * @param topN    精排后保留的表数
     * @return 按 rerank 分降序、score 更新为 rerank 分的表列表；scores 不匹配时原样返回（降级）
     */
    static List<AcademicTableSchemaMatch> rerank(List<AcademicTableSchemaMatch> matches,
                                                 List<Double> scores, int topN) {
        if (matches == null || matches.isEmpty()) {
            return matches == null ? List.of() : matches;
        }
        if (scores == null || scores.size() != matches.size()) {
            return matches;
        }
        List<Integer> order = new ArrayList<>(matches.size());
        for (int i = 0; i < matches.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
        int limit = Math.min(Math.max(topN, 1), matches.size());
        List<AcademicTableSchemaMatch> reranked = new ArrayList<>(limit);
        for (int k = 0; k < limit; k++) {
            int i = order.get(k);
            AcademicTableSchemaMatch m = matches.get(i);
            reranked.add(new AcademicTableSchemaMatch(m.modelCode(), scores.get(i), m.schemaList()));
        }
        return reranked;
    }
}
