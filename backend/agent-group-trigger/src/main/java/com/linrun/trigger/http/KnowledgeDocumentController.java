package com.linrun.trigger.http;

import com.linrun.api.dto.KnowledgeDocumentDTO;
import com.linrun.api.dto.KnowledgeFragmentDTO;
import com.linrun.api.dto.UploadKnowledgeDocumentRequest;
import com.linrun.api.dto.UploadKnowledgeDocumentResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.http.KnowledgeDocumentUploadHandler;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/knowledge/document")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentUploadHandler knowledgeDocumentUploadService;
    private final KnowledgeDocumentAdminHandler knowledgeDocumentAdminHandler;

    public KnowledgeDocumentController(KnowledgeDocumentUploadHandler knowledgeDocumentUploadService,
                                       KnowledgeDocumentAdminHandler knowledgeDocumentAdminHandler) {
        this.knowledgeDocumentUploadService = knowledgeDocumentUploadService;
        this.knowledgeDocumentAdminHandler = knowledgeDocumentAdminHandler;
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

    @GetMapping("/list")
    public Response<List<KnowledgeDocumentDTO>> list(@RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer limit) {
        return Response.success(knowledgeDocumentAdminHandler.queryDocuments(status, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/fragments")
    public Response<List<KnowledgeFragmentDTO>> fragments(@RequestParam String documentId) {
        return Response.success(knowledgeDocumentAdminHandler.queryFragments(documentId), RequestTraceContext.getRequestId());
    }
}
