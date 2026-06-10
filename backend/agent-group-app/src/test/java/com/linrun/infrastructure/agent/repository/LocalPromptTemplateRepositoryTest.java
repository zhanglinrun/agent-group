package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.prompt.model.PromptTemplate;
import com.linrun.domain.agent.prompt.model.PromptTemplateType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPromptTemplateRepositoryTest {

    private final LocalPromptTemplateRepository repository = new LocalPromptTemplateRepository();

    @Test
    void shouldProvideAllEnabledPromptTemplates() {
        Set<PromptTemplateType> templateTypes = repository.queryEnabledTemplates().stream()
                .map(PromptTemplate::getTemplateType)
                .collect(Collectors.toSet());

        assertEquals(Set.of(PromptTemplateType.values()), templateTypes);
    }

    @Test
    void shouldProvideReadableGuideAndSelfCheckTemplates() {
        PromptTemplate guideTemplate = repository.queryEnabledByType(PromptTemplateType.GUIDE).orElseThrow();
        PromptTemplate selfCheckTemplate = repository.queryEnabledByType(PromptTemplateType.SELF_CHECK).orElseThrow();

        assertTrue(guideTemplate.getContent().contains("棰濆害璐拱涓庢櫤鑳戒綋浣跨敤鍔╂墜"));
        assertTrue(guideTemplate.getContent().contains("不要编造"));
        assertTrue(selfCheckTemplate.getContent().contains("回答前检查"));
        assertTrue(selfCheckTemplate.getContent().contains("资料待补充"));
    }
}















