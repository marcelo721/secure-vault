package com.securevault.security;

import java.util.UUID;

/**
 * Identidade extraída do token JWT validado pelo backend.
 * É a única fonte confiável de "quem é o usuário": nenhum ID vindo do cliente é usado para isso.
 */
public record AuthenticatedUser(UUID id) {
}
