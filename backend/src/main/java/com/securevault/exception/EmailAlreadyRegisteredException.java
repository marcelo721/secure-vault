package com.securevault.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("Este e-mail já está cadastrado.");
    }
}
