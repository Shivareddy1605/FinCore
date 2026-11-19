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
}