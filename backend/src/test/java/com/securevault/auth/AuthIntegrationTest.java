package com.securevault.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.securevault.config.JwtProperties;
import com.securevault.security.JwtService;
import com.securevault.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Autenticação (integração)")
class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JwtProperties jwtProperties;

    // ---------- cadastro ----------

    @Test
    @DisplayName("cadastro válido retorna 201, não expõe senha e grava hash BCrypt")
    void registerSuccess() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana Silva", "Ana.Silva@Example.com", PASSWORD), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Ana Silva"))
                .andExpect(jsonPath("$.email").value("ana.silva@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String storedHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "ana.silva@example.com");
        assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
    }

    @Test
    @DisplayName("cadastro com e-mail inválido é rejeitado com 400 e aponta o campo")
    void registerInvalidEmail() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "isto-nao-e-email", PASSWORD), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists());
        assertThat(countUsers()).isZero();
    }

    @Test
    @DisplayName("cadastro com senha curta (9 caracteres) é rejeitado")
    void registerShortPassword() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", "123456789"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
        assertThat(countUsers()).isZero();
    }

    @Test
    @DisplayName("cadastro com senha acima de 72 caracteres é rejeitado (limite do BCrypt)")
    void registerTooLongPassword() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", "a".repeat(73)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
    }

    @Test
    @DisplayName("cadastro sem nome é rejeitado")
    void registerBlankName() throws Exception {
        postJson("/api/v1/auth/register", registerBody("   ", "ana@example.com", PASSWORD), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='name')]").exists());
    }

    @Test
    @DisplayName("cadastro com campo extra (ex.: role) é rejeitado: sem atribuição indevida")
    void registerUnknownPropertyRejected() throws Exception {
        Map<String, Object> body = registerBody("Ana", "ana@example.com", PASSWORD);
        body.put("role", "ADMIN");
        postJson("/api/v1/auth/register", body, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(countUsers()).isZero();
    }

    @Test
    @DisplayName("e-mail duplicado (inclusive com outra capitalização) retorna 409")
    void registerDuplicateEmail() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        postJson("/api/v1/auth/register", registerBody("Outra Ana", "ANA@example.com", PASSWORD), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertThat(countUsers()).isEqualTo(1);
    }

    // ---------- login ----------

    @Test
    @DisplayName("login correto retorna 200 e emite cookie HttpOnly, SameSite=Strict, sem token no corpo")
    void loginSuccess() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        MvcResult result = postJson("/api/v1/auth/login", loginBody("ana@example.com", PASSWORD), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith("vault_token=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api")
                .contains("Max-Age=3600");
    }

    @Test
    @DisplayName("login aceita e-mail com capitalização diferente")
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());
        postJson("/api/v1/auth/login", loginBody("ANA@Example.com", PASSWORD), null)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("login com senha incorreta retorna 401 genérico e não emite cookie")
    void loginWrongPassword() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        postJson("/api/v1/auth/login", loginBody("ana@example.com", "senha-errada-123"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("e-mail inexistente e senha errada produzem a mesma resposta (sem enumeração de contas)")
    void loginUnknownEmailLooksLikeWrongPassword() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        JsonNode wrongPassword = readTree(
                postJson("/api/v1/auth/login", loginBody("ana@example.com", "senha-errada-123"), null)
                        .andExpect(status().isUnauthorized()));
        JsonNode unknownEmail = readTree(
                postJson("/api/v1/auth/login", loginBody("ninguem@example.com", "senha-errada-123"), null)
                        .andExpect(status().isUnauthorized()));

        assertThat(unknownEmail.get("code").asText()).isEqualTo(wrongPassword.get("code").asText());
        assertThat(unknownEmail.get("message").asText()).isEqualTo(wrongPassword.get("message").asText());
    }

    @Test
    @DisplayName("após 5 falhas o login é bloqueado com 429 e Retry-After, mesmo com a senha certa")
    void loginRateLimited() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", loginBody("ana@example.com", "senha-errada-123"), null)
                    .andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/auth/login", loginBody("ana@example.com", PASSWORD), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    @DisplayName("o bloqueio de um e-mail não afeta o login de outro e-mail")
    void rateLimitIsPerEmail() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());
        postJson("/api/v1/auth/register", registerBody("Bia", "bia@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            postJson("/api/v1/auth/login", loginBody("ana@example.com", "senha-errada-123"), null)
                    .andExpect(status().isUnauthorized());
        }

        postJson("/api/v1/auth/login", loginBody("bia@example.com", PASSWORD), null)
                .andExpect(status().isOk());
    }

    // ---------- identificação do usuário autenticado ----------

    @Test
    @DisplayName("/me sem autenticação retorna 401")
    void meWithoutAuthentication() throws Exception {
        getJson("/api/v1/auth/me", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("/me com sessão válida retorna o usuário autenticado")
    void meWithValidSession() throws Exception {
        TestUser ana = newUser("ana");
        getJson("/api/v1/auth/me", ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.id().toString()))
                .andExpect(jsonPath("$.email").value(ana.email()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("token adulterado retorna 401 TOKEN_INVALID")
    void meWithTamperedToken() throws Exception {
        TestUser ana = newUser("ana");
        String token = ana.cookie().getValue();
        String tampered = token.substring(0, token.length() - 3)
                + (token.endsWith("AAA") ? "BBB" : "AAA");

        getJson("/api/v1/auth/me", new Cookie("vault_token", tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("valor aleatório no cookie retorna 401 TOKEN_INVALID")
    void meWithGarbageToken() throws Exception {
        getJson("/api/v1/auth/me", new Cookie("vault_token", "isto.nao.e-um-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("token expirado retorna 401 TOKEN_EXPIRED")
    void meWithExpiredToken() throws Exception {
        TestUser ana = newUser("ana");
        Clock threeHoursAgo = Clock.fixed(Instant.now().minusSeconds(3 * 3600), ZoneOffset.UTC);
        String expired = new JwtService(jwtProperties, threeHoursAgo).generateToken(ana.id());

        getJson("/api/v1/auth/me", new Cookie("vault_token", expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("token assinado com outra chave é rejeitado")
    void meWithTokenSignedByAnotherKey() throws Exception {
        TestUser ana = newUser("ana");
        JwtProperties other = new JwtProperties(
                "another-secret-another-secret-another-secret-123456", 60, jwtProperties.issuer());
        String forged = new JwtService(other, Clock.systemUTC()).generateToken(ana.id());

        getJson("/api/v1/auth/me", new Cookie("vault_token", forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("token válido de usuário que não existe mais retorna 401")
    void meWithValidTokenOfDeletedUser() throws Exception {
        TestUser ana = newUser("ana");
        jdbc.update("DELETE FROM users WHERE id = ?", ana.id());

        getJson("/api/v1/auth/me", ana.cookie())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rota inexistente sem autenticação também retorna 401 (negar por padrão)")
    void unknownRouteRequiresAuthentication() throws Exception {
        getJson("/api/v1/nao-existe", null).andExpect(status().isUnauthorized());
        getJson("/actuator/env", null).andExpect(status().isUnauthorized());
    }

    // ---------- logout ----------

    @Test
    @DisplayName("logout retorna 204 e expira o cookie de sessão")
    void logoutClearsCookie() throws Exception {
        TestUser ana = newUser("ana");

        MvcResult result = postJson("/api/v1/auth/logout", Map.of(), ana.cookie())
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("vault_token=")
                .contains("Max-Age=0")
                .contains("HttpOnly");
    }

    // ---------- CSRF ----------

    @Test
    @DisplayName("POST sem token CSRF é bloqueado com 403 CSRF_INVALID")
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginBody("ana@example.com", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("requisição autenticada que altera estado sem CSRF é bloqueada, e nada é gravado")
    void authenticatedWriteWithoutCsrfTokenIsForbidden() throws Exception {
        TestUser ana = newUser("ana");

        mockMvc.perform(post(ITEMS_URL)
                        .cookie(ana.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemBody("Titulo", "segredo"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(countItems()).isZero();
    }

    @Test
    @DisplayName("fluxo real de double submit: cookie XSRF-TOKEN + cabeçalho X-XSRF-TOKEN iguais são aceitos")
    void doubleSubmitCookieFlow() throws Exception {
        postJson("/api/v1/auth/register", registerBody("Ana", "ana@example.com", PASSWORD), null)
                .andExpect(status().isCreated());

        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).as("o JS precisa ler este cookie").isFalse();
        assertThat(csrfResult.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Strict");

        String loginJson = objectMapper.writeValueAsString(loginBody("ana@example.com", PASSWORD));

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    // ---------- cabeçalhos e respostas de erro ----------

    @Test
    @DisplayName("respostas incluem cabeçalhos de segurança")
    void securityHeadersPresent() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("erros 401 não vazam stack trace nem detalhes internos")
    void errorBodiesDoNotLeakInternals() throws Exception {
        String body = getJson("/api/v1/auth/me", null)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("Exception")
                .doesNotContain("at com.")
                .doesNotContain("org.springframework")
                .doesNotContain("trace");
    }

    private long countUsers() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
        return count == null ? 0 : count;
    }
}
