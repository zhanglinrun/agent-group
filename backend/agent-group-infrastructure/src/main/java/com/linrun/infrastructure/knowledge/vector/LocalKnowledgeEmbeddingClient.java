package com.linrun.infrastructure.knowledge.vector;

import com.linrun.domain.knowledge.adapter.KnowledgeEmbeddingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class LocalKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

    private final int dimension;

    public LocalKnowledgeEmbeddingClient(@Value("${agent.group.vector.dimension:1024}") int dimension) {
        this.dimension = Math.max(dimension, 16);
    }

    @Override
    public List<Double> embed(String content) {
        double[] vector = new double[dimension];
        if (StringUtils.hasText(content)) {
            content.trim().toLowerCase().codePoints()
                    .filter(codePoint -> !Character.isWhitespace(codePoint))
                    .forEach(codePoint -> vector[Math.floorMod(codePoint, dimension)] += 1.0d);
        }
        normalize(vector);
        List<Double> result = new ArrayList<>(dimension);
        for (double value : vector) {
            result.add(value);
        }
        return result;
    }

    private void normalize(double[] vector) {
        double sum = 0.0d;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum <= 0.0d) {
            return;
        }
        double length = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / length;
        }
    }
}
