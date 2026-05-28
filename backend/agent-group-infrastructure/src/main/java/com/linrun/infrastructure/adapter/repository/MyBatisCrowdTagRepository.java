package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.tag.adapter.CrowdTagRepository;
import com.linrun.domain.tag.model.CrowdTag;
import com.linrun.domain.tag.model.CrowdTagJob;
import com.linrun.infrastructure.converter.SupportPOConverter;
import com.linrun.infrastructure.dao.ICrowdTagDao;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisCrowdTagRepository implements CrowdTagRepository {

    private final ICrowdTagDao crowdTagDao;

    public MyBatisCrowdTagRepository(ICrowdTagDao crowdTagDao) {
        this.crowdTagDao = crowdTagDao;
    }

    @Override
    public Optional<CrowdTagJob> queryJob(String tagId, String batchId) {
        return Optional.ofNullable(SupportPOConverter.toEntity(crowdTagDao.queryJob(tagId, batchId)));
    }

    @Override
    public List<CrowdTag> queryTagList(int limit) {
        return SupportPOConverter.toCrowdTags(crowdTagDao.queryTagList(Math.max(1, limit)));
    }

    @Override
    public List<CrowdTagJob> queryRunnableJobs(int limit) {
        return SupportPOConverter.toCrowdTagJobs(crowdTagDao.queryRunnableJobs(Math.max(1, limit)));
    }

    @Override
    public List<String> queryUserIdsByOrderCount(LocalDateTime startTime, LocalDateTime endTime, int minOrderCount) {
        return crowdTagDao.queryUserIdsByOrderCount(startTime, endTime, minOrderCount);
    }

    @Override
    public List<String> queryUserIdsByPayAmount(LocalDateTime startTime, LocalDateTime endTime, BigDecimal minPayAmount) {
        return crowdTagDao.queryUserIdsByPayAmount(startTime, endTime, minPayAmount);
    }

    @Override
    public List<String> queryDistinctPaidUserIds(LocalDateTime startTime, LocalDateTime endTime) {
        return crowdTagDao.queryDistinctPaidUserIds(startTime, endTime);
    }

    @Override
    public int addCrowdTagUserId(String tagId, String userId) {
        return crowdTagDao.addCrowdTagUserId(tagId, userId);
    }

    @Override
    public int countCrowdTagUsers(String tagId) {
        return crowdTagDao.countCrowdTagUsers(tagId);
    }

    @Override
    public void updateCrowdTagStatistics(String tagId, int statistics) {
        crowdTagDao.updateCrowdTagStatistics(tagId, statistics);
    }

    @Override
    public void updateJobStatus(String tagId, String batchId, int status) {
        crowdTagDao.updateJobStatus(tagId, batchId, status);
    }
}
