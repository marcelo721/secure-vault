package com.securevault.security;

import com.securevault.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emissão e validação de JWT assinado com HS256.
 * O token carrega apenas o ID do usuário (claim "sub"), emissor, emissão e expiração.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.issuer = properties.issuer();
        this.ttl = Duration.ofMinutes(properties.expirationMinutes());
        this.clock = clock;
    }

    public String generateToken(UUID userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida assinatura, emissor e expiração.
     *
     * @throws io.jsonwebtoken.ExpiredJwtException token expirado
     * @throws JwtException                        token malformado, adulterado ou inválido
     */
    public AuthenticatedUser parseToken(String token) {
        var claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            return new AuthenticatedUser(UUID.fromString(claims.getSubject()));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JwtException("Subject inválido");
        }
    }
}
