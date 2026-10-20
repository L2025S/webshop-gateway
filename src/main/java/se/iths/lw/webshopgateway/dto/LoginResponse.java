package se.iths.lw.webshopgateway.dto;

import java.util.List;

public record LoginResponse(
        long expiresIn,
        String subject,
        List<String> roles
) {
}
