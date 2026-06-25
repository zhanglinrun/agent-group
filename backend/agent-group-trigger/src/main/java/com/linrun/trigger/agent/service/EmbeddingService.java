package com.linrun.trigger.agent.service;

import com.linrun.trigger.agent.utils.DynamicPgVectorStoreFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmbeddingService {
    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private DynamicPgVectorStoreFactory pgVectorStoreFactory;

    @Autowired
    private ChatModel chatModel;


    private PgVectorStore vectorStore;

    private static final int EMBEDDING_BATCH_SIZE = 9;

    @PostConstruct
    public void init(){
        try {
            vectorStore = pgVectorStoreFactory.createPgVectorStore("vector_file_info");
        } catch (Exception e) {
            log.warn("PgVector 初始化失败，文件问答将退回全文读取 {}", e.getMessage());
            vectorStore = null;
        }
    }

    /**
     * 向量化。
     */
    public List<float[]> embed(List<Document> documents) {
        return documents.stream().map(document -> embeddingModel.embed(document.getText())).collect(Collectors.toList());
    }

    /**
     * 存储向量。
     */
    public void embedAndStore(List<Document> documents) {
        if (vectorStore == null) {
            log.warn("PgVector 不可用，跳过文件向量化");
            return;
        }
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            List<Document> batches = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
            vectorStore.add(batches);
        }
    }

    /**
     * RAG 检索 - 根据文件ID和问题检索相关文档。
     *
     * @param fileId   文件ID
     * @param question 用户问题
     * @return 相关文档内容列表
     */
    public List<String> ragRetrieve(String fileId, String question) {
        RagRetrievalResult result = ragRetrieveDetailed(fileId, question);
        if (result.hits().isEmpty()) {
            return Collections.singletonList(result.message());
        }
        return result.hits().stream()
                .map(RagHit::content)
                .collect(Collectors.toList());
    }

    public RagRetrievalResult ragRetrieveDetailed(String fileId, String question) {
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

            // 1. 问题压缩重写
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            CompressionQueryTransformer queryTransformer = CompressionQueryTransformer.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .build();

            Query compressed = queryTransformer.transform(query);
            log.info("压缩重写后的Query: {}", compressed.text());

            // 2. 问题扩展
            QueryExpander queryExpander = MultiQueryExpander.builder()
                    .chatClientBuilder(chatClient.mutate())
                    .numberOfQueries(3)
                    .includeOriginal(true)
                    .build();

            List<Query> expandedQueries = queryExpander.expand(compressed);
            log.info("扩展后的Query：{}", expandedQueries);

            // 3. 语义向量检索 - 使用 fileid 过滤。
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

    public record RagRetrievalResult(
            boolean success,
            String mode,
            String originalQuestion,
            String compressedQuestion,
            List<String> expandedQueries,
            int hitCount,
            String message,
            List<RagHit> hits) {

        private static RagRetrievalResult failed(String mode,
                                                 String originalQuestion,
                                                 String compressedQuestion,
                                                 List<String> expandedQueries,
                                                 String message) {
            return new RagRetrievalResult(false, mode, originalQuestion, compressedQuestion,
                    expandedQueries == null ? List.of() : expandedQueries, 0,
                    StringUtils.defaultString(message, "检索失败"), List.of());
        }
    }

    public record RagHit(
            int rank,
            String documentId,
            String content,
            Map<String, Object> metadata) {
    }
}















