package com.linrun.domain.academic.runtime.artifact;

import com.linrun.domain.academic.model.AcademicArtifact;

public class AcademicToolArtifactBinding {

    private final AcademicToolArtifactSource source;
    private final AcademicArtifact artifact;

    public AcademicToolArtifactBinding(AcademicToolArtifactSource source, AcademicArtifact artifact) {
        if (source == null) {
            throw new IllegalArgumentException("artifact source cannot be null");
        }
        if (artifact == null) {
            throw new IllegalArgumentException("artifact cannot be null");
        }
        this.source = source;
        this.artifact = artifact;
    }

    public AcademicToolArtifactSource getSource() {
        return source;
    }

    public AcademicArtifact getArtifact() {
        return artifact;
    }

    public boolean isInternalArtifact() {
        return "INTERNAL".equalsIgnoreCase(artifact.getArtifactType())
                || "INTERNAL".equalsIgnoreCase(artifact.getSourceType());
    }
}
