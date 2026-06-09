package com.linrun.trigger.http.account;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.WeixinTemplateMessageRequest;
import com.linrun.api.dto.WeixinTemplateMessageResponse;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WeixinOfficialAccountClient {

    private static final String ACCESS_TOKEN_URL =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String QR_CREATE_URL =
            "https://api.weixin.qq.com/cgi-bin/qrcode/create?access_token=%s";
    private static final String TEMPLATE_SEND_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s";
    private static final String QR_SHOW_URL =
            "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=%s";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String appId;
    private final String appSecret;
    private final String token;
    private final int qrExpireSeconds;

    private volatile String cachedAccessToken;
    private volatile long cachedAccessTokenExpireAt;

    public WeixinOfficialAccountClient(ObjectMapper objectMapper,
                                       @Value("${agent.group.wechat.official.app-id:}") String appId,
                                       @Value("${agent.group.wechat.official.app-secret:}") String appSecret,
                                       @Value("${agent.group.wechat.official.token:}") String token,
                                       @Value("${agent.group.wechat.official.qr-expire-seconds:900}") int qrExpireSeconds) {
        this.objectMapper = objectMapper;
        this.appId = appId;
        this.appSecret = appSecret;
        this.token = token;
        this.qrExpireSeconds = qrExpireSeconds;
    }

    public boolean officialConfigured() {
        return StringUtils.hasText(appId) && StringUtils.hasText(appSecret);
    }

    public String token() {
        return token;
    }

    public int qrExpireSeconds() {
        return qrExpireSeconds;
    }

    public WeixinQrTicket createTemporaryQrCode(String sceneId) {
        if (!officialConfigured()) {
            return WeixinQrTicket.mock(qrExpireSeconds);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("expire_seconds", qrExpireSeconds);
            payload.put("action_name", "QR_STR_SCENE");
            payload.put("action_info", Map.of("scene", Map.of("scene_str", sceneId)));
            JsonNode root = postJson(QR_CREATE_URL.formatted(accessToken()), payload);
            if (root.has("errcode") && root.get("errcode").asInt() != 0) {
                throw new AppException("WX_0002", "create weixin qr failed: " + root);
            }
            String ticket = root.path("ticket").asText("");
            return new WeixinQrTicket(
                    ticket,
                    QR_SHOW_URL.formatted(URLEncoder.encode(ticket, StandardCharsets.UTF_8)),
                    root.path("expire_seconds").asInt(qrExpireSeconds));
        } catch (IOException e) {
            throw new AppException("WX_0002", "create weixin qr failed: " + e.getMessage());
        }
    }

    public WeixinTemplateMessageResponse sendTemplateMessage(WeixinTemplateMessageRequest request, String openId) {
        Map<String, Object> payload = buildTemplatePayload(request, openId);
        String payloadText;
        try {
            payloadText = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            throw new AppException("WX_0003", "build template message failed");
        }

        if (!officialConfigured()) {
            WeixinTemplateMessageResponse response = new WeixinTemplateMessageResponse();
            response.setSuccess(true);
            response.setMode("MOCK");
            response.setOpenId(openId);
            response.setPayload(payloadText);
            response.setMessage("mock template message recorded");
            return response;
        }

        try {
            JsonNode root = postJson(TEMPLATE_SEND_URL.formatted(accessToken()), payload);
            boolean success = root.path("errcode").asInt(-1) == 0;
            WeixinTemplateMessageResponse response = new WeixinTemplateMessageResponse();
            response.setSuccess(success);
            response.setMode("OFFICIAL");
            response.setOpenId(openId);
            response.setPayload(payloadText);
            response.setMessageId(root.path("msgid").asText(""));
            response.setMessage(success ? "template message sent" : root.toString());
            return response;
        } catch (IOException e) {
            throw new AppException("WX_0004", "send weixin template message failed: " + e.getMessage());
        }
    }

    private Map<String, Object> buildTemplatePayload(WeixinTemplateMessageRequest request, String openId) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (StringUtils.hasText(request.getTitle())) {
            data.put("first", Map.of("value", request.getTitle()));
        }
        if (request.getData() != null) {
            request.getData().forEach((key, value) -> data.put(key, Map.of("value", value == null ? "" : value)));
        }
        if (StringUtils.hasText(request.getRemark())) {
            data.put("remark", Map.of("value", request.getRemark()));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", openId);
        payload.put("template_id", request.getTemplateId());
        if (StringUtils.hasText(request.getTargetUrl())) {
            payload.put("url", request.getTargetUrl());
        }
        payload.put("data", data);
        return payload;
    }

    private String accessToken() throws IOException {
        long now = Instant.now().getEpochSecond();
        if (StringUtils.hasText(cachedAccessToken) && cachedAccessTokenExpireAt > now + 60) {
            return cachedAccessToken;
        }
        String url = ACCESS_TOKEN_URL.formatted(
                URLEncoder.encode(appId, StandardCharsets.UTF_8),
                URLEncoder.encode(appSecret, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        JsonNode root = send(request);
        if (!StringUtils.hasText(root.path("access_token").asText(""))) {
            throw new AppException("WX_0001", "get weixin access token failed: " + root);
        }
        cachedAccessToken = root.path("access_token").asText();
        cachedAccessTokenExpireAt = now + root.path("expires_in").asLong(7200);
        return cachedAccessToken;
    }

    private JsonNode postJson(String url, Map<String, Object> payload) throws IOException {
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("weixin request interrupted", e);
        }
    }

    public record WeixinQrTicket(String ticket, String qrCodeUrl, int expireSeconds) {

        static WeixinQrTicket mock(int expireSeconds) {
            return new WeixinQrTicket("", "", expireSeconds);
        }
    }
}















