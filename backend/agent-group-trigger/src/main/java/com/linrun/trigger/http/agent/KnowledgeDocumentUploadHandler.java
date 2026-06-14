package com.linrun.trigger.http.agent;

import com.linrun.api.dto.UploadKnowledgeDocumentRequest;
import com.linrun.api.dto.KnowledgeFragmentDTO;
import com.linrun.api.dto.UploadKnowledgeDocumentResponse;
import com.linrun.api.dto.UploadKnowledgeWebUrlRequest;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentTextExtractor;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeObjectStorageClient;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.CreateKnowledgeDocumentCommand;
import com.linrun.domain.agent.knowledge.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentBuildResult;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.StoredKnowledgeObject;
import com.linrun.domain.agent.knowledge.service.KnowledgeDocumentParser;
import com.linrun.domain.agent.knowledge.service.KnowledgeDocumentService;
import com.linrun.domain.agent.knowledge.service.KnowledgeVectorService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeDocumentUploadHandler {

    private static final String DEFAULT_KNOWLEDGE_VERSION = "v1";
    private static final String DEFAULT_SOURCE_TYPE = "OPERATOR_UPLOAD";
    private static final String WEB_SOURCE_TYPE = "WEB_URL";
    private static final int MAX_FILENAME_LENGTH = 160;
    private static final int MAX_WEB_TITLE_LENGTH = 120;
    private static final Duration WEB_FETCH_TIMEOUT = Duration.ofSeconds(8);
    private static final Set<String> BLOCKED_EXTENSION_MARKERS = Set.of(
            ".jsp.", ".php.", ".asp.", ".aspx.", ".js.", ".exe.", ".sh.", ".bat.", ".cmd.");
    private static final Set<String> STRICT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown");

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentParser knowledgeDocumentParser;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVectorService knowledgeVectorService;
    private final KnowledgeObjectStorageClient knowledgeObjectStorageClient;
    private final KnowledgeDocumentTextExtractor knowledgeDocumentTextExtractor;

    @Value("${agent.group.upload.allowed-extensions:md,txt,pdf,docx}")
    private String allowedExtensions = "md,txt,pdf,docx";

    @Value("${agent.group.upload.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    @Value("${agent.group.upload.max-web-content-bytes:1048576}")
    private int maxWebContentBytes = 1024 * 1024;

    @Value("${agent.group.upload.allow-private-web-url:false}")
    private boolean allowPrivateWebUrl = false;

    @Autowired
    public KnowledgeDocumentUploadHandler(KnowledgeDocumentService knowledgeDocumentService,
                                           KnowledgeDocumentParser knowledgeDocumentParser,
                                           KnowledgeDocumentRepository knowledgeDocumentRepository,
                                          KnowledgeVectorService knowledgeVectorService,
                                          KnowledgeObjectStorageClient knowledgeObjectStorageClient,
                                          KnowledgeDocumentTextExtractor knowledgeDocumentTextExtractor) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentParser = knowledgeDocumentParser;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeVectorService = knowledgeVectorService;
        this.knowledgeObjectStorageClient = knowledgeObjectStorageClient;
        this.knowledgeDocumentTextExtractor = knowledgeDocumentTextExtractor;
    }

    public KnowledgeDocumentUploadHandler(KnowledgeDocumentService knowledgeDocumentService,
                                          KnowledgeDocumentParser knowledgeDocumentParser,
                                          KnowledgeDocumentRepository knowledgeDocumentRepository,
                                          KnowledgeVectorService knowledgeVectorService,
                                          KnowledgeObjectStorageClient knowledgeObjectStorageClient) {
        this(knowledgeDocumentService, knowledgeDocumentParser, knowledgeDocumentRepository, knowledgeVectorService, knowledgeObjectStorageClient, null);
    }

    public KnowledgeDocumentUploadHandler(KnowledgeDocumentService knowledgeDocumentService,
                                          KnowledgeDocumentParser knowledgeDocumentParser,
                                          KnowledgeDocumentRepository knowledgeDocumentRepository,
                                          KnowledgeVectorService knowledgeVectorService) {
        this(knowledgeDocumentService, knowledgeDocumentParser, knowledgeDocumentRepository, knowledgeVectorService, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadKnowledgeDocumentResponse uploadText(UploadKnowledgeDocumentRequest request) {
        if (request == null) {
            throw new AppException("0001", "上传请求不能为空");
        }

        CreateKnowledgeDocumentCommand documentCommand = toDocumentCommand(request);
        List<CreateKnowledgeFragmentCommand> fragmentCommands = knowledgeDocumentParser.parse(
                request.getGoodsId(),
                request.getContent());
        KnowledgeDocumentBuildResult buildResult = knowledgeDocumentService.createParsedDocument(
                documentCommand,
                fragmentCommands);
        knowledgeDocumentRepository.save(buildResult.getDocument(), buildResult.getFragments());
        int embeddingFailedCount = saveFragmentEmbeddings(buildResult.getFragments());
        if (embeddingFailedCount > 0) {
            buildResult.getDocument().markEmbeddingFailed();
            knowledgeDocumentRepository.updateDocumentStatus(buildResult.getDocument());
        }
        return toResponse(buildResult);
    }

    private int saveFragmentEmbeddings(List<KnowledgeFragment> fragments) {
        int failedCount = 0;
        for (KnowledgeFragment fragment : fragments) {
            try {
                knowledgeVectorService.saveFragmentEmbedding(fragment);
            } catch (Exception e) {
                failedCount++;
            }
        }
        return failedCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadKnowledgeDocumentResponse uploadFile(MultipartFile file,
                                                      String goodsId,
                                                      String documentName,
                                                      String documentType,
                                                      String knowledgeVersion) {
        if (file == null || file.isEmpty()) {
            throw new AppException("0001", "上传文件不能为空");
        }
        if (knowledgeObjectStorageClient == null) {
            throw new AppException("MINIO_0002", "对象存储客户端不可用");
        }
        validateUploadFile(file);

        byte[] content = readFile(file);
        StoredKnowledgeObject storedObject = knowledgeObjectStorageClient.store(
                file.getOriginalFilename(),
                file.getContentType(),
                content);
        UploadKnowledgeDocumentRequest request = new UploadKnowledgeDocumentRequest();
        request.setDocumentName(StringUtils.hasText(documentName) ? documentName : file.getOriginalFilename());
        request.setDocumentType(StringUtils.hasText(documentType) ? documentType : "知识资料");
        request.setKnowledgeVersion(knowledgeVersion);
        request.setSourceType("MINIO_OBJECT");
        request.setSourceName(storedObject.getObjectKey());
        request.setGoodsId(goodsId);
        request.setContent(extractFileText(file, content));

        UploadKnowledgeDocumentResponse response = uploadText(request);
        response.setObjectStorageBucket(storedObject.getBucketName());
        response.setObjectKey(storedObject.getObjectKey());
        response.setObjectUrl(storedObject.getObjectUrl());
        response.setContentType(storedObject.getContentType());
        response.setObjectSize(storedObject.getObjectSize());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadKnowledgeDocumentResponse uploadWebUrl(UploadKnowledgeWebUrlRequest request) {
        if (request == null) {
            throw new AppException("0001", "upload web url request cannot be null");
        }
        URI uri = validateWebUrl(request.getUrl());
        WebPageContent webPage = fetchWebPage(uri);

        UploadKnowledgeDocumentRequest uploadRequest = new UploadKnowledgeDocumentRequest();
        uploadRequest.setDocumentName(StringUtils.hasText(request.getDocumentName())
                ? request.getDocumentName().trim()
                : defaultWebDocumentName(uri, webPage.title()));
        uploadRequest.setDocumentType(StringUtils.hasText(request.getDocumentType())
                ? request.getDocumentType().trim()
                : "Web Page");
        uploadRequest.setKnowledgeVersion(request.getKnowledgeVersion());
        uploadRequest.setSourceType(WEB_SOURCE_TYPE);
        uploadRequest.setSourceName(uri.toString());
        uploadRequest.setGoodsId(StringUtils.hasText(request.getGoodsId()) ? request.getGoodsId().trim() : "global");
        uploadRequest.setContent(webPage.content());
        return uploadText(uploadRequest);
    }

    private void validateUploadFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || filename.length() > MAX_FILENAME_LENGTH) {
            throw new AppException("UPLOAD_0001", "filename cannot be blank and length cannot exceed 160 characters");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new AppException("UPLOAD_0002", "上传文件超过大小限制");
        }
        String safeName = filename.replace("\\", "/");
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSION_MARKERS.stream().anyMatch(safeName::contains)) {
            throw new AppException("UPLOAD_0003", "上传文件包含高风险扩展名");
        }
        String extension = extension(safeName);
        if (!allowedExtensionSet().contains(extension)) {
            throw new AppException("UPLOAD_0004", "当前只允许上传 md、txt、pdf、docx 类型文件");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !"application/octet-stream".equalsIgnoreCase(contentType)
                && !STRICT_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException("UPLOAD_0005", "上传文件类型与知识库白名单不匹配");
        }
    }

    private Set<String> allowedExtensionSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1);
    }

    private CreateKnowledgeDocumentCommand toDocumentCommand(UploadKnowledgeDocumentRequest request) {
        CreateKnowledgeDocumentCommand command = new CreateKnowledgeDocumentCommand();
        command.setDocumentName(request.getDocumentName());
        command.setDocumentType(request.getDocumentType());
        command.setKnowledgeVersion(StringUtils.hasText(request.getKnowledgeVersion())
                ? request.getKnowledgeVersion()
                : DEFAULT_KNOWLEDGE_VERSION);
        command.setSourceType(StringUtils.hasText(request.getSourceType())
                ? request.getSourceType()
                : DEFAULT_SOURCE_TYPE);
        command.setSourceName(StringUtils.hasText(request.getSourceName())
                ? request.getSourceName()
                : request.getDocumentName());
        return command;
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new AppException("0001", "upload file read failed: " + e.getMessage());
        }
    }

    private String extractFileText(MultipartFile file, byte[] content) {
        if (knowledgeDocumentTextExtractor != null) {
            return knowledgeDocumentTextExtractor.extract(file.getOriginalFilename(), file.getContentType(), content);
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private URI validateWebUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new AppException("UPLOAD_0010", "web url cannot be blank");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new AppException("UPLOAD_0011", "web url only supports http or https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new AppException("UPLOAD_0012", "web url host cannot be blank");
            }
            if (!allowPrivateWebUrl) {
                validatePublicHost(uri.getHost());
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new AppException("UPLOAD_0013", "web url format is invalid");
        }
    }

    private void validatePublicHost(String host) {
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new AppException("UPLOAD_0018", "web url cannot point to local host");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalizedHost)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()) {
                    throw new AppException("UPLOAD_0018", "web url cannot point to private host");
                }
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("UPLOAD_0019", "web url host cannot be resolved");
        }
    }

    private WebPageContent fetchWebPage(URI uri) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(WEB_FETCH_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(WEB_FETCH_TIMEOUT)
                    .header("User-Agent", "agent-group-knowledge-importer/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AppException("UPLOAD_0014", "web url fetch failed: " + response.statusCode());
            }
            long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (contentLength > maxWebContentBytes) {
                throw new AppException("UPLOAD_0015", "web url content exceeds size limit");
            }
            byte[] body = readLimited(response.body(), maxWebContentBytes);
            String raw = new String(body, StandardCharsets.UTF_8);
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (contentType.toLowerCase(Locale.ROOT).contains("html")) {
                org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(raw, uri.toString());
                document.select("script,style,noscript,svg,canvas").remove();
                String title = document.title();
                String text = document.body() == null ? document.text() : document.body().text();
                return new WebPageContent(title, normalizeWebText(text));
            }
            return new WebPageContent("", normalizeWebText(raw));
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("UPLOAD_0016", "web url fetch failed: " + e.getMessage());
        }
    }

    private byte[] readLimited(InputStream inputStream, int limit) throws IOException {
        try (InputStream input = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new AppException("UPLOAD_0015", "web url content exceeds size limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String normalizeWebText(String text) {
        String normalized = String.valueOf(text == null ? "" : text)
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            throw new AppException("UPLOAD_0017", "web url content cannot be empty");
        }
        return normalized;
    }

    private String defaultWebDocumentName(URI uri, String title) {
        String value = StringUtils.hasText(title) ? title.trim() : uri.getHost();
        if (value.length() > MAX_WEB_TITLE_LENGTH) {
            return value.substring(0, MAX_WEB_TITLE_LENGTH);
        }
        return value;
    }

    private UploadKnowledgeDocumentResponse toResponse(KnowledgeDocumentBuildResult buildResult) {
        KnowledgeDocument document = buildResult.getDocument();
        UploadKnowledgeDocumentResponse response = new UploadKnowledgeDocumentResponse();
        response.setDocumentId(document.getDocumentId());
        response.setDocumentName(document.getDocumentName());
        response.setDocumentType(document.getDocumentType());
        response.setKnowledgeVersion(document.getKnowledgeVersion());
        response.setSourceType(document.getSourceType());
        response.setSourceName(document.getSourceName());
        response.setDocumentStatus(document.getDocumentStatus().name());
        response.setParseStatus(buildResult.getFragments().isEmpty() ? "PENDING" : "PARSED");
        response.setEmbeddingStatus(embeddingStatus(document));
        response.setRetrievalReady(KnowledgeDocumentStatus.ENABLED.equals(document.getDocumentStatus())
                && Boolean.TRUE.equals(document.getEnabled())
                && !buildResult.getFragments().isEmpty());
        response.setFailureReason(failureReason(document, buildResult.getFragments().isEmpty()));
        response.setFragmentCount(buildResult.getFragments().size());
        response.setCreateTime(document.getCreateTime());
        response.setFragments(buildResult.getFragments().stream()
                .map(this::toFragmentDTO)
                .toList());
        return response;
    }

    private KnowledgeFragmentDTO toFragmentDTO(KnowledgeFragment fragment) {
        KnowledgeFragmentDTO dto = new KnowledgeFragmentDTO();
        dto.setFragmentId(fragment.getFragmentId());
        dto.setDocumentId(fragment.getDocumentId());
        dto.setGoodsId(fragment.getGoodsId());
        dto.setDocumentType(fragment.getDocumentType());
        dto.setKnowledgeVersion(fragment.getKnowledgeVersion());
        dto.setContent(fragment.getContent());
        dto.setRankNo(fragment.getRankNo());
        dto.setParentFragmentId(fragment.getParentFragmentId());
        dto.setBrotherGroupId(fragment.getBrotherGroupId());
        dto.setBrotherIndex(fragment.getBrotherIndex());
        dto.setBrotherTotal(fragment.getBrotherTotal());
        dto.setChunkType(fragment.getChunkType());
        dto.setEmbeddingEnabled(fragment.getEmbeddingEnabled());
        dto.setFragmentStatus(fragment.getFragmentStatus().name());
        dto.setCitationLabel(citationLabel(fragment));
        dto.setCitationSnippet(snippet(fragment.getContent(), 120));
        return dto;
    }

    private String embeddingStatus(KnowledgeDocument document) {
        KnowledgeDocumentStatus status = document.getDocumentStatus();
        if (KnowledgeDocumentStatus.ENABLED.equals(status)) {
            return "READY";
        }
        if (KnowledgeDocumentStatus.EMBEDDING_FAILED.equals(status)) {
            return "FAILED";
        }
        if (KnowledgeDocumentStatus.DISABLED.equals(status)) {
            return "DISABLED";
        }
        return "PENDING";
    }

    private String failureReason(KnowledgeDocument document, boolean noFragments) {
        if (noFragments) {
            return "文档还没有生成可引用片段";
        }
        if (KnowledgeDocumentStatus.EMBEDDING_FAILED.equals(document.getDocumentStatus())) {
            return "向量入库失败，已保留解析片段，可重新执行向量补偿";
        }
        return "";
    }

    private String citationLabel(KnowledgeFragment fragment) {
        String documentId = fragment.getDocumentId() == null ? "-" : fragment.getDocumentId();
        String rankNo = fragment.getRankNo() == null ? "-" : String.valueOf(fragment.getRankNo());
        return documentId + "#" + rankNo;
    }

    private String snippet(String content, int limit) {
        String text = String.valueOf(content == null ? "" : content)
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

    private record WebPageContent(String title, String content) {
    }
}














