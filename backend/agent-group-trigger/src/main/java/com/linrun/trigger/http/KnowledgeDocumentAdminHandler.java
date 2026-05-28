package com.linrun.trigger.http;

import com.linrun.api.dto.KnowledgeDocumentDTO;
import com.linrun.api.dto.KnowledgeFragmentDTO;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeDocumentAdminHandler {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public KnowledgeDocumentAdminHandler(KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    public List<KnowledgeDocumentDTO> queryDocuments(String status, Integer limit) {
        int safeLimit = Math.min(MAX_LIMIT, Math.max(1, limit == null ? DEFAULT_LIMIT : limit));
        KnowledgeDocumentStatus documentStatus = parseStatus(status);
        return knowledgeDocumentRepository.queryDocumentsByStatus(documentStatus, safeLimit).stream()
                .map(this::toDocumentDTO)
                .toList();
    }

    public List<KnowledgeFragmentDTO> queryFragments(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            throw new AppException("0001", "documentId cannot be blank");
        }
        return knowledgeDocumentRepository.queryFragmentsByDocumentId(documentId).stream()
                .map(this::toFragmentDTO)
                .toList();
    }

    private KnowledgeDocumentStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return KnowledgeDocumentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException("0001", "unsupported document status: " + status);
        }
    }

    private KnowledgeDocumentDTO toDocumentDTO(KnowledgeDocument document) {
        KnowledgeDocumentDTO dto = new KnowledgeDocumentDTO();
        dto.setDocumentId(document.getDocumentId());
        dto.setDocumentName(document.getDocumentName());
        dto.setDocumentType(document.getDocumentType());
        dto.setKnowledgeVersion(document.getKnowledgeVersion());
        dto.setSourceType(document.getSourceType());
        dto.setSourceName(document.getSourceName());
        dto.setDocumentStatus(document.getDocumentStatus() == null ? null : document.getDocumentStatus().name());
        dto.setEnabled(document.getEnabled());
        dto.setFragmentCount(knowledgeDocumentRepository.queryFragmentsByDocumentId(document.getDocumentId()).size());
        dto.setCreateTime(document.getCreateTime());
        dto.setUpdateTime(document.getUpdateTime());
        return dto;
    }

    private KnowledgeFragmentDTO toFragmentDTO(KnowledgeFragment fragment) {
        KnowledgeFragmentDTO dto = new KnowledgeFragmentDTO();
        dto.setFragmentId(fragment.getFragmentId());
        dto.setDocumentId(fragment.getDocumentId());
        dto.setGoodsId(fragment.getGoodsId());
        dto.setDocumentType(fragment.getDocumentType());
        dto.setKnowledgeVersion(fragment.getKnowledgeVersion());
        dto.setContent(fragment.getContent());
        dto.setRankNo(fragment.getRankNo());
        dto.setFragmentStatus(fragment.getFragmentStatus() == null ? null : fragment.getFragmentStatus().name());
        return dto;
    }
}
