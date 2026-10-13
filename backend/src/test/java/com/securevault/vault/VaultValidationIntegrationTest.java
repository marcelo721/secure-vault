package com.securevault.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.securevault.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@DisplayName("Cofre: validação de entrada e tratamento de erros (integração)")
class VaultValidationIntegrationTest extends IntegrationTestBase {

    private void assertRejectedField(TestUser user, Map<String, Object> body, String field) throws Exception {
        postJson(ITEMS_URL, body, user.cookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='" + field + "')]").exists());
        assertThat(countItems()).as("nada deve ser gravado").isZero();
    }

    @Test
    @DisplayName("título ausente ou em branco é rejeitado")
    void blankTitle() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> body = itemBody("   ", "segredo");
        assertRejectedField(ana, body, "title");
    }

    @Test
    @DisplayName("título acima de 150 caracteres é rejeitado")
    void titleTooLong() throws Exception {
        TestUser ana = newUser("ana");
        assertRejectedField(ana, itemBody("t".repeat(151), "segredo"), "title");
    }

    @Test
    @DisplayName("segredo vazio é rejeitado")
    void blankSecret() throws Exception {
        TestUser ana = newUser("ana");
        assertRejectedField(ana, itemBody("Titulo", ""), "secret");
    }

    @Test
    @DisplayName("segredo acima de 10.000 caracteres é rejeitado (400), no limite exato é aceito")
    void secretTooLong() throws Exception {
        TestUser ana = newUser("ana");
        assertRejectedField(ana, itemBody("Titulo", "s".repeat(10_001)), "secret");

        postJson(ITEMS_URL, itemBody("Titulo", "s".repeat(10_000)), ana.cookie())
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("usuário/identificador acima de 255 caracteres é rejeitado")
    void usernameTooLong() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> body = itemBody("Titulo", "segredo");
        body.put("username", "u".repeat(256));
        assertRejectedField(ana, body, "username");
    }

    @Test
    @DisplayName("descrição acima de 1000 caracteres é rejeitada")
    void descriptionTooLong() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> body = itemBody("Titulo", "segredo");
        body.put("description", "d".repeat(1001));
        assertRejectedField(ana, body, "description");
    }

    @Test
    @DisplayName("URL com esquema perigoso (javascript:) é rejeitada")
    void dangerousUrlScheme() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> body = itemBody("Titulo", "segredo");
        body.put("url", "javascript:alert(1)");
        assertRejectedField(ana, body, "url");
    }

    @Test
    @DisplayName("tipo ausente é rejeitado; tipo desconhecido retorna 400")
    void invalidType() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> missing = itemBody("Titulo", "segredo");
        missing.put("type", null);
        assertRejectedField(ana, missing, "type");

        Map<String, Object> unknown = itemBody("Titulo", "segredo");
        unknown.put("type", "NAO_EXISTE");
        postJson(ITEMS_URL, unknown, ana.cookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("a validação também vale para a edição")
    void validationAppliesToUpdate() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Titulo", "segredo");

        putJson(ITEMS_URL + "/" + id, itemBody("", "segredo"), ana.cookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("corpo excessivamente grande é rejeitado com 413 antes de ser processado")
    void oversizedPayloadRejected() throws Exception {
        TestUser ana = newUser("ana");
        String huge = objectMapper.writeValueAsString(itemBody("Titulo", "x".repeat(70_000)));

        mockMvc.perform(post(ITEMS_URL)
                        .with(csrf())
                        .cookie(ana.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(huge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        assertThat(countItems()).isZero();
    }

    @Test
    @DisplayName("JSON malformado retorna 400 sem ecoar o conteúdo enviado")
    void malformedJson() throws Exception {
        TestUser ana = newUser("ana");
        String body = mockMvc.perform(post(ITEMS_URL)
                        .with(csrf())
                        .cookie(ana.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"segredo-vazado\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("segredo-vazado");
    }

    @Test
    @DisplayName("tipo de conteúdo não suportado retorna 415")
    void unsupportedMediaType() throws Exception {
        TestUser ana = newUser("ana");
        mockMvc.perform(post(ITEMS_URL)
                        .with(csrf())
                        .cookie(ana.cookie())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("title=x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("método não suportado retorna 405 no formato padrão de erro")
    void methodNotAllowed() throws Exception {
        TestUser ana = newUser("ana");
        mockMvc.perform(patch(ITEMS_URL + "/" + UUID.randomUUID())
                        .with(csrf())
                        .cookie(ana.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("erro de validação não ecoa o valor do segredo enviado")
    void validationErrorsDoNotEchoSecrets() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> body = itemBody("", "segredo-que-nao-pode-vazar");
        String response = postJson(ITEMS_URL, body, ana.cookie())
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("segredo-que-nao-pode-vazar");
    }
}
