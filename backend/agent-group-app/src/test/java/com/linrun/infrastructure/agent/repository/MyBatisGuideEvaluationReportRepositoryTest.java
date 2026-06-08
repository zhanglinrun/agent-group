package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.quality.model.GuideEvaluationFeedback;
import com.linrun.domain.agent.quality.model.GuideEvaluationItemResult;
import com.linrun.domain.agent.quality.model.GuideEvaluationReport;
import com.linrun.infrastructure.dao.IGuideEvaluationReportDao;
import com.linrun.infrastructure.po.GuideEvaluationFeedbackPO;
import com.linrun.infrastructure.po.GuideEvaluationItemResultPO;
import com.linrun.infrastructure.po.GuideEvaluationReportPO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBatisGuideEvaluationReportRepositoryTest {

    @Test
    void shouldPersistReportWithItemsAndFeedbacks() {
        FakeGuideEvaluationReportDao dao = new FakeGuideEvaluationReportDao();
        MyBatisGuideEvaluationReportRepository repository = new MyBatisGuideEvaluationReportRepository(dao);

        GuideEvaluationReport report = report();
        repository.save(report);

        GuideEvaluationReport latest = repository.queryLatest().orElseThrow();
        assertEquals("EVAL20260518101010001", latest.getBatchNo());
        assertEquals(new BigDecimal("100.00"), latest.getRetrievalHitRate());
        assertEquals(1, latest.getItems().size());
        assertEquals("EV10001", latest.getItems().get(0).getCaseId());
        assertEquals(1, latest.getFeedbacks().size());
        assertEquals("PROMPT", latest.getFeedbacks().get(0).getTargetType());
    }

    @Test
    void shouldIgnoreBlankReport() {
        FakeGuideEvaluationReportDao dao = new FakeGuideEvaluationReportDao();
        MyBatisGuideEvaluationReportRepository repository = new MyBatisGuideEvaluationReportRepository(dao);

        repository.save(new GuideEvaluationReport());

        assertTrue(repository.queryLatest().isEmpty());
    }

    private GuideEvaluationReport report() {
        GuideEvaluationItemResult item = new GuideEvaluationItemResult();
        item.setCaseId("EV10001");
        item.setCaseName("student budget quota");
        item.setQuestion("limited budget quota package");
        item.setExpectedGoodsId("G10001");
        item.setActualGoodsId("G10001");
        item.setReferencePassed(true);
        item.setAnswerPassed(true);
        item.setRecommendationPassed(true);
        item.setContextPassed(true);
        item.setLatencyMillis(12L);
        item.setScore(100);
        item.setSuggestion("passed");

        GuideEvaluationReport report = new GuideEvaluationReport();
        report.setBatchNo("EVAL20260518101010001");
        report.setPromptVersion("guide-v1.0/self-check-v1.0");
        report.setKnowledgeVersion("v1");
        report.setTotalCount(1);
        report.setRetrievalHitRate(new BigDecimal("100.00"));
        report.setAnswerAccuracyRate(new BigDecimal("100.00"));
        report.setRecommendationReasonableRate(new BigDecimal("100.00"));
        report.setContextConsistencyRate(new BigDecimal("100.00"));
        report.setItems(List.of(item));
        report.setFeedbacks(List.of(new GuideEvaluationFeedback("PROMPT", "HIGH", "keep current prompt")));
        return report;
    }

    private static class FakeGuideEvaluationReportDao implements IGuideEvaluationReportDao {

        private GuideEvaluationReportPO report;
        private final List<GuideEvaluationItemResultPO> items = new ArrayList<>();
        private final List<GuideEvaluationFeedbackPO> feedbacks = new ArrayList<>();

        @Override
        public void insertReport(GuideEvaluationReportPO report) {
            this.report = report;
        }

        @Override
        public void insertItems(String batchNo, List<GuideEvaluationItemResultPO> items) {
            this.items.clear();
            this.items.addAll(items);
        }

        @Override
        public void insertFeedbacks(String batchNo, List<GuideEvaluationFeedbackPO> feedbacks) {
            this.feedbacks.clear();
            this.feedbacks.addAll(feedbacks);
        }

        @Override
        public GuideEvaluationReportPO queryLatestReport() {
            return report;
        }

        @Override
        public List<GuideEvaluationItemResultPO> queryItemsByBatchNo(String batchNo) {
            return items;
        }

        @Override
        public List<GuideEvaluationFeedbackPO> queryFeedbacksByBatchNo(String batchNo) {
            return feedbacks;
        }
    }
}
