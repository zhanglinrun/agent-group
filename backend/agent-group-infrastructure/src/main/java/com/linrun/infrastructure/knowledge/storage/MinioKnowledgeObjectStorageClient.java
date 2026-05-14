package com.linrun.infrastructure.knowledge.storage;

import com.linrun.domain.knowledge.adapter.KnowledgeObjectStorageClient;
import com.linrun.domain.knowledge.model.StoredKnowledgeObject;
import com.linrun.types.exception.AppException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Component
public class MinioKnowledgeObjectStorageClient implements KnowledgeObjectStorageClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final String endpoint;
    private final String bucketName;
    private final MinioClient minioClient;

    public MinioKnowledgeObjectStorageClient(@Value("${agent.group.minio.endpoint:}") String endpoint,
                                             @Value("${agent.group.minio.access-key:}") String accessKey,
                                             @Value("${agent.group.minio.secret-key:}") String secretKey,
                                             @Value("${agent.group.minio.bucket-name:agent-group}") String bucketName) {
        this.endpoint = endpoint;
        this.bucketName = bucketName;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public StoredKnowledgeObject store(String originalFilename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new AppException("0001", "上传文件内容不能为空");
        }
        try {
            ensureBucket();
            String objectKey = nextObjectKey(originalFilename);
            String safeContentType = StringUtils.hasText(contentType) ? contentType : "application/octet-stream";
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(safeContentType)
                    .build());
            return storedObject(originalFilename, safeContentType, content.length, objectKey);
        } catch (Exception e) {
            throw new AppException("MINIO_0001", "知识文档写入对象存储失败：" + e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        }
    }

    private StoredKnowledgeObject storedObject(String originalFilename,
                                               String contentType,
                                               long objectSize,
                                               String objectKey) {
        StoredKnowledgeObject object = new StoredKnowledgeObject();
        object.setBucketName(bucketName);
        object.setObjectKey(objectKey);
        object.setObjectUrl(endpointWithoutSlash() + "/" + bucketName + "/" + objectKey);
        object.setOriginalFilename(originalFilename);
        object.setContentType(contentType);
        object.setObjectSize(objectSize);
        return object;
    }

    private String endpointWithoutSlash() {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private String nextObjectKey(String originalFilename) {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        String safeName = safeFilename(originalFilename);
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return "knowledge/" + datePart + "/" + randomPart + "-" + safeName;
    }

    private String safeFilename(String originalFilename) {
        String fallback = "document.txt";
        String candidate = StringUtils.hasText(originalFilename) ? originalFilename : fallback;
        String normalized = candidate.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        if (index >= 0) {
            normalized = normalized.substring(index + 1);
        }
        normalized = normalized.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_")
                .toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }
}
