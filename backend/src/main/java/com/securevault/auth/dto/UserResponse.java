package com.securevault.auth.dto;

import com.securevault.user.User;
import java.util.UUID;

/** Dados públicos do usuário autenticado. Nunca inclui hash de senha. */
public record UserResponse(UUID id, String name, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
