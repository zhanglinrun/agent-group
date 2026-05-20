package com.linrun.domain.knowledgeasset.adapter;

import com.linrun.domain.knowledgeasset.model.StoredKnowledgeObject;

public interface KnowledgeObjectStorageClient {

    StoredKnowledgeObject store(String originalFilename, String contentType, byte[] content);
}
