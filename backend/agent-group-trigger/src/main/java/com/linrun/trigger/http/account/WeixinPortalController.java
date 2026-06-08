package com.linrun.trigger.http.account;

import com.linrun.api.dto.WeixinLoginQrRequest;
import com.linrun.api.dto.WeixinLoginQrResponse;
import com.linrun.api.dto.WeixinLoginStatusResponse;
import com.linrun.api.dto.WeixinSimulateScanRequest;
import com.linrun.api.dto.WeixinTemplateMessageRequest;
import com.linrun.api.dto.WeixinTemplateMessageResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/weixin")
public class WeixinPortalController {

    private final WeixinPortalHandler weixinPortalHandler;

    public WeixinPortalController(WeixinPortalHandler weixinPortalHandler) {
        this.weixinPortalHandler = weixinPortalHandler;
    }

    @GetMapping(value = "/portal", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verifyPortal(@RequestParam(required = false) String signature,
                               @RequestParam(required = false) String timestamp,
                               @RequestParam(required = false) String nonce,
                               @RequestParam(required = false) String echostr) {
        return weixinPortalHandler.verifyPortal(signature, timestamp, nonce, echostr);
    }

    @PostMapping(value = "/portal", consumes = MediaType.ALL_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public String receivePortalMessage(@RequestBody(required = false) String xmlBody) {
        return weixinPortalHandler.receivePortalMessage(xmlBody);
    }

    @PostMapping("/login/qr")
    public Response<WeixinLoginQrResponse> createLoginQr(@RequestBody(required = false) WeixinLoginQrRequest request) {
        return Response.success(weixinPortalHandler.createLoginQr(request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/login/status")
    public Response<WeixinLoginStatusResponse> queryLoginStatus(@RequestParam String sceneId) {
        return Response.success(weixinPortalHandler.queryLoginStatus(sceneId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/login/simulate")
    public Response<WeixinLoginStatusResponse> simulateScan(@RequestBody WeixinSimulateScanRequest request) {
        return Response.success(weixinPortalHandler.simulateScan(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/template/send")
    public Response<WeixinTemplateMessageResponse> sendTemplateMessage(@RequestBody WeixinTemplateMessageRequest request) {
        return Response.success(weixinPortalHandler.sendTemplateMessage(request), RequestTraceContext.getRequestId());
    }
}
