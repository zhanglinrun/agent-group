package com.linrun.api.quality.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class GuideEvaluationFeedbackDTO implements Serializable {

    private String targetType;
    private String priority;
    private String content;
}
