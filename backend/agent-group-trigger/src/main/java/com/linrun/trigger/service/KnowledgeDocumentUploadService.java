package com.linrun.trigger.service;

import com.linrun.api.knowledge.request.UploadKnowledgeDocumentRequest;
import com.linrun.api.knowledge.response.KnowledgeFragmentDTO;
import com.linrun.api.knowledge.response.UploadKnowledgeDocumentResponse;
import com.linrun.domain.knowledge.adapter.KnowledgeObjectStorageClient;
import com.linrun.domain.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.knowledge.model.CreateKnowledgeDocumentCommand;
import com.linrun.domain.knowledge.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.knowledge.model.KnowledgeDocument;
import com.linrun.domain.knowledge.model.KnowledgeDocumentBuildResult;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.domain.knowledge.model.StoredKnowledgeObject;
import com.linrun.domain.knowledge.service.KnowledgeDocumentParser;
import com.linrun.domain.knowledge.service.KnowledgeDocumentService;
import com.linrun.domain.knowledge.service.KnowledgeVectorService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class KnowledgeDocumentUploadService {

    private static final String DEFAULT_KNOWLEDGE_VERSION = "v1";
    private static final String DEFAULT_SOURCE_TYPE = "OPERATOR_UPLOAD";

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentParser knowledgeDocumentParser;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVectorService knowledgeVectorService;
    private final KnowledgeObjectStorageClient knowledgeObjectStorageClient;

    @Autowired
    public KnowledgeDocumentUploadService(KnowledgeDocumentService knowledgeDocumentService,
                                          KnowledgeDocumentParser knowledgeDocumentParser,
                                          KnowledgeDocumentRepository knowledgeDocumentRepository,
                                          KnowledgeVectorService knowledgeVectorService,
                                          KnowledgeObjectStorageClient knowledgeObjectStorageClient) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentParser = knowledgeDocumentParser;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeVectorService = knowledgeVectorService;
        this.knowledgeObjectStorageClient = knowledgeObjectStorageClient;
    }

    public KnowledgeDocumentUploadService(KnowledgeDocumentService knowledgeDocumentService,
                                          KnowledgeDocumentParser knowledgeDocumentParser,
                                          KnowledgeDocumentRepository knowledgeDocumentRepository,
                                          KnowledgeVectorService knowledgeVectorService) {
        this(knowledgeDocumentService, knowledgeDocumentParser, knowledgeDocumentRepository, knowledgeVectorService, null);
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
        buildResult.getFragments().forEach(knowledgeVectorService::saveFragmentEmbedding);
        return toResponse(buildResult);
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
        request.setContent(new String(content, StandardCharsets.UTF_8));

        UploadKnowledgeDocumentResponse response = uploadText(request);
        response.setObjectStorageBucket(storedObject.getBucketName());
        response.setObjectKey(storedObject.getObjectKey());
        response.setObjectUrl(storedObject.getObjectUrl());
        response.setContentType(storedObject.getContentType());
        response.setObjectSize(storedObject.getObjectSize());
        return response;
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
