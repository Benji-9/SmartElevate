package com.smartelevate.user.dto;

/** Resultado interno de login/refresh: el body público y el refresh token, que va solo en la cookie. */
public record SessionTokens(TokenResponse response, String refreshToken) {
}
