package com.linrun.domain.agent.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class AgentSkill {

    private String skillId;
    private String skillName;
    private String goal;
    private String guardrail;
    private List<String> allowedTools = new ArrayList<>();

    public AgentSkill() {
    }

    public AgentSkill(String skillId, String skillName, String goal, String guardrail, List<String> allowedTools) {
        this.skillId = skillId;
        this.skillName = skillName;
        this.goal = goal;
        this.guardrail = guardrail;
        this.allowedTools = allowedTools == null ? new ArrayList<>() : new ArrayList<>(allowedTools);
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getGuardrail() {
        return guardrail;
    }

    public void setGuardrail(String guardrail) {
        this.guardrail = guardrail;
    }

    public List<String> getAllowedTools() {
        return allowedTools;
    }

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools == null ? new ArrayList<>() : new ArrayList<>(allowedTools);
    }
}
