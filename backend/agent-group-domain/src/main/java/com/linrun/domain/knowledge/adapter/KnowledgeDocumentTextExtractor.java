package com.linrun.domain.knowledge.adapter;

public interface KnowledgeDocumentTextExtractor {

    String extract(String fileName, String contentType, byte[] content);
}
