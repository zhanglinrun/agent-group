package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.GuideEvaluationFeedbackPO;
import com.linrun.infrastructure.po.GuideEvaluationItemResultPO;
import com.linrun.infrastructure.po.GuideEvaluationReportPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IGuideEvaluationReportDao {

    void insertReport(GuideEvaluationReportPO report);

    void insertItems(@Param("batchNo") String batchNo, @Param("items") List<GuideEvaluationItemResultPO> items);

    void insertFeedbacks(@Param("batchNo") String batchNo, @Param("feedbacks") List<GuideEvaluationFeedbackPO> feedbacks);

    GuideEvaluationReportPO queryLatestReport();

    List<GuideEvaluationItemResultPO> queryItemsByBatchNo(@Param("batchNo") String batchNo);

    List<GuideEvaluationFeedbackPO> queryFeedbacksByBatchNo(@Param("batchNo") String batchNo);
}
