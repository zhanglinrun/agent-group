package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.quality.adapter.GuideEvaluationReportRepository;
import com.linrun.domain.agent.quality.model.GuideEvaluationReport;
import com.linrun.infrastructure.converter.AgentPOConverter;
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
        guideEvaluationReportDao.insertReport(AgentPOConverter.toPO(report));
        if (report.getItems() != null && !report.getItems().isEmpty()) {
            guideEvaluationReportDao.insertItems(report.getBatchNo(),
                    AgentPOConverter.toGuideEvaluationItemPOList(report.getItems()));
        }
        if (report.getFeedbacks() != null && !report.getFeedbacks().isEmpty()) {
            guideEvaluationReportDao.insertFeedbacks(report.getBatchNo(),
                    AgentPOConverter.toGuideEvaluationFeedbackPOList(report.getFeedbacks()));
        }
    }

    @Override
    public Optional<GuideEvaluationReport> queryLatest() {
        GuideEvaluationReport report = AgentPOConverter.toEntity(guideEvaluationReportDao.queryLatestReport());
        if (report == null || !StringUtils.hasText(report.getBatchNo())) {
            return Optional.empty();
        }
        report.setItems(AgentPOConverter.toGuideEvaluationItems(
                guideEvaluationReportDao.queryItemsByBatchNo(report.getBatchNo())));
        report.setFeedbacks(AgentPOConverter.toGuideEvaluationFeedbacks(
                guideEvaluationReportDao.queryFeedbacksByBatchNo(report.getBatchNo())));
        return Optional.of(report);
    }
}
