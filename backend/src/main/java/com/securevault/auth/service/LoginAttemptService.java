package com.securevault.auth.service;

import com.securevault.config.LoginAttemptProperties;
import com.securevault.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Limitador simples de tentativas de login, em memória, por chave (IP + e-mail normalizado).
 *
 * <p>Após {@code maxFailures} falhas dentro da janela, a chave é bloqueada por {@code lockMinutes}.
 * Limitação conhecida: o estado é local à instância e se perde ao reiniciar (ver README).
 */
@Service
public class LoginAttemptService {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Entry(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();
    private final LoginAttemptProperties properties;
    private final Clock clock;

    public LoginAttemptService(LoginAttemptProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Lança {@link TooManyAttemptsException} se a chave estiver bloqueada. */
    public void checkAllowed(String key) {
        Instant now = clock.instant();
        Entry entry = attempts.get(key);
        if (entry == null || entry.lockedUntil() == null) {
            return;
        }
        if (now.isBefore(entry.lockedUntil())) {
            long retryAfter = Math.max(1, Duration.between(now, entry.lockedUntil()).toSeconds() + 1);
            throw new TooManyAttemptsException(retryAfter);
        }
        // Bloqueio expirado: recomeça do zero.
        attempts.remove(key, entry);
    }

    public void recordFailure(String key) {
        Instant now = clock.instant();
        Duration window = Duration.ofMinutes(properties.windowMinutes());
        attempts.compute(key, (k, current) -> {
            int failures = 1;
            Instant windowStart = now;
            if (current != null && now.isBefore(current.windowStart().plus(window))) {
                failures = current.failures() + 1;
                windowStart = current.windowStart();
            }
            Instant lockedUntil = failures >= properties.maxFailures()
                    ? now.plus(Duration.ofMinutes(properties.lockMinutes()))
                    : null;
            return new Entry(failures, windowStart, lockedUntil);
        });
        if (attempts.size() > CLEANUP_THRESHOLD) {
            cleanup(now, window);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    /** Remove todo o estado (usado apenas em testes). */
    public void reset() {
        attempts.clear();
    }

    private void cleanup(Instant now, Duration window) {
        attempts.entrySet().removeIf(e -> {
            Entry v = e.getValue();
            boolean lockExpired = v.lockedUntil() == null || now.isAfter(v.lockedUntil());
            return lockExpired && now.isAfter(v.windowStart().plus(window));
        });
    }
}
