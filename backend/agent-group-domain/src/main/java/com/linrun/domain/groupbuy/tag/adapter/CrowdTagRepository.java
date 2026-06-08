package com.linrun.domain.groupbuy.tag.adapter;

import com.linrun.domain.groupbuy.tag.model.CrowdTagJob;
import com.linrun.domain.groupbuy.tag.model.CrowdTag;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CrowdTagRepository {

    Optional<CrowdTagJob> queryJob(String tagId, String batchId);

    default List<CrowdTag> queryTagList(int limit) {
        return List.of();
    }

    List<CrowdTagJob> queryRunnableJobs(int limit);

    List<String> queryUserIdsByOrderCount(LocalDateTime startTime, LocalDateTime endTime, int minOrderCount);

    List<String> queryUserIdsByPayAmount(LocalDateTime startTime, LocalDateTime endTime, BigDecimal minPayAmount);

    List<String> queryDistinctPaidUserIds(LocalDateTime startTime, LocalDateTime endTime);

    int addCrowdTagUserId(String tagId, String userId);

    int countCrowdTagUsers(String tagId);

    void updateCrowdTagStatistics(String tagId, int statistics);

    void updateJobStatus(String tagId, String batchId, int status);
}
