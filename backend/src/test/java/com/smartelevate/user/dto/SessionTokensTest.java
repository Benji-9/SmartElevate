package com.smartelevate.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SessionTokensTest {

    @Test
    void toStringHidesTheRefreshToken() {
        SessionTokens tokens = new SessionTokens(new TokenResponse("acc", "Bearer", 900), "secreto-refresh");

        assertThat(tokens.toString()).contains("refreshToken=***").doesNotContain("secreto-refresh");
    }
}
