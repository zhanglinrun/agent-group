package com.linrun.api.agent.response;

import lombok.Data;

import java.io.Serializable;

@Data
public class GuideImageUploadResponse implements Serializable {

    private String imageName;
    private String imageUrl;
    private String contentType;
    private Long imageSize;
    private String imageSummary;
}
