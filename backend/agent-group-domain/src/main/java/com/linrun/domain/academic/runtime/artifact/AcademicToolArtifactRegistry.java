package com.linrun.domain.academic.runtime.artifact;

import com.linrun.domain.academic.model.AcademicArtifact;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AcademicToolArtifactRegistry {

    private final List<AcademicToolArtifactBinding> bindings = new ArrayList<>();

    public synchronized AcademicToolArtifactBinding registerGeneratedArtifact(AcademicToolArtifactSource source,
                                                                             AcademicArtifact artifact) {
        AcademicToolArtifactBinding binding = new AcademicToolArtifactBinding(source, artifact);
        applySourceMetadata(source, artifact);
        if (!contains(binding)) {
            bindings.add(binding);
        }
        return binding;
    }

    public synchronized List<AcademicToolArtifactBinding> listBindings() {
        return new ArrayList<>(bindings);
    }

    public synchronized List<AcademicToolArtifactBinding> listVisibleBindings() {
        return bindings.stream()
                .filter(binding -> !binding.isInternalArtifact())
                .toList();
    }

    public synchronized List<AcademicToolArtifactBinding> findByToolInvocationId(String toolInvocationId) {
        if (!StringUtils.hasText(toolInvocationId)) {
            return List.of();
        }
        return bindings.stream()
                .filter(binding -> Objects.equals(toolInvocationId, binding.getSource().getToolInvocationId()))
                .toList();
    }

    private void applySourceMetadata(AcademicToolArtifactSource source, AcademicArtifact artifact) {
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

    private boolean contains(AcademicToolArtifactBinding candidate) {
        return bindings.stream().anyMatch(existing -> sameBinding(existing, candidate));
    }

    private boolean sameBinding(AcademicToolArtifactBinding left, AcademicToolArtifactBinding right) {
        return Objects.equals(left.getSource().getRunId(), right.getSource().getRunId())
                && Objects.equals(left.getSource().getToolInvocationId(), right.getSource().getToolInvocationId())
                && sameArtifact(left.getArtifact(), right.getArtifact());
    }

    private boolean sameArtifact(AcademicArtifact left, AcademicArtifact right) {
        return Objects.equals(left.getArtifactId(), right.getArtifactId())
                && Objects.equals(left.getTitle(), right.getTitle())
                && Objects.equals(left.getDownloadUrl(), right.getDownloadUrl())
                && Objects.equals(left.getArtifactType(), right.getArtifactType());
    }
}
