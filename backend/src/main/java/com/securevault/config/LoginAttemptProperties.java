package com.securevault.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.login-attempts")
public record LoginAttemptProperties(
        @Min(1) int maxFailures,
        @Min(1) long windowMinutes,
        @Min(1) long lockMinutes) {
}
