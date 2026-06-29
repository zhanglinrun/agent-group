package com.linrun.trigger.agent.agent.deepresearch.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentRoleContext(
        Role role,
        Map<String, Object> data,
        List<String> includedSections
) {

    public AgentRoleContext {
        data = data == null ? Map.of() : Map.copyOf(data);
        includedSections = includedSections == null ? List.of() : List.copyOf(includedSections);
    }

    public enum Role {
        PLANNER,
        WORKER,
        REVIEWER
    }

    public static Builder builder(Role role) {
        return new Builder(role);
    }

    public static class Builder {
        private final Role role;
        private final Map<String, Object> data = new LinkedHashMap<>();
        private final List<String> sections = new java.util.ArrayList<>();

        private Builder(Role role) {
            this.role = role;
        }

        public Builder put(String section, Object value) {
            if (section != null && !section.isBlank()) {
                sections.add(section.trim());
                data.put(section.trim(), value);
            }
            return this;
        }

        public AgentRoleContext build() {
            return new AgentRoleContext(role, data, sections);
        }
    }
}
