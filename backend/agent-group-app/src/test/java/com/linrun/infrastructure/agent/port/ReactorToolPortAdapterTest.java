package com.linrun.infrastructure.agent.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicWebFetchPort;
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
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"model\":\"gpt-image-2\""),
                        org.hamcrest.Matchers.containsString("\"quality\":\"auto\""),
                        org.hamcrest.Matchers.containsString("\"aspectRatio\":\"1:1\""),
                        org.hamcrest.Matchers.containsString("\"size\":\"1024x1024\"")
                )))
                .andRespond(withSuccess("""
                        {
                          "data": "图片生成完成",
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
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicImageGenerationPort.AcademicImageGenerationResult result = adapter.generate(
                new AcademicImageGenerationPort.AcademicImageGenerationRequest(
                        "生成海报", "images", "1024x1024", 1, List.of(), List.of()));

        assertTrue(result.success());
        assertEquals("图片生成完成", result.summary());
        assertEquals("result.png", result.fileRefs().getFirst().getFileName());
        server.verify();
    }

    @Test
    void shouldForwardImageModelConnectionConfig() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/image_generation"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"model\":\"custom-image-model\""),
                        org.hamcrest.Matchers.containsString("\"baseUrl\":\"https://image.example.com/v1\""),
                        org.hamcrest.Matchers.containsString("\"apiKey\":\"sk-image-secret\""),
                        org.hamcrest.Matchers.containsString("\"quality\":\"high\""),
                        org.hamcrest.Matchers.containsString("\"aspectRatio\":\"16:9\"")
                )))
                .andRespond(withSuccess("""
                        {
                          "data": "done",
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
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicImageGenerationPort.AcademicImageGenerationResult result = adapter.generate(
                new AcademicImageGenerationPort.AcademicImageGenerationRequest(
                        "draw diagram", "images", "1024x1024", 1, List.of(), List.of(),
                        "custom-image-model", "high", "16:9",
                        "https://image.example.com/v1", "sk-image-secret"));

        assertTrue(result.success());
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
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult result = adapter.analyze(
                new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisRequest(
                        "分析图片", "", List.of("https://img.example.com/a.png"), List.of()));

        assertTrue(result.success());
        assertEquals("命中图文片段。生成最终回答。", result.content());
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
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicCodeInterpreterPort.AcademicCodeExecutionResult result = adapter.execute(
                new AcademicCodeInterpreterPort.AcademicCodeExecutionRequest(
                        "计算均值", "python", "print(20)", List.of("sales.csv"), "workspace"));

        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("执行完成，均值为 20", result.stdout());
        assertEquals("code_output.md", result.fileRefs().getFirst().getFileName());
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

                        data: {"requestId":"req-deep","query":"Agent 项目","answer":"","isFinal":true,"messageType":"report"}

                        data: [DONE]
                        """, MediaType.TEXT_EVENT_STREAM));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicDeepSearchPort.AcademicDeepSearchResult result = adapter.search(
                new AcademicDeepSearchPort.AcademicDeepSearchRequest(
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
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicFileToolPort.AcademicFileToolResult result = adapter.upload(
                new AcademicFileToolPort.AcademicFileUploadRequest(
                        "req-file", "report.md", "报告", "# report", "text/markdown", false));

        assertTrue(result.success());
        assertEquals("report.md", result.fileRefs().getFirst().getFileName());
        assertEquals("https://file.example.com/preview/report.md", result.fileRefs().getFirst().getPreviewUrl());
        server.verify();
    }

    @Test
    void shouldMapFileGetResponseWithoutCodeField() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/file_tool/get_file"))
                .andRespond(withSuccess("""
                        {
                          "ossUrl": "https://file.example.com/download/report.md",
                          "downloadUrl": "https://file.example.com/download/report.md",
                          "domainUrl": "https://file.example.com/preview/report.md",
                          "requestId": "req-file",
                          "fileName": "report.md"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicFileToolPort.AcademicFileToolResult result = adapter.get(
                new AcademicFileToolPort.AcademicFileGetRequest("req-file", "report.md", 4000));

        assertTrue(result.success());
        assertEquals("report.md", result.fileRefs().getFirst().getFileName());
        assertEquals("https://file.example.com/download/report.md", result.fileRefs().getFirst().getDownloadUrl());
        server.verify();
    }

    @Test
    void shouldMapScriptRunnerResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/script_runner"))
                .andRespond(withSuccess("""
                        {
                          "requestId": "req-script",
                          "skillName": "sql-analysis",
                          "scriptName": "summarize",
                          "runtime": "python",
                          "success": true,
                          "exitCode": 0,
                          "stdout": "ok",
                          "stderr": "",
                          "summary": "脚本执行成功",
                          "fileInfo": [
                            {
                              "fileName": "summary.md",
                              "downloadUrl": "https://file.example.com/summary.md"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicScriptRunnerPort.AcademicScriptRunResult result = adapter.run(
                new AcademicScriptRunnerPort.AcademicScriptRunRequest(
                        "req-script", "sql-analysis", "skills/sql-analysis", "summarize",
                        "scripts/summarize.py", "python", Map.of("table", "experiment_result"), List.of(), 30));

        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertEquals("脚本执行成功", result.summary());
        assertEquals("summary.md", result.fileRefs().getFirst().getFileName());
        server.verify();
    }

    @Test
    void shouldMapTableRagResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/table_rag"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": {
                            "experiment_result": [
                              {"name": "experiment_id", "type": "string"},
                              {"name": "metric_value", "type": "decimal"}
                            ]
                          },
                          "requestId": "req-table"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicTableRagPort.AcademicTableRagResult result = adapter.recall(
                new AcademicTableRagPort.AcademicTableRagRequest(
                        "req-table", "查询实验指标", List.of("experiment_result"), "only_recall", true, false, 5));

        assertTrue(result.success());
        assertEquals(1, result.matches().size());
        assertEquals("experiment_result", result.matches().getFirst().schemaList().getFirst().get("name"));
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
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicNl2SqlPort.AcademicNl2SqlResult result = adapter.convert(
                new AcademicNl2SqlPort.AcademicNl2SqlRequest(
                        "req-sql", "查询实验准确率", List.of("experiment_result"), List.of(),
                        "2026-06-05", "mysql", false, true, false));

        assertTrue(result.success());
        assertEquals("data", result.status());
        assertEquals("select * from experiment_result where metric_name = 'accuracy'",
                result.candidates().getFirst().sql());
        server.verify();
    }

    @Test
    void shouldMapRemoteDataAnalysisResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/auto_analysis"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": "实验准确率均值为 92.4%",
                          "request_id": "req-data"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicDataAnalysisPort.AcademicDataAnalysisResult result = adapter.analyze(
                new AcademicDataAnalysisPort.AcademicDataAnalysisRequest(
                        "req-data", "分析实验指标", List.of(), List.of(),
                        List.of("experiment_result"), "关注准确率", 5, false));

        assertTrue(result.success());
        assertEquals("实验准确率均值为 92.4%", result.content());
        server.verify();
    }

    @Test
    void shouldMapRemoteReportResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://127.0.0.1:1601/v1/tool/report"))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": "# 论文实验报告",
                          "fileInfo": [
                            {
                              "fileName": "experiment-report.md",
                              "downloadUrl": "https://file.example.com/experiment-report.md"
                            }
                          ],
                          "requestId": "req-report"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicReportPort.AcademicReportResult result = adapter.generate(
                new AcademicReportPort.AcademicReportRequest(
                        "req-report", "生成论文实验报告", "论文实验报告", "",
                        List.of(), List.of(), List.of(), "experiment-report.md", "markdown", "html", false));

        assertTrue(result.success());
        assertEquals("# 论文实验报告", result.content());
        assertEquals("experiment-report.md", result.fileRefs().getFirst().getFileName());
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
                            "content": "这是网页正文片段",
                            "contentFormat": "markdown",
                            "wordCount": 32,
                            "truncated": false,
                            "contentSource": "html",
                            "metadata": {
                              "statusCode": 200
                            }
                          },
                          "fileInfo": [
                            {
                              "fileName": "Agent项目说明.md",
                              "downloadUrl": "https://file.example.com/download/web.md",
                              "domainUrl": "https://file.example.com/preview/web.md"
                            }
                          ],
                          "requestId": "req-web"
                        }
                        """, MediaType.APPLICATION_JSON));
        ReactorToolPortAdapter adapter = new ReactorToolPortAdapter(
                restTemplate, new ObjectMapper(), "http://127.0.0.1:1601", "");

        AcademicWebFetchPort.AcademicWebFetchResult result = adapter.fetch(
                new AcademicWebFetchPort.AcademicWebFetchRequest(
                        "req-web", "https://example.com/agent", 30, 4000));

        assertTrue(result.success());
        assertEquals("Agent 项目说明", result.title());
        assertEquals("https://example.com/agent", result.finalUrl());
        assertEquals("这是网页正文片段", result.content());
        assertEquals("Agent项目说明.md", result.fileRefs().getFirst().getFileName());
        server.verify();
    }
}
