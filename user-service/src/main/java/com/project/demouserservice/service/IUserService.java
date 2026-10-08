package com.project.demouserservice.service;

import com.project.demouserservice.dto.AuthResponse;
import com.project.demouserservice.dto.LoginRequest;
import com.project.demouserservice.dto.RegisterRequest;
import com.project.demouserservice.dto.UserResponse;

public interface IUserService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}