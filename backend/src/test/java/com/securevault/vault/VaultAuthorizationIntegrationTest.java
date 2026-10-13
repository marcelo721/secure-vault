package com.securevault.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.JsonNode;
import com.securevault.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Testes do requisito mais importante: um usuário nunca acessa dados de outro, mesmo conhecendo o ID.
 * Cada teste usa dois usuários reais, autenticados pelo fluxo de login.
 */
@DisplayName("Cofre: autenticação e autorização por proprietário (integração)")
class VaultAuthorizationIntegrationTest extends IntegrationTestBase {

    // ---------- rotas privadas exigem autenticação ----------

    @Test
    @DisplayName("todas as rotas do cofre retornam 401 sem autenticação")
    void allVaultRoutesRequireAuthentication() throws Exception {
        UUID someId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(itemBody("Titulo", "segredo"));

        mockMvc.perform(get(ITEMS_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ITEMS_URL + "/" + someId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ITEMS_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(ITEMS_URL + "/" + someId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(ITEMS_URL + "/" + someId).with(csrf())).andExpect(status().isUnauthorized());

        assertThat(countItems()).isZero();
    }

    // ---------- criação ----------

    @Test
    @DisplayName("criação define o proprietário a partir do usuário autenticado")
    void createSetsOwnerFromAuthenticatedUser() throws Exception {
        TestUser ana = newUser("ana");

        UUID id = createItem(ana, "Conta do banco", "s3nh4-secreta");

        UUID ownerId = jdbc.queryForObject("SELECT owner_id FROM vault_items WHERE id = ?", UUID.class, id);
        assertThat(ownerId).isEqualTo(ana.id());
    }

    @Test
    @DisplayName("o cliente não consegue escolher o proprietário: campo ownerId é rejeitado com 400")
    void clientCannotChooseOwner() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");

        Map<String, Object> body = itemBody("Titulo", "segredo");
        body.put("ownerId", bia.id().toString());

        postJson(ITEMS_URL, body, ana.cookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(countItems()).isZero();
    }

    @Test
    @DisplayName("o cliente não consegue definir o ID do registro")
    void clientCannotChooseId() throws Exception {
        TestUser ana = newUser("ana");
        Map<String, Object> body = itemBody("Titulo", "segredo");
        body.put("id", UUID.randomUUID().toString());

        postJson(ITEMS_URL, body, ana.cookie()).andExpect(status().isBadRequest());
        assertThat(countItems()).isZero();
    }

    @Test
    @DisplayName("criação retorna 201, Location e nenhum campo confidencial nem de proprietário")
    void createResponseShape() throws Exception {
        TestUser ana = newUser("ana");

        postJson(ITEMS_URL, itemBody("Conta do banco", "s3nh4-secreta"), ana.cookie())
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Conta do banco"))
                .andExpect(jsonPath("$.type").value("CREDENTIAL"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    // ---------- listagem ----------

    @Test
    @DisplayName("a listagem retorna somente os registros do proprietário")
    void listReturnsOnlyOwnItems() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID anaItem1 = createItem(ana, "Ana 1", "segredo-a1");
        UUID anaItem2 = createItem(ana, "Ana 2", "segredo-a2");
        UUID biaItem = createItem(bia, "Bia 1", "segredo-b1");

        JsonNode anaList = readTree(getJson(ITEMS_URL, ana.cookie()).andExpect(status().isOk()));
        JsonNode biaList = readTree(getJson(ITEMS_URL, bia.cookie()).andExpect(status().isOk()));

        assertThat(anaList.size()).isEqualTo(2);
        assertThat(anaList.findValuesAsText("id")).containsExactlyInAnyOrder(anaItem1.toString(), anaItem2.toString());
        assertThat(anaList.findValuesAsText("id")).doesNotContain(biaItem.toString());
        assertThat(biaList.size()).isEqualTo(1);
        assertThat(biaList.get(0).get("id").asText()).isEqualTo(biaItem.toString());
    }

    @Test
    @DisplayName("usuário sem registros recebe lista vazia, não os de outros")
    void listIsEmptyForUserWithoutItems() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        createItem(ana, "Ana 1", "segredo-a1");

        getJson(ITEMS_URL, bia.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---------- leitura ----------

    @Test
    @DisplayName("o proprietário lê o registro com os campos descriptografados")
    void ownerCanReadItem() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Conta do banco", "s3nh4-secreta");

        getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Conta do banco"))
                .andExpect(jsonPath("$.username").value("usuario@example.com"))
                .andExpect(jsonPath("$.secret").value("s3nh4-secreta"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    @DisplayName("outro usuário NÃO lê o registro, mesmo conhecendo o ID: 404 sem vazar o conteúdo")
    void otherUserCannotReadItem() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID id = createItem(ana, "Conta do banco", "s3nh4-super-secreta");

        String body = getJson(ITEMS_URL + "/" + id, bia.cookie())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("s3nh4-super-secreta").doesNotContain("Conta do banco");
    }

    @Test
    @DisplayName("registro de outro usuário e registro inexistente produzem respostas idênticas (404)")
    void foreignAndMissingItemsAreIndistinguishable() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID anaItem = createItem(ana, "Conta do banco", "segredo");

        JsonNode foreign = readTree(getJson(ITEMS_URL + "/" + anaItem, bia.cookie())
                .andExpect(status().isNotFound()));
        JsonNode missing = readTree(getJson(ITEMS_URL + "/" + UUID.randomUUID(), bia.cookie())
                .andExpect(status().isNotFound()));

        assertThat(foreign.get("status").asInt()).isEqualTo(missing.get("status").asInt());
        assertThat(foreign.get("code").asText()).isEqualTo(missing.get("code").asText());
        assertThat(foreign.get("message").asText()).isEqualTo(missing.get("message").asText());
    }

    @Test
    @DisplayName("ID malformado retorna 400 sem detalhes internos")
    void malformedIdReturns400() throws Exception {
        TestUser ana = newUser("ana");
        String body = getJson(ITEMS_URL + "/nao-e-um-uuid", ana.cookie())
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("java.");
    }

    // ---------- edição ----------

    @Test
    @DisplayName("o proprietário edita o registro")
    void ownerCanUpdateItem() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Titulo antigo", "segredo-antigo");

        Map<String, Object> update = itemBody("Titulo novo", "segredo-novo");
        update.put("type", "API_KEY");
        update.put("username", null);
        putJson(ITEMS_URL + "/" + id, update, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo novo"))
                .andExpect(jsonPath("$.type").value("API_KEY"))
                .andExpect(jsonPath("$.secret").doesNotExist());

        getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo novo"))
                .andExpect(jsonPath("$.secret").value("segredo-novo"))
                .andExpect(jsonPath("$.username").isEmpty());
    }

    @Test
    @DisplayName("outro usuário NÃO edita o registro: 404 e o registro permanece intacto")
    void otherUserCannotUpdateItem() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID id = createItem(ana, "Titulo da Ana", "segredo-da-ana");

        putJson(ITEMS_URL + "/" + id, itemBody("Sequestrado", "segredo-da-bia"), bia.cookie())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo da Ana"))
                .andExpect(jsonPath("$.secret").value("segredo-da-ana"));
    }

    @Test
    @DisplayName("a edição não transfere a propriedade do registro")
    void updateCannotChangeOwner() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID id = createItem(ana, "Titulo", "segredo");

        Map<String, Object> update = itemBody("Titulo", "segredo");
        update.put("ownerId", bia.id().toString());
        putJson(ITEMS_URL + "/" + id, update, ana.cookie()).andExpect(status().isBadRequest());

        UUID ownerId = jdbc.queryForObject("SELECT owner_id FROM vault_items WHERE id = ?", UUID.class, id);
        assertThat(ownerId).isEqualTo(ana.id());
    }

    // ---------- exclusão ----------

    @Test
    @DisplayName("o proprietário exclui o registro (204) e depois ele não existe mais (404)")
    void ownerCanDeleteItem() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Titulo", "segredo");

        deleteJson(ITEMS_URL + "/" + id, ana.cookie()).andExpect(status().isNoContent());

        getJson(ITEMS_URL + "/" + id, ana.cookie()).andExpect(status().isNotFound());
        assertThat(countItems()).isZero();
    }

    @Test
    @DisplayName("outro usuário NÃO exclui o registro: 404 e o registro continua existindo")
    void otherUserCannotDeleteItem() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID id = createItem(ana, "Titulo da Ana", "segredo-da-ana");

        deleteJson(ITEMS_URL + "/" + id, bia.cookie())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(countItems()).isEqualTo(1);
        getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("segredo-da-ana"));
    }

    @Test
    @DisplayName("excluir registro inexistente retorna 404")
    void deleteMissingItem() throws Exception {
        TestUser ana = newUser("ana");
        deleteJson(ITEMS_URL + "/" + UUID.randomUUID(), ana.cookie()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("excluir o usuário remove seus registros (cascata) e preserva os dos outros")
    void deletingUserCascadesOnlyToOwnItems() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        createItem(ana, "Ana", "segredo-a");
        UUID biaItem = createItem(bia, "Bia", "segredo-b");

        jdbc.update("DELETE FROM users WHERE id = ?", ana.id());

        assertThat(countItems()).isEqualTo(1);
        getJson(ITEMS_URL + "/" + biaItem, bia.cookie()).andExpect(status().isOk());
    }
}
