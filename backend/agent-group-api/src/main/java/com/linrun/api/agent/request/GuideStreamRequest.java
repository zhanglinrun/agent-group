package com.linrun.api.agent.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:14
 */
@Data
public class GuideStreamRequest implements Serializable {

    private String sessionId;
    private String userId;
    private String question;
    private String imageUrl;
}
