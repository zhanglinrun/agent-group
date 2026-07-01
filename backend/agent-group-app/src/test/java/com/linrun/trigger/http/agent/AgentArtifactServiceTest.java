package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentSessionDetailResponse;
import com.linrun.domain.agent.adapter.AgentRepository;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.model.AgentFile;
import com.linrun.domain.agent.model.AgentMessage;
import com.linrun.domain.agent.model.AgentSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAnswerReportPersistsManifestRecordAndDownloadableFile() throws Exception {
        FakeAgentRepository repository = new FakeAgentRepository();
        AgentArtifactService service = new AgentArtifactService(new ObjectMapper(), repository);
        ReflectionTestUtils.setField(service, "outputDirectory", tempDir.resolve("outputs").toString());
        ReflectionTestUtils.setField(service, "skillsDirectory", tempDir.resolve("skills").toString());

        AgentSessionDetailResponse.Artifact artifact = service.saveAnswerReport(
                "U1001", "S1001", "RUN-1001", "秋招 Agent 优化", "最终回答内容");

        assertNotNull(artifact);
        assertEquals("MD", artifact.getArtifactType());
        assertEquals("RUN-1001", artifact.getRunId());
        assertEquals("AGENT", artifact.getSourceType());
        assertEquals("report_generation", artifact.getSourceName());
        assertTrue(artifact.getFileName().startsWith("deep-report-RUN-1001"));

        List<AgentSessionDetailResponse.Artifact> manifest = service.loadManifest("U1001", "S1001");
        assertEquals(1, manifest.size());
        assertEquals(artifact.getArtifactId(), manifest.getFirst().getArtifactId());

        AgentArtifact saved = repository.artifacts.getFirst();
        assertEquals("U1001", saved.getUserId());
        assertEquals("S1001", saved.getSessionId());
        assertEquals("RUN-1001", saved.getRunId());
        assertEquals("report_generation", saved.getSourceName());

        AgentArtifactService.DownloadArtifact download = service.resolveDownload(artifact.getArtifactId());
        assertEquals(artifact.getFileName(), download.fileName());
        assertTrue(Files.readString(download.path()).contains("最终回答内容"));
    }

    private static class FakeAgentRepository implements AgentRepository {

        private final List<AgentArtifact> artifacts = new ArrayList<>();

        @Override
        public void saveSessionIfAbsent(AgentSession session) {
        }

        @Override
        public void updateSession(AgentSession session) {
        }

        @Override
        public void saveMessage(AgentMessage message) {
        }

        @Override
        public List<AgentMessage> queryMessages(String userId, String sessionId) {
            return List.of();
        }

        @Override
        public Optional<AgentSession> querySession(String userId, String sessionId) {
            return Optional.empty();
        }

        @Override
        public List<AgentSession> querySessions(String userId, int limit) {
            return List.of();
        }

        @Override
        public void deleteSession(String userId, String sessionId) {
        }

        @Override
        public void saveFile(AgentFile file) {
        }

        @Override
        public Optional<AgentFile> queryFile(String userId, String fileId) {
            return Optional.empty();
        }

        @Override
        public List<AgentFile> queryFiles(String userId, int limit) {
            return List.of();
        }

        @Override
        public List<AgentFile> queryFilesBySession(String userId, String sessionId) {
            return List.of();
        }

        @Override
        public void deleteFile(String userId, String fileId) {
        }

        @Override
        public void saveArtifact(AgentArtifact artifact) {
            artifacts.add(artifact);
        }

        @Override
        public List<AgentArtifact> queryArtifacts(String userId, String sessionId) {
            return artifacts.stream()
                    .filter(artifact -> userId.equals(artifact.getUserId()))
                    .filter(artifact -> sessionId.equals(artifact.getSessionId()))
                    .toList();
        }
    }
}
