package com.linrun.infrastructure.springai;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
public class SpringAiModelFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiModelFactory.class);

    private final boolean enabled;
    private final boolean vectorStoreEnabled;
    private final String baseUrl;
    private final String apiKey;
    private final String chatModelName;
    private final String embeddingModelName;
    private final int dimension;
    private final String vectorJdbcUrl;
    private final String vectorUsername;
    private final String vectorPassword;
    private final ObservationRegistry observationRegistry;

    private volatile Optional<ChatClient> chatClient;
    private volatile Optional<EmbeddingModel> embeddingModel;
    private volatile Optional<VectorStore> vectorStore;

    public SpringAiModelFactory(@Value("${agent.group.spring-ai.enabled:true}") boolean enabled,
                                @Value("${agent.group.spring-ai.vector-store-enabled:true}") boolean vectorStoreEnabled,
                                @Value("${agent.group.llm.base-url:}") String baseUrl,
                                @Value("${agent.group.llm.api-key:}") String apiKey,
                                @Value("${agent.group.llm.chat-model:qwen-plus}") String chatModelName,
                                @Value("${agent.group.llm.embedding-model:text-embedding-v3}") String embeddingModelName,
                                @Value("${agent.group.vector.dimension:1024}") int dimension,
                                @Value("${agent.group.vector.host:}") String vectorHost,
                                @Value("${agent.group.vector.port:15432}") int vectorPort,
                                @Value("${agent.group.vector.database:}") String vectorDatabase,
                                @Value("${agent.group.vector.username:}") String vectorUsername,
                                @Value("${agent.group.vector.password:}") String vectorPassword,
                                ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        this.enabled = enabled;
        this.vectorStoreEnabled = vectorStoreEnabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatModelName = chatModelName;
        this.embeddingModelName = embeddingModelName;
        this.dimension = Math.max(16, dimension);
        this.vectorJdbcUrl = StringUtils.hasText(vectorHost) && StringUtils.hasText(vectorDatabase)
                ? "jdbc:postgresql://" + vectorHost + ":" + vectorPort + "/" + vectorDatabase
                : "";
        this.vectorUsername = vectorUsername;
        this.vectorPassword = vectorPassword;
        this.observationRegistry = observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    public Optional<ChatClient> chatClient() {
        Optional<ChatClient> local = chatClient;
        if (local == null) {
            local = buildChatClient();
            chatClient = local;
        }
        return local;
    }

    public Optional<EmbeddingModel> embeddingModel() {
        Optional<EmbeddingModel> local = embeddingModel;
        if (local == null) {
            local = buildEmbeddingModel();
            embeddingModel = local;
        }
        return local;
    }

    public Optional<VectorStore> vectorStore() {
        Optional<VectorStore> local = vectorStore;
        if (local == null) {
            local = buildVectorStore();
            vectorStore = local;
        }
        return local;
    }

    private Optional<ChatClient> buildChatClient() {
        if (!isModelConfigured()) {
            return Optional.empty();
        }
        try {
            ChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi())
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(chatModelName)
                            .temperature(0.2D)
                            .streamUsage(true)
                            .build())
                    .toolCallingManager(DefaultToolCallingManager.builder()
                            .observationRegistry(observationRegistry)
                            .build())
                    .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                    .observationRegistry(observationRegistry)
                    .build();
            return Optional.of(ChatClient.create(chatModel, observationRegistry));
        } catch (Exception e) {
            LOGGER.warn("spring ai chat client disabled, reason={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<EmbeddingModel> buildEmbeddingModel() {
        if (!isModelConfigured()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new OpenAiEmbeddingModel(openAiApi(), MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder()
                            .model(embeddingModelName)
                            .encodingFormat("float")
                            .dimensions(dimension)
                            .build(),
                    RetryUtils.DEFAULT_RETRY_TEMPLATE,
                    observationRegistry));
        } catch (Exception e) {
            LOGGER.warn("spring ai embedding model disabled, reason={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<VectorStore> buildVectorStore() {
        if (!vectorStoreEnabled || !StringUtils.hasText(vectorJdbcUrl)) {
            return Optional.empty();
        }
        Optional<EmbeddingModel> model = embeddingModel();
        if (model.isEmpty()) {
            return Optional.empty();
        }
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(vectorJdbcUrl);
            dataSource.setUsername(vectorUsername);
            dataSource.setPassword(vectorPassword);
            PgVectorStore store = PgVectorStore.builder(new JdbcTemplate(dataSource), model.get())
                    .vectorTableName("spring_ai_knowledge_embedding")
                    .idType(PgVectorStore.PgIdType.TEXT)
                    .dimensions(dimension)
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    .indexType(PgVectorStore.PgIndexType.HNSW)
                    .initializeSchema(true)
                    .build();
            store.afterPropertiesSet();
            return Optional.of(store);
        } catch (Exception e) {
            LOGGER.warn("spring ai vector store disabled, reason={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private OpenAiApi openAiApi() {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();
    }

    private boolean isModelConfigured() {
        return enabled && StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
    }
}
