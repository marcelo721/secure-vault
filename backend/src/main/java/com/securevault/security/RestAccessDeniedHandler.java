package com.securevault.security;

import com.securevault.exception.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** Responde 403 em JSON padronizado (inclui falhas de validação do token CSRF). */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorWriter;

    public RestAccessDeniedHandler(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                    "Token CSRF ausente ou inválido.");
        } else {
            errorWriter.write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Acesso negado.");
        }
    }
}
