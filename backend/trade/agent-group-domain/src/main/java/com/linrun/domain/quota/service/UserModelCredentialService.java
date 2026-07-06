package com.linrun.domain.quota.service;

import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class UserModelCredentialService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${agent.user-model.crypto-secret:${AGENT_USER_MODEL_CRYPTO_SECRET:}}")
    private String modelConfigCryptoSecret = "";

    public String normalizeModelBaseUrl(String value) {
        String text = safe(value).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.regionMatches(true, 0, "ttps://", 0, "ttps://".length())) {
            text = "h" + text;
        }
        if (!text.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            text = "https://" + text.replaceFirst("^/+", "");
        }
        URI uri;
        try {
            uri = URI.create(text);
        } catch (Exception e) {
            throw new AppException("MODEL_CONFIG_0002", "自定义模型 API 地址格式不正确");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || !StringUtils.hasText(host)) {
            throw new AppException("MODEL_CONFIG_0002", "自定义模型 API 地址仅支持 HTTPS");
        }
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".local")
                || lowerHost.startsWith("127.")
                || lowerHost.startsWith("10.")
                || lowerHost.startsWith("192.168.")
                || lowerHost.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")) {
            throw new AppException("MODEL_CONFIG_0002", "自定义模型 API 地址不能指向本地或内网地址");
        }
        return text.replaceAll("/+$", "");
    }

    public boolean modelConfigComplete(String baseUrl, String model, String encryptedApiKey) {
        return StringUtils.hasText(baseUrl)
                && StringUtils.hasText(model)
                && StringUtils.hasText(encryptedApiKey);
    }

    public String encryptApiKey(String apiKey) {
        if (!StringUtils.hasText(modelConfigCryptoSecret)) {
            throw new AppException("MODEL_CONFIG_0003", "请先配置自定义模型密钥加密密钥");
        }
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new AppException("MODEL_CONFIG_0004", "自定义模型密钥加密失败");
        }
    }

    public String decryptApiKey(String encryptedApiKey) {
        if (!StringUtils.hasText(encryptedApiKey)) {
            return "";
        }
        if (!StringUtils.hasText(modelConfigCryptoSecret)) {
            throw new AppException("MODEL_CONFIG_0003", "请先配置自定义模型密钥加密密钥");
        }
        String[] parts = encryptedApiKey.split(":", 3);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new AppException("MODEL_CONFIG_0005", "自定义模型密钥格式不正确");
        }
        try {
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] cipherText = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException("MODEL_CONFIG_0005", "自定义模型密钥解密失败");
        }
    }

    public String maskApiKey(String apiKey) {
        String text = safe(apiKey).trim();
        if (text.length() <= 8) {
            return "****";
        }
        return text.substring(0, 4) + "****" + text.substring(text.length() - 4);
    }

    private SecretKeySpec secretKey() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(modelConfigCryptoSecret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
