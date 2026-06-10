package com.linrun.infrastructure.agent.port;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.StringHttpMessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "agent.group.reactor-tool", name = "enabled", havingValue = "true")
public class ReactorToolPortAdapter implements AcademicCodeInterpreterPort,
        AcademicWebFetchPort,
        AcademicDataAnalysisPort,
        AcademicReportPort,
        AcademicImageGenerationPort,
        AcademicMultimodalAnalysisPort,
        AcademicDeepSearchPort,
        AcademicFileToolPort,
        AcademicScriptRunnerPort,
        AcademicTableRagPort,
        AcademicNl2SqlPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    @Autowired
    public ReactorToolPortAdapter(ObjectMapper objectMapper,
                                  @Value("${agent.group.reactor-tool.base-url:}") String baseUrl,
                                  @Value("${agent.group.reactor-tool.api-key:}") String apiKey) {
        this(new RestTemplate(), objectMapper, baseUrl, apiKey);
    }

    ReactorToolPortAdapter(RestTemplate restTemplate,
                           ObjectMapper objectMapper,
                           String baseUrl,
                           String apiKey) {
        this.restTemplate = restTemplate == null ? new RestTemplate() : restTemplate;
        this.restTemplate.getMessageConverters().stream()
                .filter(StringHttpMessageConverter.class::isInstance)
                .map(StringHttpMessageConverter.class::cast)
                .forEach(converter -> converter.setDefaultCharset(StandardCharsets.UTF_8));
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.baseUrl = trimSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public AcademicCodeExecutionResult execute(AcademicCodeExecutionRequest request) {
        String requestId = nextRequestId("code");
        String task = request == null ? "" : text(request.task());
        String code = request == null ? "" : text(request.code());
        String permissionProfile = request == null
                ? AcademicCodeInterpreterPort.PERMISSION_PROFILE_ANALYSIS
                : AcademicCodeInterpreterPort.normalizePermissionProfile(request.permissionProfile());
        ReactorToolResponse response = post("/v1/tool/code_interpreter", mapOf(
                "requestId", requestId,
                "task", StringUtils.hasText(code) ? task + "\n\n```" + text(request.language()) + "\n" + code + "\n```" : task,
                "fileNames", request == null ? List.of() : safeList(request.fileNames()),
                "stream", false,
                "permissionProfile", permissionProfile
        ));
        if (!response.success()) {
            return new AcademicCodeExecutionResult(false, -1, "", response.errorMessage(), "",
                    code, "", List.of());
        }
        String content = text(response.body().get("data"));
        return new AcademicCodeExecutionResult(ok(response.body()), ok(response.body()) ? 0 : -1,
                content, errorMessage(response.body()), content, code, content, fileRefs(response.body().get("fileInfo")));
    }

    @Override
    public AcademicDataAnalysisResult analyze(AcademicDataAnalysisRequest request) {
        String requestId = request == null || !StringUtils.hasText(request.requestId())
                ? nextRequestId("data")
                : text(request.requestId());
        ReactorToolResponse response = post("/v1/tool/auto_analysis", mapOf(
                "request_id", requestId,
                "task", request == null ? "" : text(request.task()),
                "modelCodeList", request == null ? List.of() : safeList(request.modelCodeList()),
                "businessKnowledge", request == null ? "" : text(request.businessKnowledge()),
                "max_steps", request == null ? 10 : Math.max(1, request.maxSteps()),
                "stream", false
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicDataAnalysisResult(false, "", "", List.of(), response.body(),
                    firstText(response.errorMessage(), errorMessage(response.body())));
        }
        String content = firstText(response.body().get("data"), response.body().get("message"));
        return new AcademicDataAnalysisResult(true,
                content,
                limit(content, 240),
                fileRefs(response.body().get("fileInfo")),
                response.body(),
                "");
    }

    @Override
    public AcademicReportResult generate(AcademicReportRequest request) {
        String requestId = request == null || !StringUtils.hasText(request.requestId())
                ? nextRequestId("report")
                : text(request.requestId());
        ReactorToolResponse response = post("/v1/tool/report", mapOf(
                "requestId", requestId,
                "task", request == null ? "" : text(request.task()),
                "fileNames", request == null ? List.of() : safeList(request.fileNames()),
                "fileName", request == null ? "report.md" : firstText(request.fileName(), "report.md"),
                "fileDescription", request == null ? "" : text(request.summary()),
                "fileType", request == null ? "markdown" : firstText(request.fileType(), "markdown"),
                "templateType", request == null ? "html" : firstText(request.templateType(), "html"),
                "stream", false
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicReportResult(false, "", "", List.of(), response.body(),
                    firstText(response.errorMessage(), errorMessage(response.body())));
        }
        String content = firstText(response.body().get("data"), response.body().get("message"));
        return new AcademicReportResult(true,
                content,
                limit(content, 240),
                fileRefs(response.body().get("fileInfo"), request == null ? "" : text(request.fileName())),
                response.body(),
                "");
    }

    @Override
    public AcademicWebFetchResult fetch(AcademicWebFetchRequest request) {
        String requestId = request == null || !StringUtils.hasText(request.requestId())
                ? nextRequestId("web")
                : text(request.requestId());
        ReactorToolResponse response = post("/v1/tool/web_fetch", mapOf(
                "requestId", requestId,
                "url", request == null ? "" : text(request.url()),
                "timeoutSeconds", request == null ? 30 : Math.max(5, request.timeoutSeconds())
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicWebFetchResult(false, "", "", "", "", List.of(), response.body(),
                    firstText(response.errorMessage(), errorMessage(response.body())));
        }
        Map<String, Object> data = objectMap(response.body().get("data"));
        String content = firstText(data.get("content"), response.body().get("data"));
        int maxContentChars = request == null ? 4000 : Math.max(256, request.maxContentChars());
        Map<String, Object> metadata = new LinkedHashMap<>(response.body());
        metadata.put("provider", "reactor-tool");
        metadata.put("data", data);
        return new AcademicWebFetchResult(true,
                text(data.get("title")),
                firstText(data.get("finalUrl"), request == null ? "" : request.url()),
                limit(content, maxContentChars),
                limit(content, 240),
                fileRefs(response.body().get("fileInfo")),
                metadata,
                "");
    }

    @Override
    public AcademicImageGenerationResult generate(AcademicImageGenerationRequest request) {
        String requestId = nextRequestId("image");
        ReactorToolResponse response = post("/v1/tool/image_generation", mapOf(
                "requestId", requestId,
                "prompt", request == null ? "" : text(request.prompt()),
                "mode", imageMode(request == null ? "" : request.mode()),
                "model", request == null ? AcademicImageGenerationPort.DEFAULT_MODEL : firstText(request.model(), AcademicImageGenerationPort.DEFAULT_MODEL),
                "baseUrl", request == null ? "" : text(request.baseUrl()),
                "apiKey", request == null ? "" : text(request.apiKey()),
                "quality", request == null ? AcademicImageGenerationPort.DEFAULT_QUALITY : firstText(request.quality(), AcademicImageGenerationPort.DEFAULT_QUALITY),
                "aspectRatio", request == null ? AcademicImageGenerationPort.DEFAULT_ASPECT_RATIO : firstText(request.aspectRatio(), AcademicImageGenerationPort.DEFAULT_ASPECT_RATIO),
                "size", request == null ? "1024x1024" : firstText(request.size(), "1024x1024"),
                "n", request == null ? 1 : Math.max(1, Math.min(10, request.batchCount())),
                "fileNames", request == null ? List.of() : safeList(request.sourceImageUrls()),
                "maskFileNames", request == null ? List.of() : safeList(request.maskImageUrls()),
                "stream", false
        ));
        if (!response.success()) {
            return new AcademicImageGenerationResult(false, "reactor-tool", "", false, List.of(), response.errorMessage());
        }
        return new AcademicImageGenerationResult(ok(response.body()), "reactor-tool",
                firstText(response.body().get("data"), response.body().get("message")),
                false,
                fileRefs(response.body().get("fileInfo")),
                errorMessage(response.body()));
    }

    @Override
    public AcademicMultimodalAnalysisResult analyze(AcademicMultimodalAnalysisRequest request) {
        ReactorToolResponse response = post("/v1/tool/mragQuery", mapOf(
                "question", firstText(request == null ? "" : request.task(), request == null ? "" : request.text()),
                "image_urls", request == null ? List.of() : safeList(request.imageUrls())
        ));
        if (!response.success()) {
            return new AcademicMultimodalAnalysisResult(false, "", "", Map.of(), List.of(), response.errorMessage());
        }
        String content = streamContent(response);
        return new AcademicMultimodalAnalysisResult(true, limit(content, 240), content,
                Map.of("provider", "reactor-tool", "fileUrls", request == null ? List.of() : safeList(request.fileUrls())),
                List.of(), "");
    }

    @Override
    public AcademicDeepSearchResult search(AcademicDeepSearchRequest request) {
        String requestId = nextRequestId("deep");
        ReactorToolResponse response = post("/v1/tool/deepsearch", mapOf(
                "request_id", requestId,
                "query", request == null ? "" : text(request.query()),
                "maxLoop", request == null ? 1 : Math.max(1, request.maxResults()),
                "search_engines", request == null ? List.of() : safeList(request.sourceTypes()),
                "stream", true
        ));
        if (!response.success()) {
            return new AcademicDeepSearchResult(false, request == null ? "" : request.query(), "",
                    "", List.of(), List.of(), List.of(), Map.of(), response.errorMessage());
        }
        String answer = streamContent(response);
        Map<String, Object> metadata = new LinkedHashMap<>(response.body());
        metadata.put("provider", "reactor-tool");
        return new AcademicDeepSearchResult(true,
                request == null ? "" : request.query(),
                answer,
                limit(answer, 240),
                strings(response.body().get("subQueries")),
                documents(response.body()),
                fileRefs(response.body().get("fileInfo")),
                metadata,
                "");
    }

    @Override
    public AcademicFileToolResult upload(AcademicFileUploadRequest request) {
        ReactorToolResponse response = post("/v1/file_tool/upload_file", mapOf(
                "requestId", request == null ? nextRequestId("file") : text(request.requestId()),
                "fileName", request == null ? "" : text(request.fileName()),
                "description", request == null ? "" : text(request.description()),
                "content", request == null ? "" : text(request.content())
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicFileToolResult(false, "upload", request == null ? "" : request.fileName(), "",
                    "", List.of(), response.body(), firstText(response.errorMessage(), errorMessage(response.body())));
        }
        return new AcademicFileToolResult(true, "upload", request == null ? "" : request.fileName(), "",
                "file uploaded", fileRefs(List.of(response.body()), request == null ? "" : text(request.fileName())),
                response.body(), "");
    }

    @Override
    public AcademicFileToolResult get(AcademicFileGetRequest request) {
        ReactorToolResponse response = post("/v1/file_tool/get_file", mapOf(
                "requestId", request == null ? "" : text(request.requestId()),
                "fileName", request == null ? "" : text(request.fileName())
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicFileToolResult(false, "get", request == null ? "" : request.fileName(), "",
                    "", List.of(), response.body(), firstText(response.errorMessage(), errorMessage(response.body())));
        }
        return new AcademicFileToolResult(true, "get", request == null ? "" : request.fileName(), "",
                "file loaded", fileRefs(List.of(response.body()), request == null ? "" : text(request.fileName())),
                response.body(), "");
    }

    @Override
    public AcademicScriptRunResult run(AcademicScriptRunRequest request) {
        ReactorToolResponse response = post("/v1/tool/script_runner", mapOf(
                "requestId", request == null ? nextRequestId("script") : text(request.requestId()),
                "skillName", request == null ? "" : text(request.skillName()),
                "skillBasePath", request == null ? "" : text(request.skillBasePath()),
                "scriptName", request == null ? "" : text(request.scriptName()),
                "scriptPath", request == null ? "" : text(request.scriptPath()),
                "runtime", request == null ? "" : text(request.runtime()),
                "arguments", request == null ? Map.of() : objectMap(request.arguments()),
                "argv", request == null ? List.of() : safeList(request.argv()),
                "timeoutSeconds", request == null ? 120 : request.timeoutSeconds()
        ));
        if (!response.success()) {
            return new AcademicScriptRunResult(false, -1, "", response.errorMessage(), "", List.of(), Map.of(), response.errorMessage());
        }
        boolean success = bool(response.body().get("success"), ok(response.body()));
        return new AcademicScriptRunResult(success,
                integer(response.body().get("exitCode"), success ? 0 : -1),
                text(response.body().get("stdout")),
                text(response.body().get("stderr")),
                firstText(response.body().get("summary"), response.body().get("message")),
                fileRefs(response.body().get("fileInfo")),
                response.body(),
                errorMessage(response.body()));
    }

    @Override
    public AcademicTableRagResult recall(AcademicTableRagRequest request) {
        String requestId = request == null ? nextRequestId("table") : text(request.requestId());
        ReactorToolResponse response = post("/v1/tool/table_rag", mapOf(
                "requestId", requestId,
                "query", request == null ? "" : text(request.query()),
                "currentDateInfo", LocalDate.now().toString(),
                "modelCodeList", request == null ? List.of() : safeList(request.modelCodeList()),
                "schemaInfo", List.of(),
                "recallType", request == null ? "only_recall" : text(request.recallType()),
                "stream", false,
                "useVector", request != null && request.useVector(),
                "useElastic", request != null && request.useElastic()
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicTableRagResult(false, requestId, List.of(), response.body(),
                    firstText(response.errorMessage(), errorMessage(response.body())));
        }
        return new AcademicTableRagResult(true, requestId, tableMatches(response.body().get("data")), response.body(), "");
    }

    @Override
    public AcademicNl2SqlResult convert(AcademicNl2SqlRequest request) {
        String requestId = request == null ? nextRequestId("sql") : text(request.requestId());
        ReactorToolResponse response = post("/v1/tool/nl2sql", mapOf(
                "requestId", requestId,
                "query", request == null ? "" : text(request.query()),
                "currentDateInfo", request == null ? LocalDate.now().toString() : text(request.currentDateInfo()),
                "modelCodeList", request == null ? List.of() : safeList(request.modelCodeList()),
                "schemaInfo", request == null ? List.of() : safeList(request.schemaInfo()),
                "dbType", request == null ? "mysql" : text(request.dbType()),
                "stream", false
        ));
        if (!response.success() || !ok(response.body())) {
            return new AcademicNl2SqlResult(false, requestId, request == null ? "" : request.query(), "",
                    "error", List.of(), response.body(), firstText(response.errorMessage(), errorMessage(response.body())));
        }
        return new AcademicNl2SqlResult(true, requestId, request == null ? "" : request.query(),
                firstText(response.body().get("think"), nested(response.body().get("data"), "think")),
                firstText(response.body().get("status"), "data"),
                sqlCandidates(response.body()),
                response.body(),
                "");
    }

    private ReactorToolResponse post(String path, Map<String, Object> body) {
        if (!StringUtils.hasText(baseUrl)) {
            return ReactorToolResponse.failure("reactor-tool base url is not configured");
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM, MediaType.TEXT_PLAIN));
            if (StringUtils.hasText(apiKey)) {
                headers.setBearerAuth(apiKey);
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return ReactorToolResponse.failure("reactor-tool http status: " + response.getStatusCode().value());
            }
            String raw = response.getBody() == null ? "" : response.getBody();
            return ReactorToolResponse.success(parseBody(raw), raw);
        } catch (RestClientException e) {
            return ReactorToolResponse.failure(e.getMessage());
        }
    }

    private Map<String, Object> parseBody(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("data:")) {
            return parseSse(trimmed);
        }
        try {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("data", trimmed);
        }
    }

    private Map<String, Object> parseSse(String raw) {
        List<String> events = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        Map<String, Object> lastJson = new LinkedHashMap<>();
        List<String> subQueries = new ArrayList<>();
        List<Map<String, Object>> documents = new ArrayList<>();
        List<Map<String, Object>> fileInfo = new ArrayList<>();
        for (String line : raw.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String data = trimmed.substring("data:".length()).trim();
            if (!StringUtils.hasText(data) || "[DONE]".equals(data)) {
                continue;
            }
            events.add(data);
            Map<String, Object> parsed = parseJsonObject(data);
            if (!parsed.isEmpty()) {
                lastJson = parsed;
                content.append(extractEventContent(parsed));
                collectDeepSearchArtifacts(parsed, subQueries, documents);
                collectFileInfo(parsed.get("fileInfo"), fileInfo);
            } else {
                content.append(data);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(lastJson);
        result.put("data", content.toString());
        result.put("events", events);
        if (!subQueries.isEmpty()) {
            result.put("subQueries", subQueries);
        }
        if (!documents.isEmpty()) {
            result.put("documents", documents);
        }
        if (!fileInfo.isEmpty()) {
            result.put("fileInfo", fileInfo);
        }
        return result;
    }

    private Map<String, Object> parseJsonObject(String text) {
        try {
            return objectMapper.readValue(text, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractEventContent(Map<String, Object> event) {
        Object choices = event.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> choice) {
            Object delta = ((Map<String, Object>) choice).get("delta");
            if (delta instanceof Map<?, ?> deltaMap) {
                return text(((Map<String, Object>) deltaMap).get("content"));
            }
        }
        String answer = text(event.get("answer"));
        if (StringUtils.hasText(answer)) {
            return answer;
        }
        String codeOutput = text(event.get("codeOutput"));
        if (StringUtils.hasText(codeOutput)) {
            return codeOutput;
        }
        Object data = event.get("data");
        return data instanceof Map<?, ?> || data instanceof List<?> ? "" : text(data);
    }

    private String streamContent(ReactorToolResponse response) {
        return firstText(response.body().get("data"), response.raw());
    }

    @SuppressWarnings("unchecked")
    private void collectDeepSearchArtifacts(Map<String, Object> event,
                                            List<String> subQueries,
                                            List<Map<String, Object>> documents) {
        Object searchResult = event.get("searchResult");
        if (!(searchResult instanceof Map<?, ?> map)) {
            return;
        }
        Map<String, Object> result = (Map<String, Object>) map;
        for (String query : strings(result.get("query"))) {
            if (!subQueries.contains(query)) {
                subQueries.add(query);
            }
        }
        Object docs = result.get("docs");
        if (!(docs instanceof List<?> docsList)) {
            return;
        }
        for (Object bucket : docsList) {
            if (bucket instanceof List<?> bucketList) {
                for (Object item : bucketList) {
                    addDocument(item, documents);
                }
            } else {
                addDocument(bucket, documents);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void addDocument(Object item, List<Map<String, Object>> documents) {
        if (item instanceof Map<?, ?> map) {
            documents.add(new LinkedHashMap<>((Map<String, Object>) map));
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFileInfo(Object value, List<Map<String, Object>> target) {
        if (!(value instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                target.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<AcademicToolFileRef> fileRefs(Object value) {
        return fileRefs(value, "");
    }

    @SuppressWarnings("unchecked")
    private List<AcademicToolFileRef> fileRefs(Object value, String fallbackFileName) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<AcademicToolFileRef> refs = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> fileInfo = (Map<String, Object>) map;
                refs.add(AcademicToolFileRef.builder()
                        .artifactId(firstText(fileInfo.get("artifactId"), fileInfo.get("fileId")))
                        .fileName(firstText(fileInfo.get("fileName"), fileInfo.get("filename"), fallbackFileName))
                        .downloadUrl(firstText(fileInfo.get("downloadUrl"), fileInfo.get("ossUrl")))
                        .previewUrl(firstText(fileInfo.get("domainUrl"), fileInfo.get("previewUrl")))
                        .contentType(firstText(fileInfo.get("contentType"), fileInfo.get("mimeType")))
                        .fileSize(longValue(fileInfo.get("fileSize")))
                        .build());
            }
        }
        return refs;
    }

    @SuppressWarnings("unchecked")
    private List<AcademicDeepSearchDocument> documents(Map<String, Object> body) {
        Object data = body.get("data");
        Object docs = data instanceof Map<?, ?> dataMap ? ((Map<String, Object>) dataMap).get("documents") : body.get("documents");
        if (!(docs instanceof List<?> list)) {
            return List.of();
        }
        List<AcademicDeepSearchDocument> documents = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> doc = (Map<String, Object>) map;
                documents.add(new AcademicDeepSearchDocument(
                        text(doc.get("title")),
                        firstText(doc.get("url"), doc.get("link")),
                        text(doc.get("content")),
                        firstText(doc.get("source"), doc.get("doc_type"), nested(doc.get("data"), "search_engine"))));
            }
        }
        return documents;
    }

    @SuppressWarnings("unchecked")
    private List<AcademicTableSchemaMatch> tableMatches(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<Map<String, Object>> schemaList = new ArrayList<>();
            ((Map<String, Object>) map).forEach((key, item) -> schemaList.add(mapOf("name", key, "value", item)));
            return List.of(new AcademicTableSchemaMatch("", 1.0d, schemaList));
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<AcademicTableSchemaMatch> matches = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> match = (Map<String, Object>) map;
                matches.add(new AcademicTableSchemaMatch(
                        firstText(match.get("modelCode"), match.get("model_code")),
                        doubleValue(match.get("score")),
                        listOfMap(match.get("schemaList"))));
            }
        }
        return matches;
    }

    private List<AcademicSqlCandidate> sqlCandidates(Map<String, Object> body) {
        Object data = body.get("data");
        List<AcademicSqlCandidate> candidates = new ArrayList<>();
        collectSqlCandidate(candidates, body);
        if (data instanceof Map<?, ?> map) {
            collectSqlCandidate(candidates, objectMap(map));
        } else if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    collectSqlCandidate(candidates, objectMap(map));
                }
            }
        }
        return candidates;
    }

    private void collectSqlCandidate(List<AcademicSqlCandidate> candidates, Map<String, Object> map) {
        String sql = firstText(map.get("sql"), map.get("SQL"), map.get("querySql"), map.get("nl2sql"));
        if (StringUtils.hasText(sql)) {
            candidates.add(new AcademicSqlCandidate(firstText(map.get("query"), map.get("rootQuery")), sql));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }

    private List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::text)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        String text = text(value);
        return StringUtils.hasText(text) ? List.of(text) : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String nested(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return text(((Map<String, Object>) map).get(key));
        }
        return "";
    }

    private boolean ok(Map<String, Object> body) {
        if (body.containsKey("success") && !bool(body.get("success"), true)) {
            return false;
        }
        int code = integer(body.get("code"), 200);
        return code >= 200 && code < 300 && !StringUtils.hasText(errorMessage(body));
    }

    private String errorMessage(Map<String, Object> body) {
        String explicit = firstText(body.get("errorMessage"), body.get("err_msg"), body.get("error_msg"),
                body.get("error"), body.get("detail"));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        if (body.containsKey("success") && !bool(body.get("success"), true)) {
            return firstText(body.get("message"), "reactor-tool returned success=false");
        }
        int code = integer(body.get("code"), 200);
        if (code < 200 || code >= 300) {
            return firstText(body.get("message"), "reactor-tool error code: " + code);
        }
        return "";
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object key = pairs[i];
            if (key != null) {
                map.put(String.valueOf(key), pairs[i + 1]);
            }
        }
        return map;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String imageMode(String mode) {
        if ("edit".equalsIgnoreCase(mode) || "edits".equalsIgnoreCase(mode)) {
            return "edits";
        }
        return "images";
    }

    private String nextRequestId(String prefix) {
        return "agent-group-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String trimSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String limit(String value, int maxLength) {
        String text = text(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(text(value));
        } catch (NumberFormatException e) {
            return 0.0d;
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
    }

    private record ReactorToolResponse(boolean success,
                                       Map<String, Object> body,
                                       String raw,
                                       String errorMessage) {

        private static ReactorToolResponse success(Map<String, Object> body, String raw) {
            return new ReactorToolResponse(true, body == null ? Map.of() : body, raw == null ? "" : raw, "");
        }

        private static ReactorToolResponse failure(String errorMessage) {
            return new ReactorToolResponse(false, Map.of(), "", errorMessage == null ? "" : errorMessage);
        }
    }
}















