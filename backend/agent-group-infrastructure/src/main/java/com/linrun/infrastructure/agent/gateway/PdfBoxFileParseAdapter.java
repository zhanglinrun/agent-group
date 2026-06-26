package com.linrun.infrastructure.agent.gateway;

import com.linrun.domain.agent.file.adapter.FileParsePort;
import com.linrun.domain.agent.file.model.ParsedFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 会话文件解析实现，基于 PDFBox 和 POI。
 */
@Component
@Slf4j
public class PdfBoxFileParseAdapter implements FileParsePort {

    private static final int MAX_TEXT_LENGTH = 20000;

    @Override
    public ParsedFile parse(String fileName, byte[] content, String contentType) {
        String fullText = parseInternal(fileName, content);
        String truncated = truncateIfNeeded(fullText);
        return new ParsedFile(fullText, truncated);
    }

    private String parseInternal(String fileName, byte[] content) {
        String fileType = fileTypeOf(fileName);
        log.info("开始解析文件 {} (类型: {}, 大小: {} bytes)", fileName, fileType, content == null ? 0 : content.length);
        try {
            String text;
            switch (fileType.toLowerCase(Locale.ROOT)) {
                case "pdf":
                    text = parsePdf(content);
                    break;
                case "docx":
                    text = parseDocx(content);
                    break;
                case "doc":
                    throw new IllegalArgumentException("暂不支持 .doc 格式，请转换为 .docx");
                case "txt":
                case "md":
                case "markdown":
                    text = parseTxt(content);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            }
            log.info("文件解析完成，内容长度 {} 字符", text.length());
            return text;
        } catch (Exception e) {
            log.error("文件解析失败: {}", fileName, e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    private String parsePdf(byte[] content) throws Exception {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.info("PDF 解析完成，页数 {}, 文本长度: {}", document.getNumberOfPages(), text.length());
            return text.trim();
        }
    }

    private String parseDocx(byte[] content) throws Exception {
        try (InputStream is = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(is)) {
            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            for (XWPFParagraph paragraph : paragraphs) {
                String paraText = paragraph.getText();
                if (paraText != null && !paraText.trim().isEmpty()) {
                    text.append(paraText).append("\n");
                }
            }
            log.info("DOCX 解析完成，段落数: {}, 文本长度: {}", paragraphs.size(), text.length());
            return text.toString().trim();
        }
    }

    private String parseTxt(byte[] content) throws Exception {
        String text = new String(content, StandardCharsets.UTF_8);
        log.info("TXT 解析完成，文本长度 {}", text.length());
        return text.trim();
    }

    private String truncateIfNeeded(String content) {
        if (content != null && content.length() > MAX_TEXT_LENGTH) {
            log.warn("文件内容过长，将截断到 {} 字符", MAX_TEXT_LENGTH);
            return content.substring(0, MAX_TEXT_LENGTH) + "\n\n... (内容已截断，文件过长)";
        }
        return content;
    }

    private String fileTypeOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "unknown";
    }
}
