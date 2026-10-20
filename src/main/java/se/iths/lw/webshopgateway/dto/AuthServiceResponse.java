package se.iths.lw.webshopgateway.dto;

import java.util.List;

public record AuthServiceResponse(
        String accessToken,
        long expiresIn,
        String subject,
        List<String> roles
) {
}
