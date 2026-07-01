package com.linrun.domain.agent.runtime.artifact;

import com.linrun.domain.agent.model.AgentArtifact;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AgentToolArtifactRegistry {

    private final List<AgentToolArtifactBinding> bindings = new ArrayList<>();

    public synchronized AgentToolArtifactBinding registerGeneratedArtifact(AgentToolArtifactSource source,
                                                                             AgentArtifact artifact) {
        AgentToolArtifactBinding binding = new AgentToolArtifactBinding(source, artifact);
        applySourceMetadata(source, artifact);
        if (!contains(binding)) {
            bindings.add(binding);
        }
        return binding;
    }

    public synchronized List<AgentToolArtifactBinding> listBindings() {
        return new ArrayList<>(bindings);
    }

    public synchronized List<AgentToolArtifactBinding> listVisibleBindings() {
        return bindings.stream()
                .filter(binding -> !binding.isInternalArtifact())
                .toList();
    }

    public synchronized List<AgentToolArtifactBinding> findByToolInvocationId(String toolInvocationId) {
        if (!StringUtils.hasText(toolInvocationId)) {
            return List.of();
        }
        return bindings.stream()
                .filter(binding -> Objects.equals(toolInvocationId, binding.getSource().getToolInvocationId()))
                .toList();
    }

    private void applySourceMetadata(AgentToolArtifactSource source, AgentArtifact artifact) {
        if (!StringUtils.hasText(artifact.getRunId())) {
            artifact.setRunId(source.getRunId());
        }
        if (!StringUtils.hasText(artifact.getToolInvocationId())) {
            artifact.setToolInvocationId(source.getToolInvocationId());
        }
        if (!StringUtils.hasText(artifact.getSourceType())) {
            artifact.setSourceType(source.getSourceType());
        }
        if (!StringUtils.hasText(artifact.getSourceName())) {
            artifact.setSourceName(source.getSourceName());
        }
    }

    private boolean contains(AgentToolArtifactBinding candidate) {
        return bindings.stream().anyMatch(existing -> sameBinding(existing, candidate));
    }

    private boolean sameBinding(AgentToolArtifactBinding left, AgentToolArtifactBinding right) {
        return Objects.equals(left.getSource().getRunId(), right.getSource().getRunId())
                && Objects.equals(left.getSource().getToolInvocationId(), right.getSource().getToolInvocationId())
                && sameArtifact(left.getArtifact(), right.getArtifact());
    }

    private boolean sameArtifact(AgentArtifact left, AgentArtifact right) {
        return Objects.equals(left.getArtifactId(), right.getArtifactId())
                && Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getDownloadUrl(), right.getDownloadUrl())
                && Objects.equals(left.getArtifactType(), right.getArtifactType());
    }
}















