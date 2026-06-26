package com.linrun.infrastructure.agent.gateway;

import com.linrun.domain.agent.file.adapter.EmbeddingPort;
import com.linrun.domain.agent.file.model.EmbeddingChunk;
import com.linrun.domain.agent.file.model.RagHit;
import com.linrun.domain.agent.file.model.RagRetrievalResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 会话文件向量化与 RAG 检索实现，基于 Spring AI。
 */
@Component
@Slf4j
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

    private static final int EMBEDDING_BATCH_SIZE = 9;
    private static final String VECTOR_TABLE = "vector_file_info";

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final DynamicPgVectorStoreFactory pgVectorStoreFactory;

    private PgVectorStore vectorStore;

    public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel,
                                    ChatModel chatModel,
                                    DynamicPgVectorStoreFactory pgVectorStoreFactory) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.pgVectorStoreFactory = pgVectorStoreFactory;
    }

    @PostConstruct
    public void init() {
        try {
            vectorStore = pgVectorStoreFactory.createPgVectorStore(VECTOR_TABLE);
        } catch (Exception e) {
            log.warn("PgVector 初始化失败，文件问答将退回全文读取 {}", e.getMessage());
            vectorStore = null;
        }
    }

    @Override
    public void embedAndStore(List<EmbeddingChunk> chunks) {
        if (vectorStore == null) {
            log.warn("PgVector 不可用，跳过文件向量化");
            return;
        }
        List<Document> documents = chunks.stream()
                .map(chunk -> {
                    Document doc = new Document(chunk.text());
                    if (chunk.metadata() != null) {
                        doc.getMetadata().putAll(chunk.metadata());
                    }
                    return doc;
                })
                .collect(Collectors.toList());
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
            vectorStore.add(batch);
        }
    }

    @Override
    public RagRetrievalResult ragRetrieve(String fileId, String question) {
        log.info("RAG 检索开始 fileId={}, question={}", fileId, question);

        if (StringUtils.isBlank(fileId) || StringUtils.isBlank(question)) {
            log.warn("RAG 检索参数为空 fileId={}, question={}", fileId, question);
            return RagRetrievalResult.failed("rag", question, "", List.of(), "检索参数不能为空");
        }
        if (vectorStore == null) {
            return RagRetrievalResult.failed("rag", question, "", List.of(), "向量库不可用，请直接读取文件全文");
        }

        try {
            Query query = Query.builder().text(question).build();

            ChatClient chatClient = ChatClient.builder(chatModel).build();
            CompressionQueryTransformer queryTransformer = CompressionQueryTransformer.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .build();
            Query compressed = queryTransformer.transform(query);
            log.info("压缩重写后的Query: {}", compressed.text());

            QueryExpander queryExpander = MultiQueryExpander.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .numberOfQueries(3)
                    .includeOriginal(true)
                    .build();
            List<Query> expandedQueries = queryExpander.expand(compressed);
            log.info("扩展后的Query：{}", expandedQueries);

            List<RagHit> hits = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            Filter.Expression filter = builder.eq("fileid", fileId).build();

            for (Query eq : expandedQueries) {
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(eq.text())
                                .topK(5)
                                .filterExpression(filter)
                                .build());

                for (Document doc : docs) {
                    String docId = StringUtils.defaultString(doc.getId(),
                            "doc-" + Math.abs(Objects.hash(doc.getText(), hits.size())));
                    if (seenIds.add(docId)) {
                        hits.add(new RagHit(
                                hits.size() + 1,
                                docId,
                                doc.getText(),
                                new LinkedHashMap<>(doc.getMetadata())));
                    }
                }
            }

            log.info("RAG 检索完成 fileId={}, 返回结果数={}", fileId, hits.size());
            return new RagRetrievalResult(
                    true,
                    "rag",
                    question,
                    compressed.text(),
                    expandedQueries.stream().map(Query::text).toList(),
                    hits.size(),
                    hits.isEmpty() ? "未检索到与问题相关的内容" : "RAG检索命中 " + hits.size() + " 段",
                    hits);
        } catch (Exception e) {
            log.error("RAG 检索失败 fileId={}, question={}", fileId, question, e);
            return RagRetrievalResult.failed("rag", question, "", List.of(), "RAG 检索失败：" + e.getMessage());
        }
    }
}
