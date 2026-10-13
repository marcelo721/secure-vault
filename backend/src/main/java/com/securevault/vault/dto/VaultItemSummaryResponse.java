package com.securevault.vault.dto;

import com.securevault.vault.entity.VaultItem;
import com.securevault.vault.entity.VaultItemType;
import java.time.Instant;
import java.util.UUID;

/** Visão de listagem/criação/atualização: não contém nenhum campo confidencial. */
public record VaultItemSummaryResponse(
        UUID id,
        String title,
        VaultItemType type,
        String url,
        String description,
        Instant createdAt,
        Instant updatedAt) {

    public static VaultItemSummaryResponse from(VaultItem item) {
        return new VaultItemSummaryResponse(item.getId(), item.getTitle(), item.getType(), item.getUrl(),
                item.getDescription(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
