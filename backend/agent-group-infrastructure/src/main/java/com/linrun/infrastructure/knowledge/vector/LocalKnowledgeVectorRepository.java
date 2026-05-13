package com.linrun.infrastructure.knowledge.vector;

import com.linrun.domain.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class LocalKnowledgeVectorRepository implements KnowledgeVectorRepository {

    private final Map<String, VectorRecord> vectorRecords = new ConcurrentHashMap<>();

    @Override
    public void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding) {
        if (fragment == null || fragment.getFragmentId() == null || embedding == null || embedding.isEmpty()) {
            return;
        }
        vectorRecords.put(fragment.getFragmentId(), new VectorRecord(fragment, new ArrayList<>(embedding)));
    }

    @Override
    public List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit) {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || limit <= 0) {
            return List.of();
        }
        return vectorRecords.values().stream()
                .map(record -> new ScoredFragment(record.fragment(), cosine(queryEmbedding, record.embedding())))
                .filter(item -> item.score() > 0.0d)
                .sorted(Comparator.comparingDouble(ScoredFragment::score).reversed())
                .limit(limit)
                .map(ScoredFragment::fragment)
                .toList();
    }

    private double cosine(List<Double> left, List<Double> right) {
        int size = Math.min(left.size(), right.size());
        double score = 0.0d;
        for (int i = 0; i < size; i++) {
            score += left.get(i) * right.get(i);
        }
        return score;
    }

    private record VectorRecord(KnowledgeFragment fragment, List<Double> embedding) {
    }

    private record ScoredFragment(KnowledgeFragment fragment, double score) {
    }
}
