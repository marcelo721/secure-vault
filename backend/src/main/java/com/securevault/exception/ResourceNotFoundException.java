package com.securevault.exception;

/** Recurso inexistente OU pertencente a outro usuário (as duas situações são indistinguíveis para o cliente). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException() {
        super("Recurso não encontrado.");
    }
}
