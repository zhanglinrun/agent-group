package com.linrun.domain.knowledge.service;

import com.linrun.domain.knowledge.model.CreateKnowledgeDocumentCommand;
import com.linrun.domain.knowledge.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.knowledge.model.KnowledgeDocument;
import com.linrun.domain.knowledge.model.KnowledgeDocumentBuildResult;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

        List<KnowledgeFragment> fragments = fragmentCommands.stream()
                .map(command -> KnowledgeFragment.enabled(nextNo("KF"), document, command, now))
                .toList();
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
