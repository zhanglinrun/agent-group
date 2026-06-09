package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AcademicProjectPatchCreateRequest implements Serializable {

    private String fileId;
    private String title;
    private String reason;
    private String beforeText;
    private String afterText;
}















