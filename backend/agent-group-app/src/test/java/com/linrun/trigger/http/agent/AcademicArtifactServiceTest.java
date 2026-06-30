package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicMessage;
import com.linrun.domain.academic.model.AcademicSession;
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

class AcademicArtifactServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAnswerReportPersistsManifestRecordAndDownloadableFile() throws Exception {
        FakeAcademicAgentRepository repository = new FakeAcademicAgentRepository();
        AcademicArtifactService service = new AcademicArtifactService(new ObjectMapper(), repository);
        ReflectionTestUtils.setField(service, "outputDirectory", tempDir.resolve("outputs").toString());
        ReflectionTestUtils.setField(service, "skillsDirectory", tempDir.resolve("skills").toString());

        AcademicSessionDetailResponse.Artifact artifact = service.saveAnswerReport(
                "U1001", "S1001", "RUN-1001", "秋招 Agent 优化", "最终回答内容");

        assertNotNull(artifact);
        assertEquals("MD", artifact.getArtifactType());
        assertEquals("RUN-1001", artifact.getRunId());
        assertEquals("AGENT", artifact.getSourceType());
        assertEquals("report_generation", artifact.getSourceName());
        assertTrue(artifact.getFileName().startsWith("deep-report-RUN-1001"));

        List<AcademicSessionDetailResponse.Artifact> manifest = service.loadManifest("U1001", "S1001");
        assertEquals(1, manifest.size());
        assertEquals(artifact.getArtifactId(), manifest.getFirst().getArtifactId());

        AcademicArtifact saved = repository.artifacts.getFirst();
        assertEquals("U1001", saved.getUserId());
        assertEquals("S1001", saved.getSessionId());
        assertEquals("RUN-1001", saved.getRunId());
        assertEquals("report_generation", saved.getSourceName());

        AcademicArtifactService.DownloadArtifact download = service.resolveDownload(artifact.getArtifactId());
        assertEquals(artifact.getFileName(), download.fileName());
        assertTrue(Files.readString(download.path()).contains("最终回答内容"));
    }

    private static class FakeAcademicAgentRepository implements AcademicAgentRepository {

        private final List<AcademicArtifact> artifacts = new ArrayList<>();

        @Override
        public void saveSessionIfAbsent(AcademicSession session) {
        }

        @Override
        public void updateSession(AcademicSession session) {
        }

        @Override
        public void saveMessage(AcademicMessage message) {
        }

        @Override
        public List<AcademicMessage> queryMessages(String userId, String sessionId) {
            return List.of();
        }

        @Override
        public Optional<AcademicSession> querySession(String userId, String sessionId) {
            return Optional.empty();
        }

        @Override
        public List<AcademicSession> querySessions(String userId, int limit) {
            return List.of();
        }

        @Override
        public void deleteSession(String userId, String sessionId) {
        }

        @Override
        public void saveFile(AcademicFile file) {
        }

        @Override
        public Optional<AcademicFile> queryFile(String userId, String fileId) {
            return Optional.empty();
        }

        @Override
        public List<AcademicFile> queryFiles(String userId, int limit) {
            return List.of();
        }

        @Override
        public List<AcademicFile> queryFilesBySession(String userId, String sessionId) {
            return List.of();
        }

        @Override
        public void deleteFile(String userId, String fileId) {
        }

        @Override
        public void saveArtifact(AcademicArtifact artifact) {
            artifacts.add(artifact);
        }

        @Override
        public List<AcademicArtifact> queryArtifacts(String userId, String sessionId) {
            return artifacts.stream()
                    .filter(artifact -> userId.equals(artifact.getUserId()))
                    .filter(artifact -> sessionId.equals(artifact.getSessionId()))
                    .toList();
        }
    }
}
