package com.linrun.trigger.http;

import com.linrun.api.dto.AcademicFileUploadResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentTextExtractor;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeObjectStorageClient;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.domain.agent.knowledge.model.StoredKnowledgeObject;
import com.linrun.domain.agent.knowledge.service.KnowledgeVectorService;
import com.linrun.types.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AcademicFileUploadHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcademicFileUploadHandler.class);
    private static final int MAX_FILENAME_LENGTH = 160;
    private static final int VECTOR_CHUNK_SIZE = 900;
    private static final int VECTOR_CHUNK_OVERLAP = 120;
    private static final Set<String> BLOCKED_EXTENSION_MARKERS = Set.of(
            ".jsp.", ".php.", ".asp.", ".aspx.", ".js.", ".exe.", ".sh.", ".bat.", ".cmd.");

    private final UserAccountService userAccountService;
    private final AcademicAgentRepository academicAgentRepository;
    private final KnowledgeDocumentTextExtractor textExtractor;
    private final KnowledgeObjectStorageClient objectStorageClient;
    private final KnowledgeVectorService knowledgeVectorService;

    @Value("${agent.group.upload.allowed-extensions:md,txt,pdf,docx,png,jpg,jpeg,webp}")
    private String allowedExtensions = "md,txt,pdf,docx,png,jpg,jpeg,webp";

    @Value("${agent.group.upload.max-file-size-bytes:10485760}")
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    public AcademicFileUploadHandler(UserAccountService userAccountService,
                                     AcademicAgentRepository academicAgentRepository,
                                     KnowledgeDocumentTextExtractor textExtractor,
                                     KnowledgeObjectStorageClient objectStorageClient,
                                     KnowledgeVectorService knowledgeVectorService) {
        this.userAccountService = userAccountService;
        this.academicAgentRepository = academicAgentRepository;
        this.textExtractor = textExtractor;
        this.objectStorageClient = objectStorageClient;
        this.knowledgeVectorService = knowledgeVectorService;
    }

    public AcademicFileUploadResponse upload(String token, MultipartFile file, String sessionId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        validate(file);
        byte[] content = readFile(file);
        StoredKnowledgeObject storedObject = storeQuietly(file, content);
        String extractedText = extractText(file, content);
        String fileId = nextNo("AF");

        AcademicFile academicFile = new AcademicFile();
        academicFile.setFileId(fileId);
        academicFile.setUserId(user.getUserId());
        academicFile.setSessionId(StringUtils.hasText(sessionId) ? sessionId : "");
        academicFile.setFileName(safeFilename(file.getOriginalFilename()));
        academicFile.setFileType(extension(academicFile.getFileName()));
        academicFile.setFileSize(file.getSize());
        academicFile.setObjectUrl(storedObject == null ? "" : storedObject.getObjectUrl());
        academicFile.setContent(limit(extractedText, 60000));
        academicFile.setSummary(summary(extractedText));
        boolean vectorReady = indexFileVectors(academicFile, extractedText);
        academicFile.setStatus(vectorReady ? "PARSED_VECTOR_READY" : "PARSED");
        academicFile.setCreateTime(LocalDateTime.now());
        academicAgentRepository.saveFile(academicFile);

        AcademicFileUploadResponse response = new AcademicFileUploadResponse();
        response.setFileId(fileId);
        response.setFileName(academicFile.getFileName());
        response.setFileType(academicFile.getFileType());
        response.setFileSize(academicFile.getFileSize());
        response.setSummary(academicFile.getSummary());
        response.setStatus(academicFile.getStatus());
        return response;
    }

    private boolean indexFileVectors(AcademicFile file, String text) {
        if (file == null || !StringUtils.hasText(text)) {
            return false;
        }
        try {
            int rank = 1;
            for (String chunk : chunks(text)) {
                KnowledgeFragment fragment = new KnowledgeFragment();
                fragment.setFragmentId(nextNo("AFK"));
                fragment.setDocumentId(file.getFileId());
                fragment.setGoodsId(file.getFileId());
                fragment.setDocumentType("ACADEMIC_FILE");
                fragment.setKnowledgeVersion("v1");
                fragment.setContent(chunk);
                fragment.setRankNo(rank++);
                fragment.setParentFragmentId("");
                fragment.setBrotherGroupId(file.getFileId());
                fragment.setBrotherIndex(rank - 1);
                fragment.setBrotherTotal(1);
                fragment.setChunkType("CHILD");
                fragment.setEmbeddingEnabled(Boolean.TRUE);
                fragment.setFragmentStatus(KnowledgeFragmentStatus.ENABLED);
                fragment.setEnabled(Boolean.TRUE);
                fragment.setCreateTime(LocalDateTime.now());
                fragment.setUpdateTime(LocalDateTime.now());
                knowledgeVectorService.saveFragmentEmbedding(fragment);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("academic file vector index skipped, fileId={}, reason={}",
                    file.getFileId(), e.getClass().getSimpleName());
            return false;
        }
    }

    private java.util.List<String> chunks(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return java.util.List.of();
        }
        java.util.List<String> chunks = new java.util.ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + VECTOR_CHUNK_SIZE);
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(0, end - VECTOR_CHUNK_OVERLAP);
        }
        return chunks;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("UPLOAD_0001", "上传文件不能为空");
        }
        String filename = safeFilename(file.getOriginalFilename());
        if (!StringUtils.hasText(filename) || filename.length() > MAX_FILENAME_LENGTH) {
            throw new AppException("UPLOAD_0002", "文件名不能为空，且长度不能超过 160 个字符");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new AppException("UPLOAD_0003", "上传文件超过大小限制");
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSION_MARKERS.stream().anyMatch(lowerName::contains)
                || !allowedExtensionSet().contains(extension(lowerName))) {
            throw new AppException("UPLOAD_0004", "文件类型不在学术助手白名单内");
        }
    }

    private Set<String> allowedExtensionSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new AppException("UPLOAD_0005", "上传文件读取失败：" + e.getMessage());
        }
    }

    private StoredKnowledgeObject storeQuietly(MultipartFile file, byte[] content) {
        try {
            return objectStorageClient.store(file.getOriginalFilename(), file.getContentType(), content);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractText(MultipartFile file, byte[] content) {
        try {
            return textExtractor.extract(file.getOriginalFilename(), file.getContentType(), content);
        } catch (Exception e) {
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private String summary(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return limit(normalized, 500);
    }

    private String safeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String normalized = originalFilename.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    private String extension(String filename) {
        int index = filename == null ? -1 : filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private String nextNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase();
    }
}
