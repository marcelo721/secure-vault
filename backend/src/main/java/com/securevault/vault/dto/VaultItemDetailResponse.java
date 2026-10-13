package com.securevault.vault.dto;

import com.securevault.vault.entity.VaultItemType;
import java.time.Instant;
import java.util.UUID;

/** Visão de detalhe: única resposta que contém conteúdo descriptografado. */
public record VaultItemDetailResponse(
        UUID id,
        String title,
        VaultItemType type,
        String username,
        String secret,
        String url,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    @Override
    public String toString() {
        return "VaultItemDetailResponse[id=" + id + ", title=" + title + ", secret=***]";
    }
}
