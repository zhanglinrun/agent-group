package com.linrun.domain.tag.service;

import com.linrun.domain.tag.adapter.CrowdTagRepository;
import com.linrun.domain.tag.model.CrowdTagJob;
import com.linrun.domain.tag.model.CrowdTagJobResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowdTagServiceTest {

    @Test
    void shouldExecuteOrderCountCrowdTagJob() {
        FakeCrowdTagRepository repository = new FakeCrowdTagRepository();
        CrowdTagJob job = new CrowdTagJob();
        job.setTagId("TAG_ORDER_2");
        job.setBatchId("BATCH_001");
        job.setTagType(1);
        job.setTagRule("2");
        repository.job = job;
        repository.orderCountUsers = List.of("U10001", "U10002");

        CrowdTagJobResult result = new CrowdTagService(repository).execTagBatchJob("TAG_ORDER_2", "BATCH_001");

        assertEquals(2, result.getMatchedCount());
        assertEquals(2, repository.statistics);
        assertTrue(repository.statuses.contains(CrowdTagService.STATUS_RUNNING));
        assertTrue(repository.statuses.contains(CrowdTagService.STATUS_DONE));
    }

    private static class FakeCrowdTagRepository implements CrowdTagRepository {

        private CrowdTagJob job;
        private List<String> orderCountUsers = List.of();
        private int statistics;
        private final Set<String> details = new HashSet<>();
        private final List<Integer> statuses = new ArrayList<>();

        @Override
        public Optional<CrowdTagJob> queryJob(String tagId, String batchId) {
            return Optional.ofNullable(job);
        }

        @Override
        public List<String> queryUserIdsByOrderCount(LocalDateTime startTime, LocalDateTime endTime, int minOrderCount) {
            return orderCountUsers;
        }

        @Override
        public List<String> queryUserIdsByPayAmount(LocalDateTime startTime, LocalDateTime endTime, BigDecimal minPayAmount) {
            return List.of();
        }

        @Override
        public List<String> queryDistinctPaidUserIds(LocalDateTime startTime, LocalDateTime endTime) {
            return List.of();
        }

        @Override
        public int addCrowdTagUserId(String tagId, String userId) {
            details.add(tagId + ":" + userId);
            return 1;
        }

        @Override
        public int countCrowdTagUsers(String tagId) {
            return details.size();
        }

        @Override
        public void updateCrowdTagStatistics(String tagId, int statistics) {
            this.statistics = statistics;
        }

        @Override
        public void updateJobStatus(String tagId, String batchId, int status) {
            statuses.add(status);
        }
    }
}
