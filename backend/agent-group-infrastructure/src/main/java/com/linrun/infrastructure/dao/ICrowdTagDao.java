package com.linrun.infrastructure.dao;

import com.linrun.infrastructure.po.CrowdTagJobPO;
import com.linrun.infrastructure.po.CrowdTagPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ICrowdTagDao {

    CrowdTagJobPO queryJob(@Param("tagId") String tagId, @Param("batchId") String batchId);

    List<CrowdTagPO> queryTagList(@Param("limit") int limit);

    List<CrowdTagJobPO> queryRunnableJobs(@Param("limit") int limit);

    List<String> queryUserIdsByOrderCount(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime,
                                          @Param("minOrderCount") int minOrderCount);

    List<String> queryUserIdsByPayAmount(@Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("minPayAmount") BigDecimal minPayAmount);

    List<String> queryDistinctPaidUserIds(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    int addCrowdTagUserId(@Param("tagId") String tagId, @Param("userId") String userId);

    int countCrowdTagUsers(@Param("tagId") String tagId);

    boolean isTagCrowdRange(@Param("tagId") String tagId, @Param("userId") String userId);

    void updateCrowdTagStatistics(@Param("tagId") String tagId, @Param("statistics") int statistics);

    void updateJobStatus(@Param("tagId") String tagId,
                         @Param("batchId") String batchId,
                         @Param("status") int status);
}















