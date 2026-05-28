package com.linrun.trigger.http;

import com.linrun.api.dto.WeixinLoginQrRequest;
import com.linrun.api.dto.WeixinLoginQrResponse;
import com.linrun.api.dto.WeixinLoginStatusResponse;
import com.linrun.api.dto.WeixinSimulateScanRequest;
import com.linrun.api.dto.WeixinTemplateMessageRequest;
import com.linrun.api.dto.WeixinTemplateMessageResponse;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeixinPortalHandler {

    private static final String STATUS_WAITING = "WAITING_SCAN";
    private static final String STATUS_SCANNED = "SCANNED";
    private static final String STATUS_EXPIRED = "EXPIRED";

    private final Map<String, WeixinLoginSession> loginSessions = new ConcurrentHashMap<>();
    private final WeixinOfficialAccountClient weixinClient;
    private final String callbackBaseUrl;

    public WeixinPortalHandler(WeixinOfficialAccountClient weixinClient,
                               @Value("${agent.group.wechat.official.callback-base-url:http://localhost:8080}") String callbackBaseUrl) {
        this.weixinClient = weixinClient;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String verifyPortal(String signature, String timestamp, String nonce, String echostr) {
        if (!StringUtils.hasText(echostr)) {
            return "success";
        }
        if (!StringUtils.hasText(weixinClient.token()) || validateSignature(signature, timestamp, nonce)) {
            return echostr;
        }
        throw new AppException("WX_0005", "weixin signature invalid");
    }

    public String receivePortalMessage(String xmlBody) {
        if (!StringUtils.hasText(xmlBody)) {
            return "success";
        }
        Map<String, String> message = parseXml(xmlBody);
        String event = message.getOrDefault("Event", "");
        if ("SCAN".equalsIgnoreCase(event) || "subscribe".equalsIgnoreCase(event)) {
            String sceneId = normalizeSceneId(message.get("EventKey"));
            if (StringUtils.hasText(sceneId)) {
                markScanned(sceneId, message.get("FromUserName"), null, "微信用户");
            }
        }
        return "success";
    }

    public WeixinLoginQrResponse createLoginQr(WeixinLoginQrRequest request) {
        String sceneId = "AG_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        WeixinOfficialAccountClient.WeixinQrTicket ticket = weixinClient.createTemporaryQrCode(sceneId);
        WeixinLoginSession session = new WeixinLoginSession();
        session.sceneId = sceneId;
        session.userId = request == null ? "" : request.getUserId();
        session.status = STATUS_WAITING;
        session.expireTime = LocalDateTime.now().plusSeconds(ticket.expireSeconds());
        loginSessions.put(sceneId, session);

        WeixinLoginQrResponse response = new WeixinLoginQrResponse();
        response.setSceneId(sceneId);
        response.setTicket(ticket.ticket());
        response.setQrCodeUrl(ticket.qrCodeUrl());
        response.setLocalScanUrl(callbackBaseUrl + "/api/v1/weixin/login/simulate");
        response.setExpireSeconds(ticket.expireSeconds());
        response.setExpireTime(session.expireTime);
        response.setStatus(session.status);
        response.setOfficialConfigured(weixinClient.officialConfigured());
        return response;
    }

    public WeixinLoginStatusResponse queryLoginStatus(String sceneId) {
        WeixinLoginSession session = findSession(sceneId);
        if (LocalDateTime.now().isAfter(session.expireTime) && !STATUS_SCANNED.equals(session.status)) {
            session.status = STATUS_EXPIRED;
        }
        return toResponse(session);
    }

    public WeixinLoginStatusResponse simulateScan(WeixinSimulateScanRequest request) {
        if (request == null || !StringUtils.hasText(request.getSceneId())) {
            throw new AppException("WX_0006", "sceneId cannot be blank");
        }
        return toResponse(markScanned(request.getSceneId(), request.getOpenId(), request.getUserId(), request.getNickname()));
    }

    public WeixinTemplateMessageResponse sendTemplateMessage(WeixinTemplateMessageRequest request) {
        if (request == null) {
            throw new AppException("WX_0007", "template request cannot be null");
        }
        if (!StringUtils.hasText(request.getTemplateId())) {
            throw new AppException("WX_0008", "templateId cannot be blank");
        }
        String openId = resolveOpenId(request);
        if (!StringUtils.hasText(openId)) {
            throw new AppException("WX_0009", "openId cannot be blank");
        }
        return weixinClient.sendTemplateMessage(request, openId);
    }

    private WeixinLoginSession markScanned(String sceneId, String openId, String userId, String nickname) {
        WeixinLoginSession session = findSession(sceneId);
        session.status = STATUS_SCANNED;
        session.openId = StringUtils.hasText(openId) ? openId : "mock_openid_" + sceneId.substring(Math.max(0, sceneId.length() - 8));
        session.userId = StringUtils.hasText(userId)
                ? userId
                : (StringUtils.hasText(session.userId) ? session.userId : "WX_" + session.openId.substring(Math.max(0, session.openId.length() - 8)));
        session.nickname = StringUtils.hasText(nickname) ? nickname : "微信用户";
        session.scanTime = LocalDateTime.now();
        return session;
    }

    private WeixinLoginSession findSession(String sceneId) {
        if (!StringUtils.hasText(sceneId)) {
            throw new AppException("WX_0006", "sceneId cannot be blank");
        }
        WeixinLoginSession session = loginSessions.get(sceneId);
        if (session == null) {
            throw new AppException("WX_0010", "login scene not found");
        }
        return session;
    }

    private String resolveOpenId(WeixinTemplateMessageRequest request) {
        if (StringUtils.hasText(request.getOpenId())) {
            return request.getOpenId();
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "";
        }
        Optional<WeixinLoginSession> session = loginSessions.values().stream()
                .filter(item -> request.getUserId().equals(item.userId))
                .filter(item -> StringUtils.hasText(item.openId))
                .findFirst();
        return session.map(item -> item.openId).orElse("");
    }

    private boolean validateSignature(String signature, String timestamp, String nonce) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            return false;
        }
        String[] values = {weixinClient.token(), timestamp, nonce};
        Arrays.sort(values);
        return signature.equals(sha1(String.join("", values)));
    }

    private String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new AppException("WX_0011", "sha1 digest failed");
        }
    }

    private Map<String, String> parseXml(String xmlBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlBody)));
            return Map.of(
                    "FromUserName", text(document, "FromUserName"),
                    "Event", text(document, "Event"),
                    "EventKey", text(document, "EventKey")
            );
        } catch (Exception e) {
            throw new AppException("WX_0012", "parse weixin xml failed");
        }
    }

    private String text(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
    }

    private String normalizeSceneId(String eventKey) {
        if (!StringUtils.hasText(eventKey)) {
            return "";
        }
        return eventKey.startsWith("qrscene_") ? eventKey.substring("qrscene_".length()) : eventKey;
    }

    private WeixinLoginStatusResponse toResponse(WeixinLoginSession session) {
        WeixinLoginStatusResponse response = new WeixinLoginStatusResponse();
        response.setSceneId(session.sceneId);
        response.setStatus(session.status);
        response.setUserId(session.userId);
        response.setOpenId(session.openId);
        response.setNickname(session.nickname);
        response.setExpireTime(session.expireTime);
        response.setScanTime(session.scanTime);
        return response;
    }

    private static class WeixinLoginSession {
        private String sceneId;
        private String status;
        private String userId;
        private String openId;
        private String nickname;
        private LocalDateTime expireTime;
        private LocalDateTime scanTime;
    }
}
