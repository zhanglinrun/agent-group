package com.linrun.trigger.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserServiceTest {

    private final FileParserService fileParserService = new FileParserService();

    @Test
    void shouldParseMarkdownFileAsText() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "drawio.md",
                "text/markdown",
                "# Drawio 图\n\n这个文件包含 draw.io 说明�?.getBytes(StandardCharsets.UTF_8));

        FileParserService.ParseResult result = fileParserService.parseFile(file);

        assertTrue(result.getFullText().contains("Drawio �?));
        assertTrue(result.getTruncatedText().contains("draw.io"));
    }
}















