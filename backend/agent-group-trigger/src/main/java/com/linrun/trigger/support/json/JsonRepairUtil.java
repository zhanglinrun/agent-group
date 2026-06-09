package com.linrun.trigger.support.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonRepairUtil {

    private static final Pattern MARKDOWN_JSON = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonRepairUtil() {
    }

    public static String repair(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "{}";
        }
        String fixed = extractMarkdownJson(text.trim());
        fixed = trimToJsonScope(fixed);
        fixed = normalizeQuotes(fixed);
        fixed = fixed.replaceAll(",\\s*([}\\]])", "$1");
        fixed = fixed.replaceAll("([{,]\\s*)([a-zA-Z_][a-zA-Z0-9_]*)\\s*:", "$1\"$2\":");
        fixed = fixed.replaceAll("(?<!\\\\)[\\r\\n\\t]", " ");
        if (isValid(fixed)) {
            return fixed;
        }
        return wrapContent(text);
    }

    public static boolean isValid(String text) {
        try {
            OBJECT_MAPPER.readTree(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonNode parse(String text) {
        try {
            return OBJECT_MAPPER.readTree(repair(text));
        } catch (Exception e) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static String extractMarkdownJson(String text) {
        Matcher matcher = MARKDOWN_JSON.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : text;
    }

    private static String trimToJsonScope(String text) {
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (value == '{' || value == '[') {
                start = i;
                break;
            }
        }
        int end = -1;
        for (int i = text.length() - 1; i >= 0; i--) {
            char value = text.charAt(i);
            if (value == '}' || value == ']') {
                end = i + 1;
                break;
            }
        }
        return start >= 0 && end > start ? text.substring(start, end) : text;
    }

    private static String normalizeQuotes(String text) {
        return text.replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replaceAll("'([^']*?)'", "\"$1\"");
    }

    private static String wrapContent(String text) {
        String escaped = text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"content\":\"" + escaped + "\"}";
    }
}















