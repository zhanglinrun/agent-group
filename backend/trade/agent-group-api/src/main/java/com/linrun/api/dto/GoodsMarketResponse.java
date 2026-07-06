package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GoodsMarketResponse implements Serializable {

    private String activityId;
    private Boolean visible;
    private Boolean enable;
    private String message;
    private Discount discount;
    private Goods goods;
    private List<Team> teamList = new ArrayList<>();
    private TeamStatistic teamStatistic = new TeamStatistic();

    @Data
    public static class Goods implements Serializable {

        private String goodsId;
        private String goodsName;
        private BigDecimal originalPrice;
        private BigDecimal deductionPrice;
        private BigDecimal payPrice;
        private Integer totalStock;
        private Integer availableStock;
        private Integer lockedStock;
        private Integer paidStock;
    }

    @Data
    public static class Discount implements Serializable {

        private String discountId;
        private String discountName;
        private String marketPlan;
        private String marketExpr;
        private String tagId;
        private String tagScope;
    }

    @Data
    public static class Team implements Serializable {

        private String userId;
        private String teamId;
        private String activityId;
        private Integer targetCount;
        private Integer completeCount;
        private Integer lockCount;
        private GroupProgress progress;
        private LocalDateTime validStartTime;
        private LocalDateTime validEndTime;
        private String validTimeCountdown;
        private String outTradeNo;
    }

    @Data
    public static class GroupProgress implements Serializable {

        private Integer targetCount = 0;
        private Integer lockedCount = 0;
        private Integer completeCount = 0;
        private Integer remainingCount = 0;
        private BigDecimal progressRate = BigDecimal.ZERO;
        private Boolean success = false;
    }

    @Data
    public static class TeamStatistic implements Serializable {

        private Integer allTeamCount = 0;
        private Integer allTeamCompleteCount = 0;
        private Integer allTeamUserCount = 0;
    }
}















