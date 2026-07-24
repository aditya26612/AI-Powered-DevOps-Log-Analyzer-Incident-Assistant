package com.project.demouserservice.controller;

import com.project.demouserservice.dto.AuthResponse;
import com.project.demouserservice.dto.LoginRequest;
import com.project.demouserservice.dto.RegisterRequest;
import com.project.demouserservice.dto.UserResponse;
import com.project.demouserservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request) {

        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request) {

        return userService.login(request);
    }
}