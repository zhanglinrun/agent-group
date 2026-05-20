package com.linrun.infrastructure.dao;

import com.linrun.domain.quality.model.GuideEvaluationFeedback;
import com.linrun.domain.quality.model.GuideEvaluationItemResult;
import com.linrun.domain.quality.model.GuideEvaluationReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideEvaluationReportDao {

    void insertReport(GuideEvaluationReport report);

    void insertItems(@Param("batchNo") String batchNo, @Param("items") List<GuideEvaluationItemResult> items);

    void insertFeedbacks(@Param("batchNo") String batchNo, @Param("feedbacks") List<GuideEvaluationFeedback> feedbacks);

    GuideEvaluationReport queryLatestReport();

    List<GuideEvaluationItemResult> queryItemsByBatchNo(@Param("batchNo") String batchNo);

    List<GuideEvaluationFeedback> queryFeedbacksByBatchNo(@Param("batchNo") String batchNo);
}
