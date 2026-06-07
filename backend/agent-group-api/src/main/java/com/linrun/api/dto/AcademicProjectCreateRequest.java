package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AcademicProjectCreateRequest implements Serializable {

    private String title;
    private String researchQuestion;
    private String targetVenue;
    private String writingStatus;
    private String progressNote;
}
