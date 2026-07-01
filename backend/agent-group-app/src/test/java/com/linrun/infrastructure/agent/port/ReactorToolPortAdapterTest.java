package com.linrun.infrastructure.agent.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.runtime.tool.port.AgentCodeInterpreterPort;
import com.linrun.domain.agent.runtime.tool.port.AgentDeepSearchPort;
import com.linrun.domain.agent.runtime.tool.port.AgentFileToolPort;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.domain.agent.runtime.tool.port.AgentMultimodalAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.domain.agent.runtime.tool.port.AgentWebFetchPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReactorToolPortAdapterTest {

    @Test
    void shouldMapImageGenerationResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/image_generation"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"gpt-image-2\"")))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": "已生成图片",
                          "fileInfo": [
                            {
                              "fileName": "result.png",
                              "ossUrl": "https://file.example.com/download/result.png",
                              "domainUrl": "https://file.example.com/preview/result.png",
                              "fileSize": 128
                            }
                          ],
                          "requestId": "req-image"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentImageGenerationPort.AgentImageGenerationResult result = adapter.generate(
                new AgentImageGenerationPort.AgentImageGenerationRequest(
                        "生成活动主图", "generate", "1024x1024", 1, List.of(), List.of()));

        assertTrue(result.success());
        assertEquals("已生成图片", result.summary());
        assertEquals("result.png", result.fileRefs().getFirst().getFileName());
        server.verify();
    }

    @Test
    void shouldMapCodeInterpreterResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/code_interpreter"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"permissionProfile\":\"workspace\"")))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": "执行完成，均值为 20",
                          "fileInfo": [
                            {
                              "fileName": "code_output.md",
                              "downloadUrl": "https://file.example.com/download/code_output.md"
                            }
                          ],
                          "requestId": "req-code"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentCodeInterpreterPort.AgentCodeExecutionResult result = adapter.execute(
                new AgentCodeInterpreterPort.AgentCodeExecutionRequest(
                        "计算均值", "python", "print(20)", List.of("sales.csv"), "workspace"));

        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("执行完成，均值为 20", result.stdout());
        assertEquals("code_output.md", result.fileRefs().getFirst().getFileName());
        server.verify();
    }

    @Test
    void shouldMergeSseChunksForMultimodalAnalysis() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/mragQuery"))
                .andRespond(withSuccess("""
                        data: {"choices":[{"delta":{"content":"命中图文片段。"},"index":0}]}

                        data: {"choices":[{"delta":{"content":"生成最终回答。"},"index":0}]}

                        data: [DONE]
                        """, MediaType.TEXT_EVENT_STREAM));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentMultimodalAnalysisPort.AgentMultimodalAnalysisResult result = adapter.analyze(
                new AgentMultimodalAnalysisPort.AgentMultimodalAnalysisRequest(
                        "分析图片", "", List.of("https://img.example.com/a.png"), List.of()));

        assertTrue(result.success());
        assertEquals("命中图文片段。生成最终回答。", result.content());
        server.verify();
    }

    @Test
    void shouldMapDeepSearchSseAnswerAndDocuments() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/deepsearch"))
                .andRespond(withSuccess("""
                        data: {"requestId":"req-deep","query":"Agent 项目","searchResult":{"query":["Agent 项目亮点"],"docs":[[{"title":"项目说明","url":"https://example.com/a","content":"多智能体能力","source":"ddg"}]]},"isFinal":false,"messageType":"search"}

                        data: {"requestId":"req-deep","query":"Agent 项目","answer":"第一段","isFinal":false,"messageType":"report"}

                        data: {"requestId":"req-deep","query":"Agent 项目","answer":"第二段","isFinal":false,"messageType":"report"}

                        data: [DONE]
                        """, MediaType.TEXT_EVENT_STREAM));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentDeepSearchPort.AgentDeepSearchResult result = adapter.search(
                new AgentDeepSearchPort.AgentDeepSearchRequest(
                        "Agent 项目", 2, true, List.of("ddg"), Map.of()));

        assertTrue(result.success());
        assertEquals("第一段第二段", result.answer());
        assertEquals(List.of("Agent 项目亮点"), result.subQueries());
        assertEquals("项目说明", result.documents().getFirst().title());
        server.verify();
    }

    @Test
    void shouldMapFileUploadResponseWithoutCodeField() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/file_tool/upload_file"))
                .andRespond(withSuccess("""
                        {
                          "ossUrl": "https://file.example.com/download/report.md",
                          "domainUrl": "https://file.example.com/preview/report.md",
                          "fileSize": 64
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentFileToolPort.AgentFileToolResult result = adapter.upload(
                new AgentFileToolPort.AgentFileUploadRequest(
                        "req-file", "report.md", "报告", "# report", "text/markdown", false));

        assertTrue(result.success());
        assertEquals("report.md", result.fileRefs().getFirst().getFileName());
        assertEquals("https://file.example.com/preview/report.md", result.fileRefs().getFirst().getPreviewUrl());
        server.verify();
    }

    @Test
    void shouldMapNl2SqlResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/nl2sql"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": [
                            {
                              "query": "查询实验准确率",
                              "nl2sql": "select * from experiment_result where metric_name = 'accuracy'"
                            }
                          ],
                          "request_id": "req-sql",
                          "status": "data",
                          "error_msg": ""
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentNl2SqlPort.AgentNl2SqlResult result = adapter.convert(
                new AgentNl2SqlPort.AgentNl2SqlRequest(
                        "req-sql", "查询实验准确率", List.of("experiment_result"), List.of(),
                        "2026-06-05", "mysql", false, true, false));

        assertTrue(result.success());
        assertEquals("data", result.status());
        assertEquals("select * from experiment_result where metric_name = 'accuracy'",
                result.candidates().getFirst().sql());
        server.verify();
    }

    @Test
    void shouldMapRemoteWebFetchResponseAndFileArtifact() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/web_fetch"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": {
                            "title": "Agent 项目说明",
                            "finalUrl": "https://example.com/agent",
                            "content": "多智能体能力",
                            "contentFormat": "markdown",
                            "wordCount": 32,
                            "truncated": false
                          },
                          "fileInfo": [
                            {
                              "fileName": "agent.md",
                              "downloadUrl": "https://file.example.com/agent.md"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = adapter(restTemplate);

        AgentWebFetchPort.AgentWebFetchResult result = adapter.fetch(
                new AgentWebFetchPort.AgentWebFetchRequest("req-web", "https://example.com/agent", 30, 4000));

        assertTrue(result.success());
        assertEquals("Agent 项目说明", result.title());
        assertEquals("多智能体能力", result.content());
        assertEquals("agent.md", result.fileRefs().getFirst().getFileName());
        server.verify();
    }

    private ReactorToolPortAdapter adapter(RestTemplate restTemplate) {
        return new ReactorToolPortAdapter(restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");
    }
}
