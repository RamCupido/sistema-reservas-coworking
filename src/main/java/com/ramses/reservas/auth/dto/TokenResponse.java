package com.ramses.reservas.auth.dto;

/** expiresIn en segundos, siguiendo la convención de OAuth2. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
