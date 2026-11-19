package com.fincore.api.service;

import com.fincore.api.dto.LoginRequest;
import com.fincore.api.dto.LoginResponse;
import com.fincore.api.dto.RegisterRequest;
import com.fincore.api.entity.UserAccount;
import com.fincore.api.repository.UserAccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UUID register(RegisterRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userAccountRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        UserAccount user = new UserAccount(UUID.randomUUID());

        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.password()));
        user.setRole("BORROWER");
        user.setStatus("ACTIVE");

        return userAccountRepository.saveAndFlush(user).getId();
    }

    public LoginResponse login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        UserAccount user = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid email or password"));

        if (!passwordEncoder.matches(
                request.password(), user.getPasswordHash())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is not active");
        }

        String accessToken = jwtService.generateToken(user);

        return new LoginResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                "Login successful",
                accessToken
        );
    }
}