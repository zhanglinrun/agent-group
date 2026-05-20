package com.linrun.domain.knowledgeasset.adapter;

public interface KnowledgeDocumentTextExtractor {

    String extract(String fileName, String contentType, byte[] content);
}
