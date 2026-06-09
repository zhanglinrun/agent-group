package com.linrun.domain.agent.knowledge.service;

import com.linrun.domain.agent.knowledge.model.CreateKnowledgeDocumentCommand;
import com.linrun.domain.agent.knowledge.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentBuildResult;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public KnowledgeDocumentBuildResult createParsedDocument(CreateKnowledgeDocumentCommand documentCommand,
                                                             List<CreateKnowledgeFragmentCommand> fragmentCommands) {
        validateDocumentCommand(documentCommand);
        validateFragmentCommands(fragmentCommands);

        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = KnowledgeDocument.uploaded(nextNo("DOC"), documentCommand, now);
        document.markParsed();
        document.enable();

        List<String> fragmentIds = fragmentCommands.stream()
                .map(command -> nextNo("KF"))
                .toList();
        Map<String, String> parentIdMap = new HashMap<>();
        for (int i = 0; i < fragmentCommands.size(); i++) {
            CreateKnowledgeFragmentCommand command = fragmentCommands.get(i);
            if (StringUtils.hasText(command.getParentKey()) && "PARENT".equals(command.getChunkType())) {
                parentIdMap.put(command.getParentKey(), fragmentIds.get(i));
            }
        }
        for (CreateKnowledgeFragmentCommand command : fragmentCommands) {
            if (StringUtils.hasText(command.getParentKey())
                    && !"PARENT".equals(command.getChunkType())
                    && !StringUtils.hasText(command.getParentFragmentId())) {
                command.setParentFragmentId(parentIdMap.get(command.getParentKey()));
            }
        }
        List<KnowledgeFragment> fragments = new java.util.ArrayList<>();
        for (int i = 0; i < fragmentCommands.size(); i++) {
            fragments.add(KnowledgeFragment.enabled(fragmentIds.get(i), document, fragmentCommands.get(i), now));
        }
        return new KnowledgeDocumentBuildResult(document, fragments);
    }

    private void validateDocumentCommand(CreateKnowledgeDocumentCommand command) {
        if (command == null) {
            throw new AppException("0001", "knowledge document cannot be null");
        }
        if (!StringUtils.hasText(command.getDocumentName())) {
            throw new AppException("0001", "documentName cannot be blank");
        }
        if (!StringUtils.hasText(command.getDocumentType())) {
            throw new AppException("0001", "documentType cannot be blank");
        }
        if (!StringUtils.hasText(command.getKnowledgeVersion())) {
            throw new AppException("0001", "knowledgeVersion cannot be blank");
        }
        if (!StringUtils.hasText(command.getSourceType())) {
            throw new AppException("0001", "sourceType cannot be blank");
        }
        if (!StringUtils.hasText(command.getSourceName())) {
            throw new AppException("0001", "sourceName cannot be blank");
        }
    }

    private void validateFragmentCommands(List<CreateKnowledgeFragmentCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new AppException("0001", "knowledge fragments cannot be empty");
        }
        commands.forEach(this::validateFragmentCommand);
    }

    private void validateFragmentCommand(CreateKnowledgeFragmentCommand command) {
        if (command == null) {
            throw new AppException("0001", "knowledge fragment cannot be null");
        }
        if (!StringUtils.hasText(command.getGoodsId())) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
        if (!StringUtils.hasText(command.getContent())) {
            throw new AppException("0001", "content cannot be blank");
        }
        if (command.getRankNo() == null || command.getRankNo() <= 0) {
            throw new AppException("0001", "rankNo must be positive");
        }
    }

    private String nextNo(String prefix) {
        String timePart = LocalDateTime.now().format(NO_TIME_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return prefix + timePart + randomPart;
    }
}















