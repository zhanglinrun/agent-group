package com.linrun.domain.tag.service;

import com.linrun.domain.tag.adapter.CrowdTagRepository;
import com.linrun.domain.tag.model.CrowdTagJob;
import com.linrun.domain.tag.model.CrowdTagJobResult;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CrowdTagService {

    public static final int STATUS_INIT = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 3;

    private static final int TAG_TYPE_ORDER_COUNT = 1;
    private static final int TAG_TYPE_PAY_AMOUNT = 2;

    private final CrowdTagRepository crowdTagRepository;

    public CrowdTagService(CrowdTagRepository crowdTagRepository) {
        this.crowdTagRepository = crowdTagRepository;
    }

    public CrowdTagJobResult execTagBatchJob(String tagId, String batchId) {
        if (!StringUtils.hasText(tagId) || !StringUtils.hasText(batchId)) {
            throw new AppException("TAG_0001", "tagId and batchId cannot be blank");
        }
        CrowdTagJob job = crowdTagRepository.queryJob(tagId, batchId)
                .orElseThrow(() -> new AppException("TAG_0002", "crowd tag job not found"));

        crowdTagRepository.updateJobStatus(tagId, batchId, STATUS_RUNNING);
        List<String> userIds = queryMatchedUsers(job);
        for (String userId : userIds) {
            crowdTagRepository.addCrowdTagUserId(tagId, userId);
        }
        int statistics = crowdTagRepository.countCrowdTagUsers(tagId);
        crowdTagRepository.updateCrowdTagStatistics(tagId, statistics);
        crowdTagRepository.updateJobStatus(tagId, batchId, STATUS_DONE);

        CrowdTagJobResult result = new CrowdTagJobResult();
        result.setTagId(tagId);
        result.setBatchId(batchId);
        result.setTagType(job.getTagType());
        result.setTagRule(job.getTagRule());
        result.setMatchedCount(userIds.size());
        result.setUserIds(userIds);
        result.setMessage("success");
        return result;
    }

    public List<CrowdTagJobResult> execRunnableTagBatchJobs(int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 100);
        return crowdTagRepository.queryRunnableJobs(safeLimit).stream()
                .map(job -> execTagBatchJob(job.getTagId(), job.getBatchId()))
                .toList();
    }

    public CrowdTagJobResult refreshCrowdTagStatistics(String tagId) {
        if (!StringUtils.hasText(tagId)) {
            throw new AppException("TAG_0001", "tagId cannot be blank");
        }
        int statistics = crowdTagRepository.countCrowdTagUsers(tagId);
        crowdTagRepository.updateCrowdTagStatistics(tagId, statistics);

        CrowdTagJobResult result = new CrowdTagJobResult();
        result.setTagId(tagId);
        result.setMatchedCount(statistics);
        result.setUserIds(List.of());
        result.setMessage("statistics refreshed");
        return result;
    }

    private List<String> queryMatchedUsers(CrowdTagJob job) {
        LocalDateTime startTime = job.getStatStartTime();
        LocalDateTime endTime = job.getStatEndTime();
        if (Integer.valueOf(TAG_TYPE_ORDER_COUNT).equals(job.getTagType())) {
            return crowdTagRepository.queryUserIdsByOrderCount(startTime, endTime, parseInt(job.getTagRule(), 1));
        }
        if (Integer.valueOf(TAG_TYPE_PAY_AMOUNT).equals(job.getTagType())) {
            return crowdTagRepository.queryUserIdsByPayAmount(startTime, endTime, parseDecimal(job.getTagRule()));
        }
        return crowdTagRepository.queryDistinctPaidUserIds(startTime, endTime);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
