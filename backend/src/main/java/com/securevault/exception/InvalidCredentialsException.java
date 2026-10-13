package com.securevault.exception;

/** Mensagem única para e-mail inexistente e senha incorreta, evitando enumeração de contas. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos.");
    }
}
