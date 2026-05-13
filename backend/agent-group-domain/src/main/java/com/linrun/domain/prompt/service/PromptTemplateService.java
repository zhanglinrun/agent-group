package com.linrun.domain.prompt.service;

import com.linrun.domain.prompt.adapter.PromptTemplateRepository;
import com.linrun.domain.prompt.model.PromptTemplate;
import com.linrun.domain.prompt.model.PromptTemplateType;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplateService {

    private final PromptTemplateRepository promptTemplateRepository;

    public PromptTemplateService(PromptTemplateRepository promptTemplateRepository) {
        this.promptTemplateRepository = promptTemplateRepository;
    }

    public PromptTemplate requireEnabled(PromptTemplateType templateType) {
        return promptTemplateRepository.queryEnabledByType(templateType)
                .orElseThrow(() -> new AppException("PROMPT_0001", "enabled prompt template not found"));
    }

    public List<PromptTemplate> queryEnabledTemplates() {
        return promptTemplateRepository.queryEnabledTemplates();
    }
}
