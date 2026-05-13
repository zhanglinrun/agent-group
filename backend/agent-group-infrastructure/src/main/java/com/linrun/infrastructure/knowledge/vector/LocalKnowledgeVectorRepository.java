package com.linrun.infrastructure.knowledge.vector;

import com.linrun.domain.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.domain.knowledge.model.KnowledgeFragmentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class LocalKnowledgeVectorRepository implements KnowledgeVectorRepository {

    private final Map<String, VectorRecord> vectorRecords = new ConcurrentHashMap<>();
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public LocalKnowledgeVectorRepository() {
        this("", "", "");
    }

    public LocalKnowledgeVectorRepository(@Value("${agent.group.vector.host:}") String host,
                                          @Value("${agent.group.vector.port:15432}") int port,
                                          @Value("${agent.group.vector.database:}") String database,
                                          @Value("${agent.group.vector.username:}") String username,
                                          @Value("${agent.group.vector.password:}") String password) {
        this(StringUtils.hasText(host) && StringUtils.hasText(database)
                        ? "jdbc:postgresql://" + host + ":" + port + "/" + database
                        : "",
                username,
                password);
    }

    LocalKnowledgeVectorRepository(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding) {
        if (fragment == null || fragment.getFragmentId() == null || embedding == null || embedding.isEmpty()) {
            return;
        }
        vectorRecords.put(fragment.getFragmentId(), new VectorRecord(fragment, new ArrayList<>(embedding)));
        savePgvector(fragment, embedding);
    }

    @Override
    public List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit) {
        if (queryEmbedding == null || queryEmbedding.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<KnowledgeFragment> pgvectorResult = searchPgvector(queryEmbedding, limit);
        if (!pgvectorResult.isEmpty()) {
            return pgvectorResult;
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

    private void savePgvector(KnowledgeFragment fragment, List<Double> embedding) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return;
        }
        String sql = """
                insert into knowledge_embedding (
                  fragment_id, document_id, goods_id, knowledge_version, content, embedding
                ) values (?, ?, ?, ?, ?, ?::vector)
                on conflict (fragment_id) do update set
                  document_id = excluded.document_id,
                  goods_id = excluded.goods_id,
                  knowledge_version = excluded.knowledge_version,
                  content = excluded.content,
                  embedding = excluded.embedding,
                  create_time = now()
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fragment.getFragmentId());
            statement.setString(2, fragment.getDocumentId());
            statement.setString(3, fragment.getGoodsId());
            statement.setString(4, fragment.getKnowledgeVersion());
            statement.setString(5, fragment.getContent());
            statement.setString(6, vectorLiteral(embedding));
            statement.executeUpdate();
        } catch (Exception ignored) {
            // pgvector 不可用时保留本地向量检索能力。
        }
    }

    private List<KnowledgeFragment> searchPgvector(List<Double> queryEmbedding, int limit) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return List.of();
        }
        String sql = """
                select fragment_id, document_id, goods_id, knowledge_version, content
                from knowledge_embedding
                order by embedding <=> ?::vector
                limit ?
                """;
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vectorLiteral(queryEmbedding));
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<KnowledgeFragment> fragments = new ArrayList<>();
                while (resultSet.next()) {
                    fragments.add(fragment(resultSet));
                }
                return fragments;
            }
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private KnowledgeFragment fragment(ResultSet resultSet) throws java.sql.SQLException {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(resultSet.getString("fragment_id"));
        fragment.setDocumentId(resultSet.getString("document_id"));
        fragment.setGoodsId(resultSet.getString("goods_id"));
        fragment.setKnowledgeVersion(resultSet.getString("knowledge_version"));
        fragment.setContent(resultSet.getString("content"));
        fragment.setDocumentType("向量检索");
        fragment.setRankNo(0);
        fragment.setFragmentStatus(KnowledgeFragmentStatus.ENABLED);
        fragment.setEnabled(true);
        return fragment;
    }

    private String vectorLiteral(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(value -> Double.toString(value == null ? 0.0d : value))
                .collect(Collectors.joining(",")) + "]";
    }

    private record VectorRecord(KnowledgeFragment fragment, List<Double> embedding) {
    }

    private record ScoredFragment(KnowledgeFragment fragment, double score) {
    }
}
