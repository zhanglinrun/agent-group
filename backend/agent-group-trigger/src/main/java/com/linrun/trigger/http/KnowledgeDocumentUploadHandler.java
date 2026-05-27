package com.linrun.trigger.http;

import com.linrun.api.dto.UploadKnowledgeDocumentRequest;
import com.linrun.api.dto.KnowledgeFragmentDTO;
import com.linrun.api.dto.UploadKnowledgeDocumentResponse;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentTextExtractor;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeObjectStorageClient;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.CreateKnowledgeDocumentCommand;
import com.linrun.domain.agent.knowledge.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentBuildResult;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgeDocumentUploadHandler {

    private static final String DEFAULT_KNOWLEDGE_VERSION = "v1";
    private static final String DEFAULT_SOURCE_TYPE = "OPERATOR_UPLOAD";
    private static final int MAX_FILENAME_LENGTH = 160;
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
            throw new AppException("0001", "upload request cannot be null");
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
        request.setDocumentType(StringUtils.hasText(documentType) ? documentType : "商品资料");
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

    private void validateUploadFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || filename.length() > MAX_FILENAME_LENGTH) {
            throw new AppException("UPLOAD_0001", "文件名不能为空，且长度不能超过 160 个字符");
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
            throw new AppException("0001", "上传文件读取失败：" + e.getMessage());
        }
    }

    private String extractFileText(MultipartFile file, byte[] content) {
        if (knowledgeDocumentTextExtractor != null) {
            return knowledgeDocumentTextExtractor.extract(file.getOriginalFilename(), file.getContentType(), content);
        }
        return new String(content, StandardCharsets.UTF_8);
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
        dto.setFragmentStatus(fragment.getFragmentStatus().name());
        return dto;
    }
}
