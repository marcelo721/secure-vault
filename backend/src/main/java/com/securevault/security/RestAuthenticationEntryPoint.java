package com.securevault.security;

import com.securevault.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Responde 401 em JSON padronizado para requisições sem autenticação válida. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorWriter;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        if (JwtAuthenticationFilter.TOKEN_EXPIRED.equals(reason)) {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED",
                    "Sua sessão expirou. Faça login novamente.");
        } else if (JwtAuthenticationFilter.TOKEN_INVALID.equals(reason)) {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "TOKEN_INVALID",
                    "Sessão inválida. Faça login novamente.");
        } else {
            errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Autenticação necessária.");
        }
    }
}
