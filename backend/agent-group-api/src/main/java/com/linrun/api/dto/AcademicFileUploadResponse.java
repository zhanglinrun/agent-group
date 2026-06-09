package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AcademicFileUploadResponse implements Serializable {

    private String fileId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String summary;
    private String status;
}















