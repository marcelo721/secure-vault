package com.securevault.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.securevault.auth.service.AuthService;
import com.securevault.auth.service.LoginAttemptService;
import com.securevault.exception.EmailAlreadyRegisteredException;
import com.securevault.exception.InvalidCredentialsException;
import com.securevault.exception.TooManyAttemptsException;
import com.securevault.security.JwtService;
import com.securevault.user.User;
import com.securevault.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService (unitário com Mockito)")
class AuthServiceTest {

    private static final String IP = "10.0.0.1";
    private static final String ATTEMPT_KEY = IP + "|ana@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        // O construtor gera um hash descartável para igualar o tempo de resposta
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        service = new AuthService(userRepository, passwordEncoder, jwtService, loginAttemptService);
    }

    // ---------- cadastro ----------

    @Test
    @DisplayName("cadastro normaliza o e-mail, apara o nome e grava somente o hash da senha")
    void registerNormalizesAndHashes() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("S3nh4-forte!")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.register("  Ana  ", "  Ana@Example.COM ", "S3nh4-forte!");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Ana");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash-bcrypt").isNotEqualTo("S3nh4-forte!");
    }

    @Test
    @DisplayName("cadastro com e-mail existente falha com conflito e não grava nada")
    void registerDuplicateEmail() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("Ana", "ana@example.com", "S3nh4-forte!"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("corrida de cadastro (restrição única do banco) também vira conflito")
    void registerRaceCondition() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("S3nh4-forte!")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> service.register("Ana", "ana@example.com", "S3nh4-forte!"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    // ---------- login ----------

    @Test
    @DisplayName("login correto emite token, zera o contador e verifica o bloqueio ANTES de consultar o usuário")
    void loginSuccess() {
        User user = new User("Ana", "ana@example.com", "hash-armazenado");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("S3nh4-forte!", "hash-armazenado")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("token-jwt");

        AuthService.LoginResult result = service.login("  ANA@example.com ", "S3nh4-forte!", IP);

        assertThat(result.token()).isEqualTo("token-jwt");
        assertThat(result.user()).isSameAs(user);
        InOrder order = inOrder(loginAttemptService, userRepository);
        order.verify(loginAttemptService).checkAllowed(ATTEMPT_KEY);
        order.verify(userRepository).findByEmail("ana@example.com");
        verify(loginAttemptService).recordSuccess(ATTEMPT_KEY);
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    @DisplayName("senha incorreta registra a falha e não emite token")
    void loginWrongPassword() {
        User user = new User("Ana", "ana@example.com", "hash-armazenado");
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada", "hash-armazenado")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@example.com", "errada", IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(loginAttemptService).recordFailure(ATTEMPT_KEY);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("e-mail inexistente ainda executa a comparação de hash (tempo uniforme) e falha igual")
    void loginUnknownEmailStillComparesHash() {
        when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("qualquer", "dummy-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@example.com", "qualquer", IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("E-mail ou senha inválidos.");

        verify(passwordEncoder).matches("qualquer", "dummy-hash");
        verify(loginAttemptService).recordFailure(ATTEMPT_KEY);
    }

    @Test
    @DisplayName("chave bloqueada: não consulta usuário nem compara senha")
    void loginBlocked() {
        doThrow(new TooManyAttemptsException(900)).when(loginAttemptService).checkAllowed(ATTEMPT_KEY);

        assertThatThrownBy(() -> service.login("ana@example.com", "S3nh4-forte!", IP))
                .isInstanceOf(TooManyAttemptsException.class);

        verifyNoInteractions(userRepository);
        verify(passwordEncoder, never()).matches(any(), any());
    }
}
