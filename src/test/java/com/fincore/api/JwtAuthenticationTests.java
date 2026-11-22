
package com.fincore.api;

import com.fincore.api.service.CustomerService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JwtEncoder jwtEncoder;

        @MockitoBean
        private CustomerService customerService;

        private String createToken(
                        Instant issuedAt,
                        Instant expiresAt) {

                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer("fincore-api")
                                .subject(UUID.randomUUID().toString())
                                .issuedAt(issuedAt)
                                .expiresAt(expiresAt)
                                .claim("role", "BORROWER")
                                .build();

                JwsHeader header = JwsHeader
                                .with(MacAlgorithm.HS256)
                                .build();

                return jwtEncoder.encode(
                                JwtEncoderParameters.from(header, claims)).getTokenValue();
        }

        @Test
        void validJwtAllowsAccessToProtectedEndpoint() throws Exception {

                Instant now = Instant.now();

                String token = createToken(
                                now,
                                now.plus(30, ChronoUnit.MINUTES));

                UUID customerId = UUID.randomUUID();

                mockMvc.perform(get("/api/customers/{id}", customerId)
                                .header(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(authenticated());

                verify(customerService).getCustomerById(customerId);
        }

        @Test
        void missingJwtRejectsProtectedEndpoint() throws Exception {

                mockMvc.perform(get(
                                "/api/customers/{id}",
                                UUID.randomUUID()))
                                .andExpect(status().isUnauthorized())
                                .andExpect(unauthenticated());

                verifyNoInteractions(customerService);
        }

        @Test
        void malformedJwtRejectsProtectedEndpoint() throws Exception {

                mockMvc.perform(get(
                                "/api/customers/{id}",
                                UUID.randomUUID())
                                .header(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer not-a-valid-jwt"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(unauthenticated());

                verifyNoInteractions(customerService);
        }

        @Test
        void expiredJwtRejectsProtectedEndpoint() throws Exception {

                Instant now = Instant.now();

                String expiredToken = createToken(
                                now.minus(60, ChronoUnit.MINUTES),
                                now.minus(30, ChronoUnit.MINUTES));

                mockMvc.perform(get(
                                "/api/customers/{id}",
                                UUID.randomUUID())
                                .header(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer " + expiredToken))
                                .andExpect(status().isUnauthorized())
                                .andExpect(unauthenticated());

                verifyNoInteractions(customerService);
        }

        @Test
        void tamperedJwtSignatureRejectsProtectedEndpoint()
                        throws Exception {

                Instant now = Instant.now();

                String validToken = createToken(
                                now,
                                now.plus(30, ChronoUnit.MINUTES));

                // Split the JWT into header, payload and signature.
                String[] parts = validToken.split("\\.");

                // A signed JWT must contain exactly three parts.
                org.junit.jupiter.api.Assertions.assertEquals(3, parts.length);

                String signature = parts[2];

                // Change the first character of the signature.
                // The replacement must differ from the original.
                char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';

                String tamperedSignature = replacement + signature.substring(1);

                String tamperedToken = parts[0] + "." +
                                parts[1] + "." +
                                tamperedSignature;

                // A modified signature must invalidate the JWT.
                mockMvc.perform(
                                get("/api/customers/{id}", UUID.randomUUID())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + tamperedToken))
                                .andExpect(status().isUnauthorized())
                                .andExpect(unauthenticated());

                // The request must be rejected before reaching the service.
                verifyNoInteractions(customerService);

        }

        private String createTokenWithIssuer(String issuer) {

                Instant now = Instant.now();

                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer(issuer)
                                .subject(UUID.randomUUID().toString())
                                .issuedAt(now)
                                .expiresAt(now.plus(30, ChronoUnit.MINUTES))
                                .claim("role", "BORROWER")
                                .build();

                JwsHeader header = JwsHeader
                                .with(MacAlgorithm.HS256)
                                .build();

                return jwtEncoder.encode(
                                JwtEncoderParameters.from(header, claims)).getTokenValue();
        }

        @Test
        void jwtWithIncorrectIssuerIsRejected() throws Exception {

                String token = createTokenWithIssuer("untrusted-issuer");

                mockMvc.perform(
                                get("/api/customers/{id}", UUID.randomUUID())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token))
                                .andExpect(status().isUnauthorized())
                                .andExpect(unauthenticated());

                verifyNoInteractions(customerService);
        }

}
