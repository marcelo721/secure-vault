package com.securevault.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.auth.service.LoginAttemptService;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base dos testes de integração: sobe a aplicação completa (filtros de segurança reais, Flyway e PostgreSQL de teste).
 * O banco é limpo antes de cada teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Str0ng-Passw0rd!";
    protected static final String ITEMS_URL = "/api/v1/vault-items";

    /** Usuário de teste já cadastrado e autenticado (cookie de sessão real, obtido pelo endpoint de login). */
    protected record TestUser(UUID id, String email, Cookie cookie) {
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected LoginAttemptService loginAttemptService;

    @BeforeEach
    protected void cleanDatabase() {
        jdbc.update("DELETE FROM vault_items");
        jdbc.update("DELETE FROM users");
        loginAttemptService.reset();
    }

    // ---------- usuários ----------

    protected TestUser newUser(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String body = postJson("/api/v1/auth/register", registerBody("Usuário " + prefix, email, PASSWORD), null)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        return new TestUser(id, email, login(email, PASSWORD));
    }

    protected Cookie login(String email, String password) throws Exception {
        Cookie cookie = postJson("/api/v1/auth/login", loginBody(email, password), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("vault_token");
        if (cookie == null) {
            throw new AssertionError("O login não emitiu o cookie de sessão");
        }
        return cookie;
    }

    protected static Map<String, Object> registerBody(String name, String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    protected static Map<String, Object> loginBody(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    // ---------- registros do cofre ----------

    protected static Map<String, Object> itemBody(String title, String secret) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("type", "CREDENTIAL");
        body.put("username", "usuario@example.com");
        body.put("secret", secret);
        body.put("url", "https://example.com/login");
        body.put("description", "Descrição de teste");
        return body;
    }

    protected UUID createItem(TestUser owner, String title, String secret) throws Exception {
        String response = postJson(ITEMS_URL, itemBody(title, secret), owner.cookie())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // ---------- requisições (com CSRF válido, para isolar o comportamento testado) ----------

    protected ResultActions postJson(String url, Object body, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = post(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions putJson(String url, Object body, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = put(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions getJson(String url, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request);
    }

    protected ResultActions deleteJson(String url, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder request = delete(url).with(csrf());
        if (cookie != null) {
            request.cookie(cookie);
        }
        return mockMvc.perform(request);
    }

    protected JsonNode readTree(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected long countItems() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM vault_items", Long.class);
        return count == null ? 0 : count;
    }
}
