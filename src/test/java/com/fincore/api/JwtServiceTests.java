
package com.fincore.api;

import com.fincore.api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class JwtServiceTests {

    @Test
    void zeroExpirationMinutesShouldBeRejected() {

        JwtEncoder encoder = mock(JwtEncoder.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService(encoder, "fincore-api", 0)
        );
    }

    @Test
    void expirationAboveMaximumShouldBeRejected() {

        JwtEncoder encoder = mock(JwtEncoder.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService(encoder, "fincore-api", 61)
        );
    }
}
