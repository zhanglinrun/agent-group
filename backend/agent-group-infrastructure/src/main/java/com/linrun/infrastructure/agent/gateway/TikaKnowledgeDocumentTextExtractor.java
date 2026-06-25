package com.linrun.infrastructure.agent.gateway;

import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentTextExtractor;
import com.linrun.types.exception.AppException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class TikaKnowledgeDocumentTextExtractor implements KnowledgeDocumentTextExtractor {

    private static final int MAX_CHARS = 200_000;

    private final Tika tika;

    public TikaKnowledgeDocumentTextExtractor() {
        this(new Tika());
    }

    TikaKnowledgeDocumentTextExtractor(Tika tika) {
        this.tika = tika;
        this.tika.setMaxStringLength(MAX_CHARS);
    }

    @Override
    public String extract(String fileName, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new AppException("0001", "上传文件内容不能为空");
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            String parsed = tika.parseToString(inputStream);
            if (StringUtils.hasText(parsed)) {
                return parsed.trim();
            }
            return fallbackText(content);
        } catch (IOException | TikaException e) {
            String fallback = fallbackText(content);
            if (StringUtils.hasText(fallback)) {
                return fallback;
            }
            throw new AppException("DOC_0001", "文档内容解析失败，请确认文件为可读取的 PDF、DOCX、TXT 或 Markdown");
        }
    }

    private String fallbackText(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8)
                .replace("\u0000", "")
                .trim();
        return StringUtils.hasText(text) ? text : "";
    }
}















