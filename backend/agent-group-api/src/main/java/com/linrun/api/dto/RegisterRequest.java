package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RegisterRequest implements Serializable {

    private String username;
    private String password;
    private String nickname;
    private String email;
}















