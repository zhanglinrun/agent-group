package com.linrun.api.evaluate.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class GuideEvaluationItemDTO implements Serializable {

    private String caseId;
    private String caseName;
    private String question;
    private String expectedGoodsId;
    private String actualGoodsId;
    private Boolean referencePassed;
    private Boolean answerPassed;
    private Boolean recommendationPassed;
    private Boolean contextPassed;
    private Long latencyMillis;
    private Integer score;
    private String suggestion;
}
