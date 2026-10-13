package com.securevault.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.request")
public record RequestLimitProperties(@Min(1024) long maxBodyBytes) {
}
