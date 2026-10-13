package com.securevault.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET deve ter pelo menos 32 caracteres") String secret,
        @Min(1) long expirationMinutes,
        @NotBlank String issuer) {

    @Override
    public String toString() {
        return "JwtProperties[secret=***, expirationMinutes=" + expirationMinutes + ", issuer=" + issuer + "]";
    }
}
