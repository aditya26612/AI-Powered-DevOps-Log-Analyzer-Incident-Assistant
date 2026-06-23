package com.project.demouserservice.service;

import com.project.demouserservice.dao.IUserRepo;
import com.project.demouserservice.dto.AuthResponse;
import com.project.demouserservice.dto.LoginRequest;
import com.project.demouserservice.dto.RegisterRequest;
import com.project.demouserservice.dto.UserResponse;
import com.project.demouserservice.entity.Role;
import com.project.demouserservice.entity.UserEntity;
import com.project.demouserservice.exception.AdminRegistrationNotAllowedException;
import com.project.demouserservice.exception.InvalidCredentialsException;
import com.project.demouserservice.exception.UserAlreadyExistsException;
import com.project.demouserservice.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements IUserService {


    private final IUserRepo userRepo;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    @Override
    public UserResponse register(RegisterRequest request) {

        if(userRepo.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(
                    "Email already registered");
        }

        UserEntity user = new UserEntity();

        user.setEmail(request.getEmail());

        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        if(request.getRole() == Role.ADMIN){
            throw new AdminRegistrationNotAllowedException(
                    "Cannot register as ADMIN"
            );
        }

        user.setRole(request.getRole());

        UserEntity savedUser = userRepo.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    @Override
    public AuthResponse login(LoginRequest request) {

        UserEntity user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));

        boolean passwordMatches =
                passwordEncoder.matches(
                        request.getPassword(),
                        user.getPassword()
                );

        if(!passwordMatches) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String token =
                jwtService.generateToken(user);

        return new AuthResponse(
                token,
                user.getEmail(),
                user.getRole().name()
        );
    }
}