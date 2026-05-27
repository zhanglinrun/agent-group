package com.linrun.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDeltaDTO implements Serializable {

    private String content;
}
