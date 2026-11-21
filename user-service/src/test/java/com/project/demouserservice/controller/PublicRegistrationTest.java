package com.project.demouserservice.controller;

import com.project.demouserservice.dao.IUserRepo;
import com.project.demouserservice.entity.Role;
import com.project.demouserservice.entity.UserEntity;
import com.project.demouserservice.security.SecurityConfig;
import com.project.demouserservice.service.JwtService;
import com.project.demouserservice.service.UserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, UserService.class})
class PublicRegistrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IUserRepo userRepo;

    @MockitoBean
    private JwtService jwtService;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ADMIN", "DEVOPS", "DEVELOPER", "FUTURE_PRIVILEGED_ROLE"})
    void publicRegistrationAlwaysPersistsDeveloper(String legacyRole) throws Exception {
        when(userRepo.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        String roleField = legacyRole == null ? "" : ", \"role\": \"" + legacyRole + "\"";
        String body = """
                {"email":"registration@example.test","password":"test-fixture-password"%s}
                """.formatted(roleField);

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DEVELOPER"));
        verify(userRepo).save(argThat(user -> user.getRole() == Role.DEVELOPER));
    }
}
