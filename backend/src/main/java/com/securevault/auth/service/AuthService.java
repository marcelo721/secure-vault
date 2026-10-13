package com.securevault.auth.service;

import com.securevault.exception.EmailAlreadyRegisteredException;
import com.securevault.exception.InvalidCredentialsException;
import com.securevault.exception.UnauthorizedException;
import com.securevault.security.JwtService;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record LoginResult(User user, String token) {
    }

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    /** Hash descartável para igualar o tempo de resposta quando o e-mail não existe. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(String name, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Cadastro concorrente com o mesmo e-mail: a restrição única do banco é a fonte da verdade.
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String rawPassword, String clientIp) {
        String normalizedEmail = normalizeEmail(email);
        String attemptKey = clientIp + "|" + normalizedEmail;

        loginAttemptService.checkAllowed(attemptKey);

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        String hashToCheck = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hashToCheck);

        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(attemptKey);
            throw new InvalidCredentialsException();
        }

        loginAttemptService.recordSuccess(attemptKey);
        return new LoginResult(user, jwtService.generateToken(user.getId()));
    }

    @Transactional(readOnly = true)
    public User getById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UnauthorizedException::new);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
