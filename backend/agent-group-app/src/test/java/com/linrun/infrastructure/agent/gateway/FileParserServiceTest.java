package com.linrun.infrastructure.agent.gateway;

import com.linrun.domain.agent.file.model.ParsedFile;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserServiceTest {

    private final PdfBoxFileParseAdapter fileParserService = new PdfBoxFileParseAdapter();

    @Test
    void shouldParseMarkdownFileAsText() {
        byte[] content = "# Drawio 图\n\n这个文件包含 draw.io 说明。".getBytes(StandardCharsets.UTF_8);

        ParsedFile result = fileParserService.parse("drawio.md", content, "text/markdown");

        assertTrue(result.fullText().contains("Drawio 图"));
        assertTrue(result.truncatedText().contains("draw.io"));
    }
}
