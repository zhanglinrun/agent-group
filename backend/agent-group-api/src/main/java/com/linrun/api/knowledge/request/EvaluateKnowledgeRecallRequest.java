package com.linrun.api.knowledge.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class EvaluateKnowledgeRecallRequest implements Serializable {

    private String question;
    private List<String> expectedFragmentIds;
    private Integer topK;
}
