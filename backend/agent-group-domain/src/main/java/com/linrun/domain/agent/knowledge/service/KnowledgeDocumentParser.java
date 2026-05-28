package com.linrun.domain.agent.knowledge.service;

import com.linrun.domain.agent.knowledge.model.CreateKnowledgeFragmentCommand;
import com.linrun.domain.agent.knowledge.service.splitter.DocumentSplitterFactory;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeDocumentParser {

    private static final int MAX_FRAGMENT_LENGTH = 500;
    private static final String CHUNK_TYPE_PARENT = "PARENT";
    private static final String CHUNK_TYPE_CHILD = "CHILD";

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
        int paragraphNo = 1;
        for (String paragraph : paragraphs) {
            List<String> chunks = splitLongParagraph(paragraph);
            if (chunks.size() <= 1) {
                fragments.add(fragment(goodsId, paragraph, rankNo++, null,
                        "BRO-" + paragraphNo, 1, 1, CHUNK_TYPE_CHILD, true));
            } else {
                String parentKey = "PARENT-" + paragraphNo;
                String brotherGroupId = "BRO-" + paragraphNo;
                fragments.add(fragment(goodsId, paragraph, rankNo++, parentKey,
                        brotherGroupId, 0, chunks.size(), CHUNK_TYPE_PARENT, false));
                for (int i = 0; i < chunks.size(); i++) {
                    fragments.add(fragment(goodsId, chunks.get(i), rankNo++, parentKey,
                            brotherGroupId, i + 1, chunks.size(), CHUNK_TYPE_CHILD, true));
                }
            }
            paragraphNo++;
        }
        return fragments;
    }

    private CreateKnowledgeFragmentCommand fragment(String goodsId,
                                                    String content,
                                                    int rankNo,
                                                    String parentKey,
                                                    String brotherGroupId,
                                                    int brotherIndex,
                                                    int brotherTotal,
                                                    String chunkType,
                                                    boolean embeddingEnabled) {
        CreateKnowledgeFragmentCommand command = new CreateKnowledgeFragmentCommand();
        command.setGoodsId(goodsId.trim());
        command.setContent(content);
        command.setRankNo(rankNo);
        command.setParentKey(parentKey);
        command.setBrotherGroupId(brotherGroupId);
        command.setBrotherIndex(brotherIndex);
        command.setBrotherTotal(brotherTotal);
        command.setChunkType(chunkType);
        command.setEmbeddingEnabled(embeddingEnabled);
        return command;
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
