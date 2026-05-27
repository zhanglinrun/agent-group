package com.linrun.api.agent.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RetrievalProgressDTO implements Serializable {

    private String stage;
    private String strategy;
    private String message;
    private Double confidence;
    private List<String> retrievers;
    private Integer referenceCount;

    public static RetrievalProgressDTO of(String stage,
                                          String strategy,
                                          String message,
                                          Double confidence,
                                          List<String> retrievers,
                                          Integer referenceCount) {
        RetrievalProgressDTO dto = new RetrievalProgressDTO();
        dto.setStage(stage);
        dto.setStrategy(strategy);
        dto.setMessage(message);
        dto.setConfidence(confidence);
        dto.setRetrievers(retrievers);
        dto.setReferenceCount(referenceCount);
        return dto;
    }
}
