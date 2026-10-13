package com.securevault.crypto;

import com.securevault.config.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM com nonce aleatório de 96 bits gerado por operação.
 *
 * <p>Formato armazenado: {@code [versão: 1 byte][nonce: 12 bytes][texto cifrado + tag de 16 bytes]}.
 * O byte de versão permite introduzir uma nova chave/algoritmo no futuro sem ambiguidade.
 */
@Service
public class AesGcmEncryptionService implements EncryptionService {

    private static final byte FORMAT_VERSION = 1;
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionService(CryptoProperties properties) {
        this.key = parseKey(properties.key());
    }

    @Override
    public byte[] encrypt(String plaintext, byte[] aad) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext não pode ser nulo");
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[1 + NONCE_BYTES + ciphertext.length];
            payload[0] = FORMAT_VERSION;
            System.arraycopy(nonce, 0, payload, 1, NONCE_BYTES);
            System.arraycopy(ciphertext, 0, payload, 1 + NONCE_BYTES, ciphertext.length);
            return payload;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Falha ao criptografar o dado", e);
        }
    }

    @Override
    public String decrypt(byte[] payload, byte[] aad) {
        if (payload == null || payload.length < 1 + NONCE_BYTES + TAG_BYTES || payload[0] != FORMAT_VERSION) {
            throw new CryptoException("Formato de dado criptografado inválido");
        }
        byte[] nonce = Arrays.copyOfRange(payload, 1, 1 + NONCE_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(payload, 1 + NONCE_BYTES, payload.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Inclui AEADBadTagException (dado adulterado, AAD diferente ou chave incorreta).
            // A causa é preservada para o log do servidor; o texto da mensagem é genérico.
            throw new CryptoException("Falha ao descriptografar o dado", e);
        }
    }

    private static SecretKey parseKey(String base64Key) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "VAULT_ENCRYPTION_KEY inválida: deve ser Base64 válido (gere com: openssl rand -base64 32)");
        }
        if (keyBytes.length != KEY_BYTES) {
            Arrays.fill(keyBytes, (byte) 0);
            throw new IllegalStateException(
                    "VAULT_ENCRYPTION_KEY inválida: deve conter exatamente 32 bytes (AES-256) em Base64");
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        Arrays.fill(keyBytes, (byte) 0);
        return secretKey;
    }
}
