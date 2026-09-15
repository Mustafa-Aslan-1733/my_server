package com.pse.auth;


import com.pse.auth.service.TokenGenerator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTests {

    @Test
    void generateLoginCodeShouldHaveSixDigits() {
        String code = TokenGenerator.generateOneTimePassword();

        assertThat(code.length()).isEqualTo(6);
        assertThat(code.matches("[23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ]{6}"))
                .isTrue();    
        }
}