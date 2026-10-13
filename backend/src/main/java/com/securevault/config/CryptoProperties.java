package com.securevault.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Chave AES-256 em Base64, fornecida exclusivamente por variável de ambiente. */
@Validated
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(@NotBlank String key) {

    @Override
    public String toString() {
        return "CryptoProperties[key=***]";
    }
}
