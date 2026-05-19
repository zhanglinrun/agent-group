package com.linrun.api.market.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class GoodsMarketResponse implements Serializable {

    private String activityId;
    private Goods goods;
    private List<Team> teamList = new ArrayList<>();
    private TeamStatistic teamStatistic = new TeamStatistic();

    @Data
    public static class Goods implements Serializable {

        private String goodsId;
        private BigDecimal originalPrice;
        private BigDecimal deductionPrice;
        private BigDecimal payPrice;
    }

    @Data
    public static class Team implements Serializable {

        private String userId;
        private String teamId;
        private String activityId;
        private Integer targetCount;
        private Integer completeCount;
        private Integer lockCount;
        private LocalDateTime validStartTime;
        private LocalDateTime validEndTime;
        private String validTimeCountdown;
        private String outTradeNo;
    }

    @Data
    public static class TeamStatistic implements Serializable {

        private Integer allTeamCount = 0;
        private Integer allTeamCompleteCount = 0;
        private Integer allTeamUserCount = 0;
    }
}
