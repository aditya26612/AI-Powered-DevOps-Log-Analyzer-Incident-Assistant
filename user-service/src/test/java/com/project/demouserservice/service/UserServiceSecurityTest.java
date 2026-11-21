package com.project.demouserservice.service;

import com.project.demouserservice.dao.IUserRepo;
import com.project.demouserservice.dto.AuthResponse;
import com.project.demouserservice.dto.LoginRequest;
import com.project.demouserservice.dto.RegisterRequest;
import com.project.demouserservice.dto.UserResponse;
import com.project.demouserservice.entity.Role;
import com.project.demouserservice.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTest {

    @Mock
    private IUserRepo userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private UserService userService;

    @Test
    void register_withoutRole_defaultsToServerControlledDeveloperRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@company.com");
        request.setPassword("Password123!");

        when(userRepo.existsByEmail("newuser@company.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded_pass");
        when(userRepo.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(101L);
            return user;
        });

        UserResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertEquals("newuser@company.com", response.getEmail());
        assertEquals(Role.DEVELOPER, response.getRole(),
                "Public registration without role must automatically assign safe default Role.DEVELOPER");
    }

    @Test
    void login_returnsTokenAndNonAdminRole() {
        LoginRequest request = new LoginRequest();
        request.setEmail("developer@company.com");
        request.setPassword("Password123!");

        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setEmail("developer@company.com");
        user.setPassword("encoded_secret");
        user.setRole(Role.DEVELOPER);

        when(userRepo.findByEmail("developer@company.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "encoded_secret")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("mock.jwt.token");

        AuthResponse auth = userService.login(request);

        assertNotNull(auth);
        assertEquals("developer@company.com", auth.getEmail());
        assertEquals("DEVELOPER", auth.getRole());
        assertEquals("mock.jwt.token", auth.getToken());
        assertNotEquals("ADMIN", auth.getRole(), "Standard user must not possess ADMIN role");
    }
}
