package com.linrun.domain.knowledgeasset.service;

import com.linrun.domain.knowledgeasset.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.knowledgeasset.service.splitter.DocumentSplitterFactory;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeDocumentParser {

    private static final int MAX_FRAGMENT_LENGTH = 500;

    private final DocumentSplitterFactory documentSplitterFactory;

    public KnowledgeDocumentParser() {
        this(new DocumentSplitterFactory());
    }

    public KnowledgeDocumentParser(DocumentSplitterFactory documentSplitterFactory) {
        this.documentSplitterFactory = documentSplitterFactory == null
                ? new DocumentSplitterFactory()
                : documentSplitterFactory;
    }

    public List<CreateKnowledgeFragmentCommand> parse(String goodsId, String content) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
        if (!StringUtils.hasText(content)) {
            throw new AppException("0001", "content cannot be blank");
        }

        List<String> paragraphs = documentSplitterFactory.split(content);
        List<CreateKnowledgeFragmentCommand> fragments = new ArrayList<>();
        int rankNo = 1;
        for (String paragraph : paragraphs) {
            for (String chunk : splitLongParagraph(paragraph)) {
                CreateKnowledgeFragmentCommand command = new CreateKnowledgeFragmentCommand();
                command.setGoodsId(goodsId.trim());
                command.setContent(chunk);
                command.setRankNo(rankNo++);
                fragments.add(command);
            }
        }
        return fragments;
    }

    private List<String> splitLongParagraph(String paragraph) {
        if (paragraph.length() <= MAX_FRAGMENT_LENGTH) {
            return List.of(paragraph);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_FRAGMENT_LENGTH, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            start = end;
        }
        return chunks.stream()
                .filter(StringUtils::hasText)
                .toList();
    }
}
