package com.project.demouserservice.dto;

import com.project.demouserservice.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private Role role;
}