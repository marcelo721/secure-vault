package com.securevault.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securevault.auth.service.LoginAttemptService;
import com.securevault.config.LoginAttemptProperties;
import com.securevault.exception.TooManyAttemptsException;
import com.securevault.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Limite de tentativas de login (unitário)")
class LoginAttemptServiceTest {

    private static final String KEY = "127.0.0.1|ana@example.com";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        // 3 falhas dentro de 10 minutos bloqueiam por 15 minutos
        service = new LoginAttemptService(new LoginAttemptProperties(3, 10, 15), clock);
    }

    @Test
    @DisplayName("permite tentativas abaixo do limite")
    void allowsBelowLimit() {
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThatCode(() -> service.checkAllowed(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("bloqueia ao atingir o limite e informa quanto esperar")
    void blocksAtLimit() {
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThatThrownBy(() -> service.checkAllowed(KEY))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(14L * 60, 15L * 60 + 1));
    }

    @Test
    @DisplayName("o bloqueio expira após o período configurado")
    void lockExpires() {
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> service.checkAllowed(KEY)).isInstanceOf(TooManyAttemptsException.class);

        clock.advance(Duration.ofMinutes(2));
        assertThatCode(() -> service.checkAllowed(KEY)).doesNotThrowAnyException();

        // Depois de liberado, o contador recomeça do zero
        service.recordFailure(KEY);
        assertThatCode(() -> service.checkAllowed(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("login bem-sucedido zera o contador")
    void successResetsCounter() {
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordSuccess(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThatCode(() -> service.checkAllowed(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("falhas fora da janela de tempo não se acumulam")
    void failuresOutsideWindowDoNotAccumulate() {
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        clock.advance(Duration.ofMinutes(11));
        service.recordFailure(KEY);

        assertThatCode(() -> service.checkAllowed(KEY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("chaves diferentes são independentes")
    void keysAreIndependent() {
        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThatCode(() -> service.checkAllowed("127.0.0.1|bia@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> service.checkAllowed("10.0.0.9|ana@example.com")).doesNotThrowAnyException();
    }
}
