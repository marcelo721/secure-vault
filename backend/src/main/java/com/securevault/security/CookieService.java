package com.securevault.security;

import com.securevault.config.CookieProperties;
import com.securevault.config.JwtProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie de sessão: HttpOnly (inacessível ao JavaScript), SameSite=Strict e, em produção, Secure.
 * O path "/api" evita que o cookie seja enviado para outras rotas.
 */
@Component
public class CookieService {

    public static final String ACCESS_COOKIE = "vault_token";

    private final CookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public CookieService(CookieProperties cookieProperties, JwtProperties jwtProperties) {
        this.cookieProperties = cookieProperties;
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie accessCookie(String token) {
        return base(token)
                .maxAge(Duration.ofMinutes(jwtProperties.expirationMinutes()))
                .build();
    }

    public ResponseCookie clearCookie() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(ACCESS_COOKIE, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Strict")
                .path("/api");
    }
}
