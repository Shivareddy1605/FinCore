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
        private final LoginRateLimiter loginRateLimiter;

        public AuthService(
                        UserAccountRepository userAccountRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        LoginRateLimiter loginRateLimiter) {

                this.userAccountRepository = userAccountRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.loginRateLimiter = loginRateLimiter;
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

                return loginRateLimiter.withEmailLock(email, () -> {

                        if (loginRateLimiter.isBlocked(email)) {
                                throw new ResponseStatusException(
                                                HttpStatus.TOO_MANY_REQUESTS,
                                                "Too many failed login attempts. Try again later");
                        }

                        UserAccount user = userAccountRepository.findByEmail(email)
                                        .orElse(null);

                        if (user == null ||
                                        !passwordEncoder.matches(
                                                        request.password(),
                                                        user.getPasswordHash())) {

                                loginRateLimiter.recordFailure(email);

                                throw new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Invalid email or password");
                        }

                        if (!"ACTIVE".equals(user.getStatus())) {
                                throw new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "Account is not active");
                        }

                        loginRateLimiter.clearFailures(email);

                        String accessToken = jwtService.generateToken(user);

                        return new LoginResponse(
                                        user.getId(),
                                        user.getEmail(),
                                        user.getRole(),
                                        "Login successful",
                                        accessToken);
                });
        }

}