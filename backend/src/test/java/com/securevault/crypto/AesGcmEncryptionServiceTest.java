package com.securevault.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securevault.config.CryptoProperties;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AES-256-GCM (unitário)")
class AesGcmEncryptionServiceTest {

    private static final String KEY = base64Key("0123456789abcdef0123456789abcdef");
    private static final String OTHER_KEY = base64Key("fedcba9876543210fedcba9876543210");
    private static final byte[] AAD = "owner:item:secret".getBytes(StandardCharsets.UTF_8);

    private final AesGcmEncryptionService service = new AesGcmEncryptionService(new CryptoProperties(KEY));

    @Test
    @DisplayName("criptografa e descriptografa preservando o texto (inclusive acentos e emojis)")
    void roundTrip() {
        String plaintext = "Sénha çomplexa 🔐 — com acentos";
        byte[] encrypted = service.encrypt(plaintext, AAD);
        assertThat(service.decrypt(encrypted, AAD)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("aceita texto vazio")
    void emptyString() {
        assertThat(service.decrypt(service.encrypt("", AAD), AAD)).isEmpty();
    }

    @Test
    @DisplayName("o texto cifrado não contém o texto puro e segue o formato versão|nonce|dados|tag")
    void formatAndConfidentiality() {
        String plaintext = "segredo-muito-reconhecivel";
        byte[] encrypted = service.encrypt(plaintext, AAD);

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain(plaintext);
        assertThat(encrypted[0]).isEqualTo((byte) 1);
        assertThat(encrypted).hasSize(1 + 12 + plaintext.length() + 16);
    }

    @Test
    @DisplayName("nonces nunca se repetem em 10.000 operações com a mesma chave")
    void noncesAreUnique() {
        Set<String> nonces = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            byte[] encrypted = service.encrypt("mesmo texto", AAD);
            nonces.add(Base64.getEncoder().encodeToString(Arrays.copyOfRange(encrypted, 1, 13)));
        }
        assertThat(nonces).hasSize(10_000);
    }

    @Test
    @DisplayName("o mesmo texto cifrado duas vezes gera saídas diferentes")
    void sameInputDifferentOutput() {
        assertThat(service.encrypt("x", AAD)).isNotEqualTo(service.encrypt("x", AAD));
    }

    @Test
    @DisplayName("qualquer byte alterado (versão, nonce, dados ou tag) é detectado")
    void tamperingIsDetectedInEveryPosition() {
        byte[] encrypted = service.encrypt("conteudo", AAD);
        for (int i = 0; i < encrypted.length; i++) {
            byte[] tampered = encrypted.clone();
            tampered[i] ^= 0x01;
            assertThatThrownBy(() -> service.decrypt(tampered, AAD))
                    .as("alteração na posição %d", i)
                    .isInstanceOf(CryptoException.class);
        }
    }

    @Test
    @DisplayName("AAD diferente (outro usuário/registro/campo) impede a descriptografia")
    void wrongAadIsRejected() {
        byte[] encrypted = service.encrypt("conteudo", AAD);
        byte[] otherAad = "owner:OUTRO-ITEM:secret".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.decrypt(encrypted, otherAad)).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("chave diferente impede a descriptografia")
    void wrongKeyIsRejected() {
        byte[] encrypted = service.encrypt("conteudo", AAD);
        AesGcmEncryptionService other = new AesGcmEncryptionService(new CryptoProperties(OTHER_KEY));
        assertThatThrownBy(() -> other.decrypt(encrypted, AAD)).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("payload nulo, curto ou de versão desconhecida é rejeitado")
    void malformedPayloadIsRejected() {
        assertThatThrownBy(() -> service.decrypt(null, AAD)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> service.decrypt(new byte[0], AAD)).isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> service.decrypt(new byte[20], AAD)).isInstanceOf(CryptoException.class);
        byte[] unknownVersion = service.encrypt("x", AAD);
        unknownVersion[0] = 99;
        assertThatThrownBy(() -> service.decrypt(unknownVersion, AAD)).isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("mensagens de erro nunca contêm o texto puro")
    void errorMessagesDoNotLeakPlaintext() {
        String plaintext = "texto-que-nao-pode-vazar";
        byte[] encrypted = service.encrypt(plaintext, AAD);
        encrypted[encrypted.length - 1] ^= 0x01;

        assertThatThrownBy(() -> service.decrypt(encrypted, AAD))
                .isInstanceOf(CryptoException.class)
                .hasMessageNotContaining(plaintext);
    }

    @Test
    @DisplayName("texto nulo é recusado na criptografia")
    void nullPlaintextRejected() {
        assertThatThrownBy(() -> service.encrypt(null, AAD)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chave que não é Base64 faz a aplicação falhar ao iniciar, sem ecoar o valor")
    void invalidBase64KeyFailsFast() {
        assertThatThrownBy(() -> new AesGcmEncryptionService(new CryptoProperties("TROQUE-ESTE-VALOR")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("TROQUE-ESTE-VALOR");
    }

    @Test
    @DisplayName("chave com tamanho diferente de 32 bytes faz a aplicação falhar ao iniciar")
    void wrongLengthKeyFailsFast() {
        String sixteenBytes = base64Key("0123456789abcdef");
        assertThatThrownBy(() -> new AesGcmEncryptionService(new CryptoProperties(sixteenBytes)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(sixteenBytes);
    }

    private static String base64Key(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
