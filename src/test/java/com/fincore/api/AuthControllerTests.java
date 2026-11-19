package com.fincore.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").exists())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("BORROWER"))
            .andExpect(jsonPath("$.message")
                    .value("Credentials verified successfully"));
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

    // Register account
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isCreated());

    // Mark account as disabled
    UserAccount account = userAccountRepository.findByEmail(email)
            .orElseThrow();

    account.setStatus("DISABLED");
    userAccountRepository.saveAndFlush(account);

    // Correct password must still be rejected
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
            .andExpect(status().isForbidden());
}
}