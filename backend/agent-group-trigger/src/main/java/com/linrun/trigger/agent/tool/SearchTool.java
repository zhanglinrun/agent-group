package com.linrun.trigger.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.trigger.http.agent.AcademicExternalSearchService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchTool {

    private final AcademicExternalSearchService searchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SearchTool(AcademicExternalSearchService searchService) {
        this.searchService = searchService;
    }

    public static ToolCallback[] create(AcademicExternalSearchService searchService) {
        if (searchService == null || !searchService.available()) {
            return new ToolCallback[0];
        }
        return ToolCallbacks.from(new SearchTool(searchService));
    }

    @Tool(name = "search", description = "联网搜索最新公开信息。用于政策、新闻、价格、规则、时间安排等需要实时核验的问题。")
    public String search(
            @ToolParam(description = "搜索关键词，尽量包含主体、年份和权威来源限定") String query,
            @ToolParam(description = "最多返回结果数，默认 5，范围 1 到 5", required = false) Integer maxResults) {
        try {
            if (!StringUtils.hasText(query)) {
                return objectMapper.writeValueAsString(Map.of("results", List.of()));
            }
            int limit = maxResults == null ? 5 : Math.max(1, Math.min(maxResults, 5));
            List<Map<String, String>> results = searchService.search(query, limit)
                    .stream()
                    .map(item -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("title", nullToBlank(item.title()));
                        row.put("url", nullToBlank(item.url()));
                        row.put("content", nullToBlank(item.content()));
                        return row;
                    })
                    .toList();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", query);
            response.put("results", results);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"results\":[],\"error\":\"搜索工具执行失败\"}";
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
