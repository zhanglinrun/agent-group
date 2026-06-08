package com.linrun.infrastructure.agent.gateway;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaKnowledgeDocumentTextExtractorTest {

    private final TikaKnowledgeDocumentTextExtractor extractor = new TikaKnowledgeDocumentTextExtractor();

    @Test
    void shouldExtractTextFromDocx() throws Exception {
        String text = extractor.extract(
                "quota-policy.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("DOCX quota package detail supports group buy and refund policy"));

        assertTrue(text.contains("DOCX quota package detail"));
        assertTrue(text.contains("refund policy"));
    }

    @Test
    void shouldExtractTextFromPdf() throws Exception {
        String text = extractor.extract(
                "quota-policy.pdf",
                "application/pdf",
                pdf("PDF quota policy supports group buy refund"));

        assertTrue(text.contains("PDF quota policy"));
        assertTrue(text.contains("refund"));
    }

    private byte[] docx(String content) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                      </w:body>
                    </w:document>
                    """).formatted(content).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return outputStream.toByteArray();
    }

    private byte[] pdf(String content) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(content);
                stream.endText();
            }
            document.save(outputStream);
        }
        return outputStream.toByteArray();
    }
}
