package com.linrun.trigger.http.agent;

import com.linrun.api.dto.KnowledgeDocumentDTO;
import com.linrun.api.dto.KnowledgeDocumentFullContentResponse;
import com.linrun.api.dto.KnowledgeFragmentDTO;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
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
                .sorted(Comparator.comparing(
                        KnowledgeFragment::getRankNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(this::toFragmentDTO)
                .toList();
    }

    public KnowledgeDocumentFullContentResponse queryFullContent(String documentId) {
        KnowledgeDocument document = queryRequiredDocument(documentId);
        List<KnowledgeFragment> fragments = knowledgeDocumentRepository.queryFragmentsByDocumentId(document.getDocumentId()).stream()
                .sorted(Comparator.comparing(
                        KnowledgeFragment::getRankNo,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
        KnowledgeDocumentFullContentResponse response = new KnowledgeDocumentFullContentResponse();
        response.setDocumentId(document.getDocumentId());
        response.setDocumentName(document.getDocumentName());
        response.setDocumentType(document.getDocumentType());
        response.setKnowledgeVersion(document.getKnowledgeVersion());
        response.setSourceType(document.getSourceType());
        response.setSourceName(document.getSourceName());
        response.setDocumentStatus(document.getDocumentStatus() == null ? null : document.getDocumentStatus().name());
        fillDocumentEvidence(document, fragments, response);
        response.setEnabled(document.getEnabled());
        response.setFragmentCount(fragments.size());
        response.setFragments(fragments.stream().map(this::toFragmentDTO).toList());
        response.setCitationSnippets(response.getFragments().stream()
                .map(KnowledgeFragmentDTO::getCitationSnippet)
                .filter(StringUtils::hasText)
                .limit(3)
                .toList());
        response.setContent(fragments.stream()
                .map(KnowledgeFragment::getContent)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse(""));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentDTO disableDocument(String documentId) {
        KnowledgeDocument document = queryRequiredDocument(documentId);
        document.disable();
        knowledgeDocumentRepository.updateDocumentStatus(document);
        knowledgeDocumentRepository.updateFragmentsStatusByDocumentId(
                document.getDocumentId(),
                KnowledgeFragmentStatus.DISABLED,
                false);
        return toDocumentDTO(document);
    }

    private KnowledgeDocument queryRequiredDocument(String documentId) {
        if (!StringUtils.hasText(documentId)) {
            throw new AppException("0001", "documentId cannot be blank");
        }
        return knowledgeDocumentRepository.queryDocumentByDocumentId(documentId)
                .orElseThrow(() -> new AppException("KNOWLEDGE_0001", "knowledge document not found"));
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
        List<KnowledgeFragment> fragments = knowledgeDocumentRepository.queryFragmentsByDocumentId(document.getDocumentId());
        fillDocumentEvidence(document, fragments, dto);
        dto.setEnabled(document.getEnabled());
        dto.setFragmentCount(fragments.size());
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
        dto.setCitationLabel(citationLabel(fragment));
        dto.setCitationSnippet(snippet(fragment.getContent(), 120));
        return dto;
    }

    private void fillDocumentEvidence(KnowledgeDocument document,
                                      List<KnowledgeFragment> fragments,
                                      KnowledgeDocumentDTO dto) {
        DocumentEvidence evidence = documentEvidence(document, fragments);
        dto.setParseStatus(evidence.parseStatus());
        dto.setEmbeddingStatus(evidence.embeddingStatus());
        dto.setRetrievalReady(evidence.retrievalReady());
        dto.setFailureReason(evidence.failureReason());
    }

    private void fillDocumentEvidence(KnowledgeDocument document,
                                      List<KnowledgeFragment> fragments,
                                      KnowledgeDocumentFullContentResponse dto) {
        DocumentEvidence evidence = documentEvidence(document, fragments);
        dto.setParseStatus(evidence.parseStatus());
        dto.setEmbeddingStatus(evidence.embeddingStatus());
        dto.setRetrievalReady(evidence.retrievalReady());
        dto.setFailureReason(evidence.failureReason());
    }

    private DocumentEvidence documentEvidence(KnowledgeDocument document, List<KnowledgeFragment> fragments) {
        KnowledgeDocumentStatus status = document.getDocumentStatus();
        boolean hasFragments = fragments != null && !fragments.isEmpty();
        boolean retrievalReady = KnowledgeDocumentStatus.ENABLED.equals(status)
                && Boolean.TRUE.equals(document.getEnabled())
                && hasFragments;
        String parseStatus = hasFragments ? "PARSED" : "PENDING";
        String embeddingStatus = switch (status == null ? KnowledgeDocumentStatus.UPLOADED : status) {
            case ENABLED -> "READY";
            case EMBEDDING_FAILED -> "FAILED";
            case DISABLED -> "DISABLED";
            default -> "PENDING";
        };
        return new DocumentEvidence(
                parseStatus,
                embeddingStatus,
                retrievalReady,
                failureReason(status, hasFragments));
    }

    private String failureReason(KnowledgeDocumentStatus status, boolean hasFragments) {
        if (!hasFragments) {
            return "文档还没有生成可引用片段";
        }
        if (KnowledgeDocumentStatus.EMBEDDING_FAILED.equals(status)) {
            return "向量入库失败，已保留解析片段，可重新执行向量补偿";
        }
        if (KnowledgeDocumentStatus.DISABLED.equals(status)) {
            return "文档已停用，不参与检索";
        }
        return "";
    }

    private String citationLabel(KnowledgeFragment fragment) {
        String documentId = fragment.getDocumentId() == null ? "-" : fragment.getDocumentId();
        String rankNo = fragment.getRankNo() == null ? "-" : String.valueOf(fragment.getRankNo());
        return documentId + "#" + rankNo;
    }

    private String snippet(String content, int limit) {
        String text = String.valueOf(content == null ? "" : content)
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

    private record DocumentEvidence(String parseStatus,
                                    String embeddingStatus,
                                    Boolean retrievalReady,
                                    String failureReason) {
    }
}














