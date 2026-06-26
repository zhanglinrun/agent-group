package com.linrun.infrastructure.agent.gateway;

import com.linrun.domain.agent.file.adapter.FileStoragePort;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 会话文件对象存储实现，对接 MinIO。
 */
@Component
public class MinioFileStorageAdapter implements FileStoragePort {

    private static final String DEFAULT_BUCKET = "agent-group";
    private static final int PRESIGN_EXPIRE_DAYS = 7;

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioFileStorageAdapter(@Value("${agent.group.minio.endpoint:http://127.0.0.1:9000}") String endpoint,
                                   @Value("${agent.group.minio.access-key:}") String accessKey,
                                   @Value("${agent.group.minio.secret-key:}") String secretKey,
                                   @Value("${agent.group.minio.bucket-name:agent-group}") String bucketName) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucketName = (bucketName == null || bucketName.isBlank()) ? DEFAULT_BUCKET : bucketName;
    }

    @Override
    public String upload(String objectName, byte[] content, String contentType) throws Exception {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(stream, content.length, -1)
                    .contentType(contentType)
                    .build());
            return presignedDownloadUrl(objectName);
        }
    }

    @Override
    public InputStream download(String objectName) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Override
    public void delete(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .build());
    }

    @Override
    public String presignedDownloadUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucketName)
                .object(objectName)
                .expiry(PRESIGN_EXPIRE_DAYS, TimeUnit.DAYS)
                .build());
    }
}
