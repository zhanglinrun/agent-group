package com.linrun.domain.market.tag.service;

import com.linrun.domain.market.tag.adapter.CrowdTagRepository;
import com.linrun.domain.market.tag.model.CrowdTagJob;
import com.linrun.domain.market.tag.model.CrowdTagJobResult;
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

    @Test
    void shouldExecuteRunnableTagJobsAndRefreshStatistics() {
        FakeCrowdTagRepository repository = new FakeCrowdTagRepository();
        CrowdTagJob job = new CrowdTagJob();
        job.setTagId("TAG_ORDER_2");
        job.setBatchId("BATCH_001");
        job.setTagType(1);
        job.setTagRule("2");
        repository.job = job;
        repository.runnableJobs = List.of(job);
        repository.orderCountUsers = List.of("U10001");
        CrowdTagService service = new CrowdTagService(repository);

        List<CrowdTagJobResult> results = service.execRunnableTagBatchJobs(20);
        CrowdTagJobResult refreshResult = service.refreshCrowdTagStatistics("TAG_ORDER_2");

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getMatchedCount());
        assertEquals(1, refreshResult.getMatchedCount());
        assertEquals("statistics refreshed", refreshResult.getMessage());
    }

    @Test
    void shouldExecuteDslCrowdTagRuleWithIntersectionAndUnion() {
        FakeCrowdTagRepository repository = new FakeCrowdTagRepository();
        CrowdTagJob job = new CrowdTagJob();
        job.setTagId("TAG_DSL");
        job.setBatchId("BATCH_DSL");
        job.setTagType(99);
        job.setTagRule("orderCount>=2 && payAmount>=500 || paid=true");
        repository.job = job;
        repository.orderCountUsers = List.of("U10001", "U10002");
        repository.payAmountUsers = List.of("U10002", "U10003");
        repository.paidUsers = List.of("U10004");

        CrowdTagJobResult result = new CrowdTagService(repository).execTagBatchJob("TAG_DSL", "BATCH_DSL");

        assertEquals(2, result.getMatchedCount());
        assertTrue(result.getUserIds().contains("U10002"));
        assertTrue(result.getUserIds().contains("U10004"));
        assertEquals(2, repository.statistics);
    }

    private static class FakeCrowdTagRepository implements CrowdTagRepository {

        private CrowdTagJob job;
        private List<CrowdTagJob> runnableJobs = List.of();
        private List<String> orderCountUsers = List.of();
        private List<String> payAmountUsers = List.of();
        private List<String> paidUsers = List.of();
        private int statistics;
        private final Set<String> details = new HashSet<>();
        private final List<Integer> statuses = new ArrayList<>();

        @Override
        public Optional<CrowdTagJob> queryJob(String tagId, String batchId) {
            return Optional.ofNullable(job);
        }

        @Override
        public List<CrowdTagJob> queryRunnableJobs(int limit) {
            return runnableJobs.stream().limit(limit).toList();
        }

        @Override
        public List<String> queryUserIdsByOrderCount(LocalDateTime startTime, LocalDateTime endTime, int minOrderCount) {
            return orderCountUsers;
        }

        @Override
        public List<String> queryUserIdsByPayAmount(LocalDateTime startTime, LocalDateTime endTime, BigDecimal minPayAmount) {
            return payAmountUsers;
        }

        @Override
        public List<String> queryDistinctPaidUserIds(LocalDateTime startTime, LocalDateTime endTime) {
            return paidUsers;
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















