package com.linrun.domain.agent.runtime.artifact;

import com.linrun.domain.agent.model.AgentArtifact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentToolArtifactRegistryTest {

    @Test
    void shouldRegisterArtifactWithSourceMetadataAndDeduplicate() {
        AgentToolArtifactRegistry registry = new AgentToolArtifactRegistry();
        AgentToolArtifactSource source = AgentToolArtifactSource.of("RUN1001", "TOOL1001", "report_tool");
        AgentArtifact artifact = artifact("A1001", "REPORT", "研究报告", "/artifacts/A1001");

        registry.registerGeneratedArtifact(source, artifact);
        registry.registerGeneratedArtifact(source, artifact);

        assertEquals(1, registry.listBindings().size());
        assertEquals("RUN1001", artifact.getRunId());
        assertEquals("TOOL1001", artifact.getToolInvocationId());
        assertEquals("TOOL", artifact.getSourceType());
        assertEquals("report_tool", artifact.getSourceName());
        assertEquals(1, registry.findByToolInvocationId("TOOL1001").size());
    }

    @Test
    void shouldFilterInternalArtifactsFromVisibleList() {
        AgentToolArtifactRegistry registry = new AgentToolArtifactRegistry();
        AgentToolArtifactSource source = AgentToolArtifactSource.of("RUN1002", "TOOL1002", "code_interpreter");

        registry.registerGeneratedArtifact(source, artifact("A2001", "REPORT", "可见报告", "/artifacts/A2001"));
        registry.registerGeneratedArtifact(source, artifact("A2002", "INTERNAL", "内部临时文件", "/artifacts/A2002"));

        assertEquals(2, registry.listBindings().size());
        assertEquals(1, registry.listVisibleBindings().size());
        assertEquals("A2001", registry.listVisibleBindings().getFirst().getArtifact().getArtifactId());
    }

    private AgentArtifact artifact(String artifactId, String artifactType, String title, String downloadUrl) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setArtifactType(artifactType);
        artifact.setTitle(title);
        artifact.setDownloadUrl(downloadUrl);
        return artifact;
    }
}















