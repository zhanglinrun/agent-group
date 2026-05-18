package com.linrun.infrastructure.evaluate;

import com.linrun.domain.evaluate.model.GuideEvaluationFeedback;
import com.linrun.domain.evaluate.model.GuideEvaluationItemResult;
import com.linrun.domain.evaluate.model.GuideEvaluationReport;
import com.linrun.infrastructure.dao.IGuideEvaluationReportDao;
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
        item.setCaseName("学生预算导购");
        item.setQuestion("预算有限买学习平板");
        item.setExpectedGoodsId("G10001");
        item.setActualGoodsId("G10001");
        item.setReferencePassed(true);
        item.setAnswerPassed(true);
        item.setRecommendationPassed(true);
        item.setContextPassed(true);
        item.setLatencyMillis(12L);
        item.setScore(100);
        item.setSuggestion("通过");

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
        report.setFeedbacks(List.of(new GuideEvaluationFeedback("PROMPT", "HIGH", "保留当前提示词")));
        return report;
    }

    private static class FakeGuideEvaluationReportDao implements IGuideEvaluationReportDao {

        private GuideEvaluationReport report;
        private final List<GuideEvaluationItemResult> items = new ArrayList<>();
        private final List<GuideEvaluationFeedback> feedbacks = new ArrayList<>();

        @Override
        public void insertReport(GuideEvaluationReport report) {
            this.report = report;
        }

        @Override
        public void insertItems(String batchNo, List<GuideEvaluationItemResult> items) {
            this.items.clear();
            this.items.addAll(items);
        }

        @Override
        public void insertFeedbacks(String batchNo, List<GuideEvaluationFeedback> feedbacks) {
            this.feedbacks.clear();
            this.feedbacks.addAll(feedbacks);
        }

        @Override
        public GuideEvaluationReport queryLatestReport() {
            return report;
        }

        @Override
        public List<GuideEvaluationItemResult> queryItemsByBatchNo(String batchNo) {
            return items;
        }

        @Override
        public List<GuideEvaluationFeedback> queryFeedbacksByBatchNo(String batchNo) {
            return feedbacks;
        }
    }
}
