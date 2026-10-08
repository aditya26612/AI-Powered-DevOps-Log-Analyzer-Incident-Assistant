package com.project.demouserservice.advice;

import com.project.demouserservice.exception.AdminRegistrationNotAllowedException;
import com.project.demouserservice.exception.InvalidCredentialsException;
import com.project.demouserservice.exception.UserAlreadyExistsException;
import com.project.demouserservice.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>>
    handleUserAlreadyExists(
            UserAlreadyExistsException ex) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("timestamp",
                LocalDateTime.now());

        response.put("status",
                HttpStatus.CONFLICT.value());

        response.put("message",
                ex.getMessage());

        return new ResponseEntity<>(
                response,
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>>
    handleUserNotFound(
            UserNotFoundException ex) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("timestamp",
                LocalDateTime.now());

        response.put("status",
                HttpStatus.NOT_FOUND.value());

        response.put("message",
                ex.getMessage());

        return new ResponseEntity<>(
                response,
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(
            InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>>
    handleInvalidCredentials(
            InvalidCredentialsException ex) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("timestamp",
                LocalDateTime.now());

        response.put("status",
                HttpStatus.UNAUTHORIZED.value());

        response.put("message",
                ex.getMessage());

        return new ResponseEntity<>(
                response,
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(
            AdminRegistrationNotAllowedException.class)
    public ResponseEntity<Map<String, Object>>
    handleAdminRegistrationNotAllowed(
            AdminRegistrationNotAllowedException ex) {

        Map<String, Object> response =
                new HashMap<>();

        response.put("timestamp",
                LocalDateTime.now());

        response.put("status",
                HttpStatus.FORBIDDEN.value());

        response.put("message",
                ex.getMessage());

        return new ResponseEntity<>(
                response,
                HttpStatus.FORBIDDEN
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>>
    handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors =
                new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return new ResponseEntity<>(
                errors,
                HttpStatus.BAD_REQUEST
        );
    }
}