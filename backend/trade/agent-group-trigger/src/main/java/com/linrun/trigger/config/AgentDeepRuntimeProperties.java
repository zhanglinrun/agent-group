package com.linrun.trigger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "agent.deep", ignoreInvalidFields = true)
public class AgentDeepRuntimeProperties {

    private boolean autoSaveMemory = false;
    private boolean requireReferencesForFacts = true;
    private boolean injectAutoMemory = false;
    private List<String> researchKeywords = defaultResearchKeywords();

    public boolean isAutoSaveMemory() {
        return autoSaveMemory;
    }

    public void setAutoSaveMemory(boolean autoSaveMemory) {
        this.autoSaveMemory = autoSaveMemory;
    }

    public boolean isRequireReferencesForFacts() {
        return requireReferencesForFacts;
    }

    public void setRequireReferencesForFacts(boolean requireReferencesForFacts) {
        this.requireReferencesForFacts = requireReferencesForFacts;
    }

    public boolean isInjectAutoMemory() {
        return injectAutoMemory;
    }

    public void setInjectAutoMemory(boolean injectAutoMemory) {
        this.injectAutoMemory = injectAutoMemory;
    }

    public List<String> getResearchKeywords() {
        return researchKeywords;
    }

    public void setResearchKeywords(List<String> researchKeywords) {
        this.researchKeywords = researchKeywords == null || researchKeywords.isEmpty()
                ? defaultResearchKeywords()
                : List.copyOf(researchKeywords);
    }

    private static List<String> defaultResearchKeywords() {
        return List.of(
                "论文", "综述", "文献", "arxiv", "ieee", "icassp", "globecom",
                "发展历程", "研究现状", "survey", "paper", "doi", "顶会"
        );
    }
}
