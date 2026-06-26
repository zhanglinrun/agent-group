package com.linrun.domain.agent.file.model;

/**
 * 文件解析结果。
 */
public record ParsedFile(
        String fullText,
        String truncatedText) {
}
