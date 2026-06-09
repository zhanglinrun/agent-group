package com.linrun.domain.agent.prompt.service;

import com.linrun.domain.agent.prompt.adapter.PromptTemplateRepository;
import com.linrun.domain.agent.prompt.model.PromptTemplate;
import com.linrun.domain.agent.prompt.model.PromptTemplateType;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptTemplateServiceTest {

    @Test
    void shouldReturnEnabledTemplateByType() {
        PromptTemplateService service = new PromptTemplateService(new FakePromptTemplateRepository());

        PromptTemplate template = service.requireEnabled(PromptTemplateType.GUIDE);

        assertEquals("guide-v1.0", template.getTemplateVersion());
        assertEquals("Agent 额度包提示模�?, template.getContent());
    }

    @Test
    void shouldThrowWhenTemplateMissing() {
        PromptTemplateService service = new PromptTemplateService(new EmptyPromptTemplateRepository());

        AppException exception = assertThrows(AppException.class,
                () -> service.requireEnabled(PromptTemplateType.SELF_CHECK));

        assertEquals("PROMPT_0001", exception.getCode());
    }

    private static class FakePromptTemplateRepository implements PromptTemplateRepository {

        @Override
        public Optional<PromptTemplate> queryEnabledByType(PromptTemplateType templateType) {
            if (PromptTemplateType.GUIDE.equals(templateType)) {
                return Optional.of(PromptTemplate.enabled("PT10001", templateType, "guide-v1.0", "Agent 额度包提示模�?));
            }
            return Optional.empty();
        }

        @Override
        public List<PromptTemplate> queryEnabledTemplates() {
            return List.of(PromptTemplate.enabled("PT10001", PromptTemplateType.GUIDE, "guide-v1.0", "Agent 额度包提示模�?));
        }
    }

    private static class EmptyPromptTemplateRepository implements PromptTemplateRepository {

        @Override
        public Optional<PromptTemplate> queryEnabledByType(PromptTemplateType templateType) {
            return Optional.empty();
        }

        @Override
        public List<PromptTemplate> queryEnabledTemplates() {
            return List.of();
        }
    }
}















