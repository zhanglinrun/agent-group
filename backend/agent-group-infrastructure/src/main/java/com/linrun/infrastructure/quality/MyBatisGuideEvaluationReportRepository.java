package com.linrun.infrastructure.quality;

import com.linrun.domain.quality.adapter.GuideEvaluationReportRepository;
import com.linrun.domain.quality.model.GuideEvaluationReport;
import com.linrun.infrastructure.dao.IGuideEvaluationReportDao;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
public class MyBatisGuideEvaluationReportRepository implements GuideEvaluationReportRepository {

    private final IGuideEvaluationReportDao guideEvaluationReportDao;

    public MyBatisGuideEvaluationReportRepository(IGuideEvaluationReportDao guideEvaluationReportDao) {
        this.guideEvaluationReportDao = guideEvaluationReportDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(GuideEvaluationReport report) {
        if (report == null || !StringUtils.hasText(report.getBatchNo())) {
            return;
        }
        guideEvaluationReportDao.insertReport(report);
        if (report.getItems() != null && !report.getItems().isEmpty()) {
            guideEvaluationReportDao.insertItems(report.getBatchNo(), report.getItems());
        }
        if (report.getFeedbacks() != null && !report.getFeedbacks().isEmpty()) {
            guideEvaluationReportDao.insertFeedbacks(report.getBatchNo(), report.getFeedbacks());
        }
    }

    @Override
    public Optional<GuideEvaluationReport> queryLatest() {
        GuideEvaluationReport report = guideEvaluationReportDao.queryLatestReport();
        if (report == null || !StringUtils.hasText(report.getBatchNo())) {
            return Optional.empty();
        }
        report.setItems(guideEvaluationReportDao.queryItemsByBatchNo(report.getBatchNo()));
        report.setFeedbacks(guideEvaluationReportDao.queryFeedbacksByBatchNo(report.getBatchNo()));
        return Optional.of(report);
    }
}
