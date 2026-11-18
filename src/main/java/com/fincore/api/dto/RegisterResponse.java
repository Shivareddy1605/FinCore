
package com.fincore.api.dto;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String message
) {
}
