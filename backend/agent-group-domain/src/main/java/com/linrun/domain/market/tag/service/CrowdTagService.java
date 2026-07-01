package com.linrun.domain.market.tag.service;

import com.linrun.domain.market.tag.adapter.CrowdTagRepository;
import com.linrun.domain.market.tag.model.CrowdTagJob;
import com.linrun.domain.market.tag.model.CrowdTagJobResult;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CrowdTagService {

    public static final int STATUS_INIT = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 3;

    private static final int TAG_TYPE_ORDER_COUNT = 1;
    private static final int TAG_TYPE_PAY_AMOUNT = 2;
    private static final Pattern RULE_TERM_PATTERN = Pattern.compile(
            "(orderCount|orders|payAmount|amount|paid)\\s*(>=|>|=|==)\\s*([A-Za-z0-9.]+)",
            Pattern.CASE_INSENSITIVE);

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
        if (isRuleDsl(job.getTagRule())) {
            return queryUsersByRuleDsl(job);
        }
        if (Integer.valueOf(TAG_TYPE_ORDER_COUNT).equals(job.getTagType())) {
            return crowdTagRepository.queryUserIdsByOrderCount(startTime, endTime, parseInt(job.getTagRule(), 1));
        }
        if (Integer.valueOf(TAG_TYPE_PAY_AMOUNT).equals(job.getTagType())) {
            return crowdTagRepository.queryUserIdsByPayAmount(startTime, endTime, parseDecimal(job.getTagRule()));
        }
        return crowdTagRepository.queryDistinctPaidUserIds(startTime, endTime);
    }

    private boolean isRuleDsl(String rule) {
        return StringUtils.hasText(rule)
                && (rule.contains("orderCount") || rule.contains("orders")
                || rule.contains("payAmount") || rule.contains("amount")
                || rule.contains("paid"));
    }

    private List<String> queryUsersByRuleDsl(CrowdTagJob job) {
        Set<String> union = new LinkedHashSet<>();
        String[] orGroups = job.getTagRule().split("\\s*\\|\\|\\s*");
        for (String orGroup : orGroups) {
            Set<String> intersection = null;
            for (String term : orGroup.split("\\s*&&\\s*")) {
                Set<String> users = new LinkedHashSet<>(queryUsersByRuleTerm(
                        term.trim(), job.getStatStartTime(), job.getStatEndTime()));
                intersection = intersection == null ? users : intersect(intersection, users);
            }
            if (intersection != null) {
                union.addAll(intersection);
            }
        }
        return List.copyOf(union);
    }

    private List<String> queryUsersByRuleTerm(String term, LocalDateTime startTime, LocalDateTime endTime) {
        Matcher matcher = RULE_TERM_PATTERN.matcher(term);
        if (!matcher.matches()) {
            throw new AppException("TAG_0003", "unsupported crowd tag rule term: " + term);
        }
        String field = matcher.group(1).toLowerCase();
        String operator = matcher.group(2);
        String value = matcher.group(3);
        if ("ordercount".equals(field) || "orders".equals(field)) {
            int threshold = parseInt(value, 1);
            return crowdTagRepository.queryUserIdsByOrderCount(
                    startTime, endTime, ">".equals(operator) ? threshold + 1 : threshold);
        }
        if ("payamount".equals(field) || "amount".equals(field)) {
            BigDecimal threshold = parseDecimal(value);
            return crowdTagRepository.queryUserIdsByPayAmount(
                    startTime, endTime, ">".equals(operator) ? threshold.add(new BigDecimal("0.01")) : threshold);
        }
        boolean expectedPaid = Boolean.parseBoolean(value) || "1".equals(value);
        return expectedPaid
                ? crowdTagRepository.queryDistinctPaidUserIds(startTime, endTime)
                : List.of();
    }

    private Set<String> intersect(Set<String> left, Set<String> right) {
        left.retainAll(right);
        return left;
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















