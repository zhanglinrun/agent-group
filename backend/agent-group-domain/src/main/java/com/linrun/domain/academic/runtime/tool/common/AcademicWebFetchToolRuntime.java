package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicWebFetchPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AcademicWebFetchToolRuntime {

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_CONTENT_CHARS = 4000;
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    private final AcademicWebFetchPort remotePort;
    private final HttpClient httpClient;

    public AcademicWebFetchToolRuntime() {
        this((AcademicWebFetchPort) null);
    }

    public AcademicWebFetchToolRuntime(AcademicWebFetchPort remotePort) {
        this(remotePort, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build());
    }

    public AcademicWebFetchToolRuntime(HttpClient httpClient) {
        this(null, httpClient);
    }

    public AcademicWebFetchToolRuntime(AcademicWebFetchPort remotePort, HttpClient httpClient) {
        this.remotePort = remotePort;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.WEB_FETCH)
                .description("Fetch a web page and return title, final URL, status code, and readable text.")
                .category("web")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of("type", "string", "description", "HTTP or HTTPS URL."),
                                "timeoutSeconds", Map.of("type", "integer", "description", "Request timeout seconds."),
                                "maxContentChars", Map.of("type", "integer", "description", "Maximum readable text length.")),
                        "required", List.of("url")))
                .requiredArguments(List.of("url"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        URI uri = validateUri(text(arguments.get("url")));
        int timeoutSeconds = integer(arguments.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS);
        int maxContentChars = Math.max(256, integer(arguments.get("maxContentChars"), DEFAULT_MAX_CONTENT_CHARS));

        if (remotePort != null) {
            AcademicWebFetchPort.AcademicWebFetchResult remoteResult = remotePort.fetch(
                    new AcademicWebFetchPort.AcademicWebFetchRequest(
                            requestId(command),
                            uri.toString(),
                            timeoutSeconds,
                            maxContentChars));
            if (remoteResult.success()) {
                return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.WEB_FETCH)
                        .title(firstText(remoteResult.title(), uri.toString()))
                        .summary(firstText(remoteResult.summary(), summary(remoteResult.content())))
                        .content(truncate(remoteResult.content(), maxContentChars))
                        .metadata(remoteResult.metadata())
                        .fileRefs(remoteResult.fileRefs())
                        .build();
            }
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("User-Agent", "agent-group-academic-web-fetch/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null ? "" : response.body();
            String title = htmlText(extractTitle(body));
            String readableText = truncate(extractReadableText(body), maxContentChars);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("url", uri.toString());
            metadata.put("finalUrl", response.uri().toString());
            metadata.put("statusCode", response.statusCode());
            metadata.put("contentLength", body.length());
            metadata.put("truncated", readableText.length() < extractReadableText(body).length());

            return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.WEB_FETCH)
                    .title(StringUtils.hasText(title) ? title : uri.toString())
                    .summary(summary(readableText))
                    .content(readableText)
                    .metadata(metadata)
                    .build();
        } catch (IOException e) {
            throw new AppException("WEB_FETCH_0002", "web fetch failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException("WEB_FETCH_0003", "web fetch interrupted");
        }
    }

    private URI validateUri(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new AppException("WEB_FETCH_0001", "only http and https URLs are supported");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new AppException("WEB_FETCH_0001", "URL host cannot be blank");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new AppException("WEB_FETCH_0001", "invalid URL: " + url);
        }
    }

    private String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html == null ? "" : html);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractReadableText(String html) {
        String text = html == null ? "" : html;
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        text = text.replaceAll("(?is)<[^>]+>", " ");
        return htmlText(text);
    }

    private String htmlText(String text) {
        return (text == null ? "" : text)
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String summary(String content) {
        return truncate(content, 180);
    }

    private String truncate(String content, int maxLength) {
        String text = content == null ? "" : content.trim();
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

    private String requestId(AcademicToolCallCommand command) {
        if (command != null && StringUtils.hasText(command.getRequestId())) {
            return command.getRequestId();
        }
        return "agent-group-web-" + UUID.randomUUID().toString().replace("-", "");
    }
}
