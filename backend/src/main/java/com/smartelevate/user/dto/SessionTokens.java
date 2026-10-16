package com.smartelevate.user.dto;

/** Resultado interno de login/refresh: el body público y el refresh token, que va solo en la cookie. */
public record SessionTokens(TokenResponse response, String refreshToken) {

    /** Sin el refresh token: un log o un mensaje de error no debe filtrarlo. */
    @Override
    public String toString() {
        return "SessionTokens[response=" + response + ", refreshToken=***]";
    }
}
