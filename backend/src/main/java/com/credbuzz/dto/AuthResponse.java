package com.credbuzz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private boolean success;
    private String message;
    private String token;
    private UserDto user;

    public static AuthResponse success(String token, UserDto user) {
        return AuthResponse.builder()
                .success(true)
                .token(token)
                .user(user)
                .build();
    }

    public static AuthResponse error(String message) {
        return AuthResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
