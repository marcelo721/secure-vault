package com.securevault.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Lê o JWT do cookie HttpOnly e, se válido, autentica a requisição.
 * Token ausente, inválido ou expirado simplesmente deixa a requisição não autenticada;
 * quem responde 401 é o {@link RestAuthenticationEntryPoint}.
 * Instanciado manualmente no {@link SecurityConfig} (não é um @Component) para rodar só dentro da cadeia de segurança.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo da requisição usado para diferenciar token expirado de inválido na resposta 401. */
    static final String AUTH_ERROR_ATTRIBUTE = "securevault.auth.error";
    static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    static final String TOKEN_INVALID = "TOKEN_INVALID";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie cookie = WebUtils.getCookie(request, CookieService.ACCESS_COOKIE);
        if (cookie != null && !cookie.getValue().isBlank()) {
            try {
                AuthenticatedUser user = jwtService.parseToken(cookie.getValue());
                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException e) {
                // Nunca registra o token nem a mensagem da exceção.
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, TOKEN_INVALID);
            }
        }
        chain.doFilter(request, response);
    }
}
