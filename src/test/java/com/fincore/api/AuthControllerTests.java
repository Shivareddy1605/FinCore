
package com.fincore.api;

import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.UserAccountRepository;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private JwtDecoder jwtDecoder;

        @Autowired
        private UserAccountRepository userAccountRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private org.springframework.scheduling.config.ScheduledTaskHolder scheduledTaskHolder;

        @Test
        void registrationWorksWithoutAuthentication() throws Exception {

                String email = "auth-test-" + UUID.randomUUID() + "@example.com";

                String requestBody = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.message")
                                                .value("Account registered successfully"));
        }

        @Test
        void registrationRejectsMissingRequiredFields() throws Exception {

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void registrationRejectsDuplicateEmail() throws Exception {

                String email = "duplicate-auth-" + UUID.randomUUID() + "@example.com";

                String requestBody = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isConflict());
        }

        @Test
        void registrationStoresPasswordAsBCryptHash() throws Exception {

                String email = "bcrypt-test-" + UUID.randomUUID() + "@example.com";
                String rawPassword = "FinCore@Test2026!";

                String requestBody = """
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, rawPassword);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated());

                UserAccount account = userAccountRepository.findByEmail(email)
                                .orElseThrow();

                String storedHash = account.getPasswordHash();

                assertNotEquals(rawPassword, storedHash);
                assertTrue(storedHash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$"));
                assertTrue(passwordEncoder.matches(rawPassword, storedHash));
                assertFalse(passwordEncoder.matches("WrongPassword123!", storedHash));
        }

        @Test
        void loginSucceedsWithCorrectCredentials() throws Exception {

                String email = "login-test-" + UUID.randomUUID() + "@example.com";
                String password = "FinCore@Test2026!";

                String requestBody = """
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, password);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated());

                UserAccount account = userAccountRepository.findByEmail(email)
                                .orElseThrow();

                String responseBody = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.userId").exists())
                                .andExpect(jsonPath("$.email").value(email))
                                .andExpect(jsonPath("$.role").value("BORROWER"))
                                .andExpect(jsonPath("$.message")
                                                .value("Login successful"))
                                .andExpect(jsonPath("$.accessToken").isString())
                                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                // Extract access token from the JSON response.
                String token = JsonPath.read(responseBody, "$.accessToken");

                // Decode and verify JWT signature and timestamps.
                Jwt jwt = jwtDecoder.decode(token);

                // Verify issuer.
                assertEquals("fincore-api", jwt.getClaimAsString("iss"));

                // Verify subject matches the registered user's UUID.
                assertEquals(account.getId().toString(), jwt.getSubject());

                // Verify role.
                assertEquals("BORROWER", jwt.getClaimAsString("role"));

                // Verify issued-at and expiration timestamps.
                Instant issuedAt = jwt.getIssuedAt();
                Instant expiresAt = jwt.getExpiresAt();

                assertNotNull(issuedAt);
                assertNotNull(expiresAt);

                // Token must expire exactly 30 minutes after issuance.
                assertEquals(
                                Duration.ofMinutes(30),
                                Duration.between(issuedAt, expiresAt));

                // Token must not already be expired.
                assertTrue(expiresAt.isAfter(Instant.now()));
        }

        @Test
        void loginRejectsIncorrectPassword() throws Exception {

                String email = "login-test-" + UUID.randomUUID() + "@example.com";
                String correctPassword = "FinCore@Test2026!";

                String registrationBody = """
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, correctPassword);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody))
                                .andExpect(status().isCreated());

                String loginBody = """
                                {
                                    "email": "%s",
                                    "password": "WrongPassword123!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void loginRejectsUnknownEmail() throws Exception {

                String email = "unknown-" + UUID.randomUUID() + "@example.com";

                String loginBody = """
                                {
                                    "email": "%s",
                                    "password": "SomePassword123!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void loginRejectsMissingCredentials() throws Exception {

                String loginBody = """
                                {}
                                """;

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void loginRejectsDisabledAccount() throws Exception {

                String email = "disabled-" + UUID.randomUUID() + "@example.com";
                String password = "FinCore@Test2026!";

                String requestBody = """
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(email, password);

                // Register account.
                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated());

                // Mark account as disabled.
                UserAccount account = userAccountRepository.findByEmail(email)
                                .orElseThrow();

                account.setStatus("DISABLED");
                userAccountRepository.saveAndFlush(account);

                // Correct password must still be rejected.
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isForbidden());
        }

        @Test
        void loginReturns429AfterFiveIncorrectPasswords() throws Exception {

                String email = "rate-limit-" + UUID.randomUUID() + "@example.com";

                String registrationBody = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody))
                                .andExpect(status().isCreated());

                String wrongLoginBody = """
                                {
                                    "email": "%s",
                                    "password": "WrongPassword123!"
                                }
                                """.formatted(email);

                // First five failures must return 401.
                for (int i = 0; i < 5; i++) {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(wrongLoginBody))
                                        .andExpect(status().isUnauthorized());
                }

                // The sixth attempt must be rate-limited.
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongLoginBody))
                                .andExpect(status().isTooManyRequests());
        }

        @Test
        void successfulLoginsDoNotConsumeRateLimit() throws Exception {

                String email = "successful-login-" + UUID.randomUUID() + "@example.com";

                String requestBody = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isCreated());

                for (int i = 0; i < 6; i++) {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestBody))
                                        .andExpect(status().isOk());
                }
        }

        @Test
        void successfulLoginClearsPreviousFailures() throws Exception {

                String email = "clear-failures-" + UUID.randomUUID() + "@example.com";

                String registrationBody = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody))
                                .andExpect(status().isCreated());

                String wrongLoginBody = """
                                {
                                    "email": "%s",
                                    "password": "WrongPassword123!"
                                }
                                """.formatted(email);

                for (int i = 0; i < 4; i++) {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(wrongLoginBody))
                                        .andExpect(status().isUnauthorized());
                }

                // Successful login resets failure count.
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody))
                                .andExpect(status().isOk());

                // Another five failures should be allowed before blocking.
                for (int i = 0; i < 5; i++) {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(wrongLoginBody))
                                        .andExpect(status().isUnauthorized());
                }

                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongLoginBody))
                                .andExpect(status().isTooManyRequests());
        }

        @Test
        void blockedAccountRejectsEvenCorrectPassword() throws Exception {

                String email = "blocked-login-" + UUID.randomUUID() + "@example.com";

                String registrationBody = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody))
                                .andExpect(status().isCreated());

                String wrongLoginBody = """
                                {
                                    "email": "%s",
                                    "password": "WrongPassword123!"
                                }
                                """.formatted(email);

                for (int i = 0; i < 5; i++) {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(wrongLoginBody))
                                        .andExpect(status().isUnauthorized());
                }

                // Correct password must not bypass an active rate limit.
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody))
                                .andExpect(status().isTooManyRequests());
        }

        @Test
        void loginRateLimiterCleanupIsRegisteredWithSpringScheduler() {

                boolean registered = scheduledTaskHolder.getScheduledTasks()
                                .stream()
                                .anyMatch(task -> task.toString()
                                                .contains("removeExpiredEntries"));

                assertTrue(
                                registered,
                                "Login rate-limiter cleanup must be registered with Spring");
        }

        @Test
        void repeatedIncorrectPasswordsTemporarilyBlockLegitimateLogin()
                        throws Exception {

                String email = "lockout-" + UUID.randomUUID() + "@example.com";

                String correctLogin = """
                                {
                                    "email": "%s",
                                    "password": "FinCore@Test2026!"
                                }
                                """.formatted(email);

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(correctLogin))
                                .andExpect(status().isCreated());

                String incorrectLogin = """
                                {
                                    "email": "%s",
                                    "password": "IncorrectPassword123!"
                                }
                                """.formatted(email);

                for (int i = 0; i < 5; i++) {
                        mockMvc.perform(post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(incorrectLogin))
                                        .andExpect(status().isUnauthorized());
                }

                // Even the legitimate borrower is temporarily blocked.
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(correctLogin))
                                .andExpect(status().isTooManyRequests());
        }

}
