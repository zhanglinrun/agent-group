package com.linrun.trigger.service;

import com.linrun.api.agent.response.GuideImageUploadResponse;
import com.linrun.domain.conversation.service.GuideImageInputService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GuideImageUploadService {

    private static final int MAX_FILENAME_LENGTH = 160;
    private static final int MIN_IMAGE_SIDE_PIXELS = 11;
    private static final String DEFAULT_ALLOWED_CONTENT_TYPES_CONFIG = "image/jpeg,image/png,image/webp,image/gif";
    private static final Set<String> DEFAULT_ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif");

    private final GuideImageInputService guideImageInputService;

    @Value("${agent.group.multimodal.image.max-file-size-bytes:5242880}")
    private long maxFileSizeBytes = 5 * 1024 * 1024L;

    @Value("${agent.group.multimodal.image.allowed-content-types:image/jpeg,image/png,image/webp,image/gif}")
    private String allowedContentTypes = DEFAULT_ALLOWED_CONTENT_TYPES_CONFIG;

    public GuideImageUploadService(GuideImageInputService guideImageInputService) {
        this.guideImageInputService = guideImageInputService;
    }

    public GuideImageUploadResponse uploadImage(MultipartFile file) {
        validateImage(file);
        byte[] content = readFile(file);
        String contentType = file.getContentType().toLowerCase(Locale.ROOT);
        validateImageDimensions(contentType, content);
        String imageName = safeImageName(file.getOriginalFilename());
        String imageUrl = toDataUrl(contentType, content);
        String imageSummary = guideImageInputService.parseImage(imageUrl, imageName);

        GuideImageUploadResponse response = new GuideImageUploadResponse();
        response.setImageName(imageName);
        response.setImageUrl(imageUrl);
        response.setContentType(contentType);
        response.setImageSize(file.getSize());
        response.setImageSummary(imageSummary);
        return response;
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("MULTIMODAL_0001", "上传图片不能为空");
        }
        String imageName = safeImageName(file.getOriginalFilename());
        if (!StringUtils.hasText(imageName) || imageName.length() > MAX_FILENAME_LENGTH) {
            throw new AppException("MULTIMODAL_0002", "图片文件名不能为空，且长度不能超过 160 个字符");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new AppException("MULTIMODAL_0003", "上传图片超过大小限制");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)
                || !allowedContentTypeSet().contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException("MULTIMODAL_0004", "当前只允许上传 jpeg、png、webp、gif 图片");
        }
    }

    private String safeImageName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "";
        }
        String normalized = originalFilename.replace("\\", "/");
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    private Set<String> allowedContentTypeSet() {
        Set<String> configuredTypes = Arrays.stream(allowedContentTypes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return configuredTypes.isEmpty() ? DEFAULT_ALLOWED_CONTENT_TYPES : configuredTypes;
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new AppException("MULTIMODAL_0005", "上传图片读取失败：" + e.getMessage());
        }
    }

    private void validateImageDimensions(String contentType, byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                if ("image/webp".equals(contentType)) {
                    return;
                }
                throw new AppException("MULTIMODAL_0006", "图片内容无法解析");
            }
            if (image.getWidth() < MIN_IMAGE_SIDE_PIXELS || image.getHeight() < MIN_IMAGE_SIDE_PIXELS) {
                throw new AppException("MULTIMODAL_0007", "图片宽高不能小于 11 像素");
            }
        } catch (IOException e) {
            throw new AppException("MULTIMODAL_0006", "图片内容无法解析");
        }
    }

    private String toDataUrl(String contentType, byte[] content) {
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(content);
    }
}
