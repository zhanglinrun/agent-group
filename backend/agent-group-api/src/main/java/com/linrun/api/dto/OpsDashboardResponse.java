package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class OpsDashboardResponse implements Serializable {

    private List<ActivityItem> activities = new ArrayList<>();
    private List<ChannelItem> channels = new ArrayList<>();
    private List<CrowdTagItem> crowdTags = new ArrayList<>();
    private List<StockItem> stocks = new ArrayList<>();
    private List<NotifyTaskItem> notifyTasks = new ArrayList<>();

    @Data
    public static class ActivityItem implements Serializable {
        private String activityId;
        private String activityName;
        private String goodsId;
        private BigDecimal groupPrice;
        private Integer teamSize;
        private Integer status;
        private Boolean enabled;
        private String tagId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Data
    public static class ChannelItem implements Serializable {
        private String source;
        private String channel;
        private String goodsId;
        private String goodsName;
        private String activityId;
        private BigDecimal originalPrice;
    }

    @Data
    public static class CrowdTagItem implements Serializable {
        private String tagId;
        private String tagName;
        private String tagDesc;
        private Integer statistics;
        private String latestBatchId;
        private Integer latestJobStatus;
        private LocalDateTime updateTime;
    }

    @Data
    public static class StockItem implements Serializable {
        private String activityId;
        private String goodsId;
        private Integer totalStock;
        private Integer availableStock;
        private Integer lockedStock;
        private Integer paidStock;
        private LocalDateTime updateTime;
    }

    @Data
    public static class NotifyTaskItem implements Serializable {
        private String activityId;
        private String teamId;
        private String notifyCategory;
        private String notifyType;
        private String notifyMq;
        private String notifyUrl;
        private Integer notifyCount;
        private Integer notifyStatus;
        private String uuid;
        private LocalDateTime updateTime;
    }
}
