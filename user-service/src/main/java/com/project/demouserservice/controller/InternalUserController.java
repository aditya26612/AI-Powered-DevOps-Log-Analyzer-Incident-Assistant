package com.project.demouserservice.controller;

import com.project.demouserservice.dao.IUserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    private final IUserRepo users;
    private final byte[] serviceKey;

    public InternalUserController(IUserRepo users, @Value("${INTERNAL_API_KEY:}") String key) {
        this.users = users;
        this.serviceKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{id}/exists")
    public boolean exists(@PathVariable long id,
                          @RequestHeader(value = "X-Internal-Api-Key", required = false) String presented) {
        if (serviceKey.length < 16 || presented == null || !MessageDigest.isEqual(
                serviceKey, presented.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return id > 0 && users.existsById(id);
    }
}
