package com.securevault.crypto;

/**
 * Criptografia autenticada de campos confidenciais.
 *
 * <p>O parâmetro {@code aad} (dados associados) não é cifrado, mas é autenticado: o texto cifrado só é
 * aceito na descriptografia se o mesmo {@code aad} for informado. Isso impede que um valor cifrado seja
 * "movido" para outro registro, outro campo ou outro usuário.
 */
public interface EncryptionService {

    byte[] encrypt(String plaintext, byte[] aad);

    String decrypt(byte[] payload, byte[] aad);
}
