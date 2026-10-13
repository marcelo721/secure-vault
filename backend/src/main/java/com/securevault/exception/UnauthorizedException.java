package com.securevault.exception;

/** Sessão sem correspondência válida (ex.: token válido de um usuário que não existe mais). */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Autenticação necessária.");
    }
}
