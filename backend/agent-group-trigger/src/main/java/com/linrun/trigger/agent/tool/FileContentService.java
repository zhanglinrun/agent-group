package com.linrun.trigger.agent.tool;

import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.service.EmbeddingService;
import com.linrun.trigger.agent.service.FileManageService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件内容服务工具
 * 合并了文件加载和RAG检索功??
 * 根据文件??embed 字段自动选择合适的加载方式
 */
@Service
@Slf4j
public class FileContentService {

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private FileManageService fileManageService;

    /**
     * 加载文件内容或进行RAG检??
     * 根据文件??embed 字段自动选择合适的加载方式??
     * - embed=1: 使用RAG语义检索（适用于大文件??
     * - embed=0 ??null: 直接加载完整文件内容（适用于小文件??
     *
     * @param fileId   文件ID
     * @param question 用户问题（用于RAG检索）
     * @return 文件信息或检索结??
     */
    @Tool(description = "根据文件ID加载文件内容或进行RAG语义检索。如果文件已向量化则使用语义搜索返回相关片段，否则直接返回完整文件内容。")
    public String loadContent(
            @ToolParam(description = "文件ID") String fileId,
            @ToolParam(description = "用户的问题，用于语义检索（可选）") String question) {
        log.info("EXECUTE Tool: loadContent: fileId={}, question={}", fileId, question);

        if (fileId == null || fileId.trim().isEmpty()) {
            return JSON.toJSONString(errorPayload("", "文件ID不能为空"));
        }

        List<String> fileIds = splitFileIds(fileId);
        if (fileIds.size() > 1) {
            return JSON.toJSONString(loadMultiple(fileIds, question));
        }

        try {
            return JSON.toJSONString(loadSingle(fileId, question));
        } catch (IllegalArgumentException e) {
            return JSON.toJSONString(errorPayload(fileId, e.getMessage()));
        } catch (Exception e) {
            log.error("加载文件内容失败: fileId={}, question={}", fileId, question, e);
            return JSON.toJSONString(errorPayload(fileId, "加载文件内容失败: " + e.getMessage()));
        }
    }

    private List<String> splitFileIds(String fileIds) {
        if (fileIds == null || fileIds.trim().isEmpty()) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>();
        for (String fileId : fileIds.split("[,，\\s]+")) {
            String trimmed = fileId == null ? "" : fileId.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private Map<String, Object> loadMultiple(List<String> fileIds, String question) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (String item : fileIds) {
            try {
                files.add(loadSingle(item, question));
            } catch (Exception e) {
                files.add(errorPayload(item, e.getMessage()));
            }
        }
        Map<String, Object> payload = basePayload(String.join(",", fileIds), null, "multi");
        payload.put("success", files.stream().anyMatch(file -> Boolean.TRUE.equals(file.get("success"))));
        payload.put("summary", "已读取多个附件：" + fileIds.size() + " 个");
        payload.put("hitCount", files.stream()
                .mapToInt(file -> ((Number) file.getOrDefault("hitCount", 0)).intValue())
                .sum());
        payload.put("files", files);
        payload.put("fileRefs", files.stream()
                .flatMap(file -> ((List<?>) file.getOrDefault("fileRefs", List.of())).stream())
                .toList());
        payload.put("content", files.stream()
                .map(file -> String.valueOf(file.getOrDefault("content", "")))
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(""));
        return payload;
    }

    private Map<String, Object> loadSingle(String fileId, String question) {
        FileInfo fileInfo = fileManageService.getFileInfo(fileId);
        if (fileInfo == null) {
            return errorPayload(fileId, "文件不存在，文件ID: " + fileId);
        }

        if (fileInfo.getStatus() != FileInfo.FileStatus.SUCCESS) {
            Map<String, Object> payload = basePayload(fileId, fileInfo, "unavailable");
            payload.put("success", false);
            payload.put("summary", String.format("文件处理中或处理失败，当前状态：%s", fileInfo.getStatus()));
            payload.put("content", "");
            payload.put("segments", List.of());
            payload.put("hitCount", 0);
            return payload;
        }

        Integer embed = fileInfo.getEmbed();
        if (embed != null && embed == 1) {
            return retrieveWithRAG(fileId, fileInfo, question);
        }
        return loadDirectly(fileId, fileInfo);
    }

    /**
     * 使用 RAG 语义检索方式加载文件内容。
     */
    private Map<String, Object> retrieveWithRAG(String fileId, FileInfo fileInfo, String question) {
        Map<String, Object> payload = basePayload(fileId, fileInfo, "rag");
        if (question == null || question.trim().isEmpty()) {
            payload.put("success", false);
            payload.put("summary", "请提供具体问题以进行语义检索");
            payload.put("content", "");
            payload.put("segments", List.of());
            payload.put("hitCount", 0);
            return payload;
        }

        EmbeddingService.RagRetrievalResult result = embeddingService.ragRetrieveDetailed(fileId, question);
        List<Map<String, Object>> segments = result.hits().stream()
                .map(this::segmentPayload)
                .toList();
        String content = result.hits().stream()
                .map(EmbeddingService.RagHit::content)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(result.message());

        payload.put("success", result.success());
        payload.put("query", result.originalQuestion());
        payload.put("compressedQuery", result.compressedQuestion());
        payload.put("expandedQueries", result.expandedQueries());
        payload.put("summary", result.message());
        payload.put("content", content);
        payload.put("segments", segments);
        payload.put("hitCount", result.hitCount());
        return payload;
    }

    /**
     * 直接加载完整文件内容
     */
    private Map<String, Object> loadDirectly(String fileId, FileInfo fileInfo) {
        String content = fileManageService.getFileContent(fileId);
        String contentText = (content != null && !content.trim().isEmpty()) ? content : "该文件没有可识别的内容";

        Map<String, Object> payload = basePayload(fileId, fileInfo, "full_text");
        payload.put("success", true);
        payload.put("summary", "全文读取：" + fileInfo.getFileName());
        payload.put("content", contentText);
        payload.put("segments", List.of());
        payload.put("hitCount", 0);
        return payload;
    }

    private Map<String, Object> basePayload(String fileId, FileInfo fileInfo, String mode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", "file_tool");
        payload.put("mode", mode);
        payload.put("fileId", fileId);
        payload.put("fileName", fileInfo == null ? "" : fileInfo.getFileName());
        payload.put("fileType", fileInfo == null ? "" : fileInfo.getFileType());
        payload.put("fileSize", fileInfo == null ? 0L : fileInfo.getFileSize());
        payload.put("fileStatus", fileInfo == null || fileInfo.getStatus() == null ? "" : fileInfo.getStatus().name());
        payload.put("fileRefs", fileInfo == null ? List.of() : List.of(fileRef(fileInfo)));
        return payload;
    }

    private Map<String, Object> errorPayload(String fileId, String message) {
        Map<String, Object> payload = basePayload(fileId, null, "error");
        payload.put("success", false);
        payload.put("summary", message == null ? "文件读取失败" : message);
        payload.put("content", "");
        payload.put("segments", List.of());
        payload.put("hitCount", 0);
        return payload;
    }

    private Map<String, Object> segmentPayload(EmbeddingService.RagHit hit) {
        Map<String, Object> segment = new LinkedHashMap<>();
        segment.put("rank", hit.rank());
        segment.put("documentId", hit.documentId());
        segment.put("content", hit.content());
        segment.put("metadata", hit.metadata() == null ? Map.of() : hit.metadata());
        return segment;
    }

    private Map<String, Object> fileRef(FileInfo fileInfo) {
        Map<String, Object> fileRef = new LinkedHashMap<>();
        fileRef.put("fileId", fileInfo.getFileId());
        fileRef.put("fileName", fileInfo.getFileName());
        fileRef.put("fileType", fileInfo.getFileType());
        fileRef.put("fileSize", fileInfo.getFileSize());
        return fileRef;
    }
}















