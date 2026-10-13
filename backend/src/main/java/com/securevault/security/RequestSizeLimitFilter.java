package com.securevault.security;

import com.securevault.config.RequestLimitProperties;
import com.securevault.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejeita com 413 requisições cujo Content-Length declarado excede o limite configurado,
 * antes de qualquer leitura/parse do corpo. Os limites por campo continuam sendo
 * validados pelo Bean Validation.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ErrorResponseWriter errorWriter;

    public RequestSizeLimitFilter(RequestLimitProperties properties, ErrorResponseWriter errorWriter) {
        this.maxBodyBytes = properties.maxBodyBytes();
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            errorWriter.write(request, response, HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "O corpo da requisição excede o tamanho máximo permitido.");
            return;
        }
        chain.doFilter(request, response);
    }
}
