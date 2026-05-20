package com.linrun.trigger.http;

import com.linrun.api.knowledgeasset.request.UploadKnowledgeDocumentRequest;
import com.linrun.api.knowledgeasset.response.UploadKnowledgeDocumentResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.KnowledgeDocumentUploadService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/knowledge/document")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentUploadService knowledgeDocumentUploadService;

    public KnowledgeDocumentController(KnowledgeDocumentUploadService knowledgeDocumentUploadService) {
        this.knowledgeDocumentUploadService = knowledgeDocumentUploadService;
    }

    @PostMapping("/upload-text")
    public Response<UploadKnowledgeDocumentResponse> uploadText(@RequestBody UploadKnowledgeDocumentRequest request) {
        return Response.success(knowledgeDocumentUploadService.uploadText(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/upload-file")
    public Response<UploadKnowledgeDocumentResponse> uploadFile(@RequestParam("file") MultipartFile file,
                                                                @RequestParam String goodsId,
                                                                @RequestParam(required = false) String documentName,
                                                                @RequestParam(required = false) String documentType,
                                                                @RequestParam(required = false) String knowledgeVersion) {
        return Response.success(knowledgeDocumentUploadService.uploadFile(
                file,
                goodsId,
                documentName,
                documentType,
                knowledgeVersion), RequestTraceContext.getRequestId());
    }
}
