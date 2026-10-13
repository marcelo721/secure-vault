package com.securevault.crypto;

/** Falha de criptografia/descriptografia. A mensagem nunca contém dados sensíveis. */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
