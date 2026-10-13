package com.securevault.vault.dto;

import com.securevault.vault.entity.VaultItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contrato de entrada para criar/atualizar um registro.
 * Não há campo de proprietário nem de ID: o dono vem sempre do usuário autenticado
 * e campos desconhecidos são rejeitados (fail-on-unknown-properties).
 */
public record VaultItemRequest(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres.")
        String title,

        @NotNull(message = "O tipo é obrigatório.")
        VaultItemType type,

        @Size(max = 255, message = "O usuário/identificador deve ter no máximo 255 caracteres.")
        String username,

        @NotBlank(message = "O segredo é obrigatório.")
        @Size(max = 10_000, message = "O segredo deve ter no máximo 10.000 caracteres.")
        String secret,

        @Size(max = 2048, message = "A URL deve ter no máximo 2048 caracteres.")
        @Pattern(regexp = "^(|https?://\\S+)$", message = "A URL deve começar com http:// ou https://.")
        String url,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres.")
        String description) {

    @Override
    public String toString() {
        return "VaultItemRequest[title=" + title + ", type=" + type + ", secret=***]";
    }
}
