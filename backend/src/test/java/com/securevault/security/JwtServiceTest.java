package com.securevault.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securevault.config.JwtProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JWT (unitário)")
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234567890";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private final JwtProperties properties = new JwtProperties(SECRET, 60, "secure-vault");

    private JwtService serviceAt(Instant instant) {
        return new JwtService(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("token válido devolve o ID do usuário")
    void validTokenRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = serviceAt(NOW).generateToken(userId);

        assertThat(serviceAt(NOW.plusSeconds(30)).parseToken(token).id()).isEqualTo(userId);
    }

    @Test
    @DisplayName("token vale até o fim da expiração e é rejeitado depois")
    void expiration() {
        String token = serviceAt(NOW).generateToken(UUID.randomUUID());

        // 59 min 59 s: ainda válido
        assertThat(serviceAt(NOW.plusSeconds(3599)).parseToken(token)).isNotNull();
        // 61 min: expirado
        assertThatThrownBy(() -> serviceAt(NOW.plusSeconds(3660)).parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("assinatura adulterada é rejeitada")
    void tamperedSignature() {
        String token = serviceAt(NOW).generateToken(UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> serviceAt(NOW).parseToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("payload alterado (outro usuário) invalida a assinatura")
    void tamperedPayload() {
        String token = serviceAt(NOW).generateToken(UUID.randomUUID());
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"secure-vault\",\"sub\":\"" + UUID.randomUUID() + "\",\"exp\":9999999999}").getBytes());
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> serviceAt(NOW).parseToken(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token assinado com outra chave é rejeitado")
    void differentSecret() {
        JwtProperties other = new JwtProperties("another-secret-another-secret-another-1234", 60, "secure-vault");
        String token = new JwtService(other, Clock.fixed(NOW, ZoneOffset.UTC)).generateToken(UUID.randomUUID());

        assertThatThrownBy(() -> serviceAt(NOW).parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token com emissor diferente é rejeitado")
    void differentIssuer() {
        JwtProperties other = new JwtProperties(SECRET, 60, "outro-emissor");
        String token = new JwtService(other, Clock.fixed(NOW, ZoneOffset.UTC)).generateToken(UUID.randomUUID());

        assertThatThrownBy(() -> serviceAt(NOW).parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token sem assinatura (alg=none) é rejeitado")
    void unsignedTokenRejected() {
        String unsigned = Jwts.builder()
                .issuer("secure-vault")
                .subject(UUID.randomUUID().toString())
                .compact();

        assertThatThrownBy(() -> serviceAt(NOW).parseToken(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("subject que não é UUID é rejeitado")
    void nonUuidSubjectRejected() {
        String token = Jwts.builder()
                .issuer("secure-vault")
                .subject("nao-e-uuid")
                .expiration(java.util.Date.from(NOW.plusSeconds(600)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> serviceAt(NOW).parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("lixo e strings vazias são rejeitados")
    void garbageRejected() {
        assertThatThrownBy(() -> serviceAt(NOW).parseToken("isto.nao.e-jwt")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> serviceAt(NOW).parseToken("abc")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> serviceAt(NOW).parseToken("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("o token contém apenas o ID do usuário, sem e-mail ou dados pessoais")
    void tokenCarriesOnlyUserId() {
        UUID userId = UUID.randomUUID();
        String token = serviceAt(NOW).generateToken(userId);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));

        assertThat(payload).contains(userId.toString()).doesNotContain("@").doesNotContain("email");
    }
}
