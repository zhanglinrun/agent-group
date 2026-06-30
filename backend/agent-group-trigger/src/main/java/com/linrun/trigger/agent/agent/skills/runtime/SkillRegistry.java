package com.linrun.trigger.agent.agent.skills.runtime;

import java.util.List;

public interface SkillRegistry {

    List<SkillRuntimeDescriptor> availableSkills(String mode, String taskType);
}
