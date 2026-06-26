package com.linrun.trigger.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.file.adapter.EmbeddingPort;
import com.linrun.domain.agent.file.model.RagHit;
import com.linrun.domain.agent.file.model.RagRetrievalResult;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.service.FileManageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadContentReturnsStructuredRagEvidence() throws Exception {
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        FileManageService fileManageService = mock(FileManageService.class);
        FileContentService service = new FileContentService();
        ReflectionTestUtils.setField(service, "embeddingPort", embeddingPort);
        ReflectionTestUtils.setField(service, "fileManageService", fileManageService);

        FileInfo fileInfo = FileInfo.builder()
                .fileId("file-1")
                .fileName("paper.pdf")
                .fileType("pdf")
                .fileSize(128L)
                .status(FileInfo.FileStatus.SUCCESS)
                .embed(1)
                .build();
        when(fileManageService.getFileInfo("file-1")).thenReturn(fileInfo);
        when(embeddingPort.ragRetrieve("file-1", "研究问题"))
                .thenReturn(new RagRetrievalResult(
                        true,
                        "rag",
                        "研究问题",
                        "研究问题",
                        List.of("研究问题"),
                        1,
                        "RAG检索命中 1 段",
                        List.of(new RagHit(
                                1,
                                "doc-1",
                                "命中片段",
                                Map.of("chunkId", 0)))));

        Map<String, Object> payload = objectMapper.readValue(
                service.loadContent("file-1", "研究问题"),
                new TypeReference<>() {
                });

        assertEquals("file_tool", payload.get("toolName"));
        assertEquals("rag", payload.get("mode"));
        assertEquals("paper.pdf", payload.get("fileName"));
        assertEquals(1, ((Number) payload.get("hitCount")).intValue());
        assertTrue((Boolean) payload.get("success"));
        assertEquals(1, ((List<?>) payload.get("segments")).size());
    }
}

