package com.linrun.domain.agent.runtime.artifact;

import com.linrun.domain.agent.model.AgentArtifact;

public class AgentToolArtifactBinding {

    private final AgentToolArtifactSource source;
    private final AgentArtifact artifact;

    public AgentToolArtifactBinding(AgentToolArtifactSource source, AgentArtifact artifact) {
        if (source == null) {
            throw new IllegalArgumentException("artifact source cannot be null");
        }
        if (artifact == null) {
            throw new IllegalArgumentException("artifact cannot be null");
        }
        this.source = source;
        this.artifact = artifact;
    }

    public AgentToolArtifactSource getSource() {
        return source;
    }

    public AgentArtifact getArtifact() {
        return artifact;
    }

    public boolean isInternalArtifact() {
        return "INTERNAL".equalsIgnoreCase(artifact.getArtifactType())
                || "INTERNAL".equalsIgnoreCase(artifact.getSourceType());
    }
}















