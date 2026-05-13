package com.linrun.domain.prompt.adapter;

import com.linrun.domain.prompt.model.PromptTemplate;
import com.linrun.domain.prompt.model.PromptTemplateType;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepository {

    Optional<PromptTemplate> queryEnabledByType(PromptTemplateType templateType);

    List<PromptTemplate> queryEnabledTemplates();
}
