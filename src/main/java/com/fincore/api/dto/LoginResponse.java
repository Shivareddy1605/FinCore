package com.fincore.api.dto;

import java.util.UUID;

public record LoginResponse(

        UUID userId,
        String email,
        String role,
        String message,
        String accessToken

) {
}