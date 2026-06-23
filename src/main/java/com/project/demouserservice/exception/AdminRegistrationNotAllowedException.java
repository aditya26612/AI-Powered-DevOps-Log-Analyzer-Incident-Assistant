package com.project.demouserservice.exception;

public class AdminRegistrationNotAllowedException
        extends RuntimeException {

    public AdminRegistrationNotAllowedException(
            String message) {

        super(message);
    }
}