package com.linrun.infrastructure.knowledgeasset.vector;

import com.linrun.domain.knowledgeasset.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragmentStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalKnowledgeVectorRepository.class);

    private final Map<String, VectorRecord> vectorRecords = new ConcurrentHashMap<>();
    @Value("${agent.group.vector.host:}")
    private String host;
    @Value("${agent.group.vector.port:15432}")
    private int port = 15432;
    @Value("${agent.group.vector.database:}")
    private String database;
    @Value("${agent.group.vector.username:}")
    private String username;
    @Value("${agent.group.vector.password:}")
    private String password;
    @Value("${agent.group.vector.local-fallback-enabled:true}")
    private boolean localFallbackEnabled = true;
    @Resource
    private KnowledgeVectorMetrics metrics = KnowledgeVectorMetrics.noop();
    private String jdbcUrl = "";

    public LocalKnowledgeVectorRepository() {
        this("", "", "", true, KnowledgeVectorMetrics.noop());
    }

    public LocalKnowledgeVectorRepository(@Value("${agent.group.vector.host:}") String host,
                                          @Value("${agent.group.vector.port:15432}") int port,
                                          @Value("${agent.group.vector.database:}") String database,
                                          @Value("${agent.group.vector.username:}") String username,
                                          @Value("${agent.group.vector.password:}") String password,
                                          @Value("${agent.group.vector.local-fallback-enabled:true}") boolean localFallbackEnabled,
                                          KnowledgeVectorMetrics metrics) {
        this(StringUtils.hasText(host) && StringUtils.hasText(database)
                        ? "jdbc:postgresql://" + host + ":" + port + "/" + database
                        : "",
                username,
                password,
                localFallbackEnabled,
                metrics);
    }

    LocalKnowledgeVectorRepository(String jdbcUrl, String username, String password) {
        this(jdbcUrl, username, password, true, KnowledgeVectorMetrics.noop());
    }

    LocalKnowledgeVectorRepository(String jdbcUrl, String username, String password, boolean localFallbackEnabled) {
        this(jdbcUrl, username, password, localFallbackEnabled, KnowledgeVectorMetrics.noop());
    }

    LocalKnowledgeVectorRepository(String jdbcUrl,
                                   String username,
                                   String password,
                                   boolean localFallbackEnabled,
                                   KnowledgeVectorMetrics metrics) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.localFallbackEnabled = localFallbackEnabled;
        this.metrics = metrics == null ? KnowledgeVectorMetrics.noop() : metrics;
    }

    @PostConstruct
    private void initJdbcUrl() {
        if (!StringUtils.hasText(jdbcUrl) && StringUtils.hasText(host) && StringUtils.hasText(database)) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
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
        if (!localFallbackEnabled) {
            return List.of();
        }
        metrics.recordLocalFallback(StringUtils.hasText(jdbcUrl) ? "pgvector_empty_or_failed" : "pgvector_not_configured");
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
        long startNanos = System.nanoTime();
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
            metrics.recordPgvectorSave(true, elapsedMillis(startNanos));
        } catch (Exception e) {
            metrics.recordPgvectorSave(false, elapsedMillis(startNanos));
            metrics.recordLocalFallback("pgvector_save_failed");
            LOGGER.warn("pgvector save failed, fragmentId={}, reason={}",
                    fragment.getFragmentId(), e.getClass().getSimpleName());
        }
    }

    private List<KnowledgeFragment> searchPgvector(List<Double> queryEmbedding, int limit) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return List.of();
        }
        long startNanos = System.nanoTime();
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
                metrics.recordPgvectorSearch(true, elapsedMillis(startNanos));
                return fragments;
            }
        } catch (Exception e) {
            metrics.recordPgvectorSearch(false, elapsedMillis(startNanos));
            LOGGER.warn("pgvector search failed, reason={}", e.getClass().getSimpleName());
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

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private record VectorRecord(KnowledgeFragment fragment, List<Double> embedding) {
    }

    private record ScoredFragment(KnowledgeFragment fragment, double score) {
    }
}
