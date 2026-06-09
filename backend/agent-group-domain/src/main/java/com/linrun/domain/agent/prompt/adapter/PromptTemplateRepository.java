package com.linrun.domain.agent.prompt.adapter;

import com.linrun.domain.agent.prompt.model.PromptTemplate;
import com.linrun.domain.agent.prompt.model.PromptTemplateType;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepository {

    Optional<PromptTemplate> queryEnabledByType(PromptTemplateType templateType);

    List<PromptTemplate> queryEnabledTemplates();
}















