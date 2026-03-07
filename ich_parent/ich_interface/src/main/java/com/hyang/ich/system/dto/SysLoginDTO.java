package com.hyang.ich.system.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class SysLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
}
