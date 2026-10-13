package com.securevault.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.securevault.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cofre: criptografia dos dados confidenciais (integração)")
class VaultEncryptionIntegrationTest extends IntegrationTestBase {

    private static final String SECRET = "minha-senha-ultra-secreta-9988";
    private static final String USERNAME = "usuario@example.com";

    @Test
    @DisplayName("segredo e usuário são gravados cifrados: o texto puro não aparece no banco")
    void secretsAreStoredEncrypted() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Conta do banco", SECRET);

        byte[] secretBytes = rawColumn("secret_encrypted", id);
        byte[] usernameBytes = rawColumn("username_encrypted", id);

        assertThat(new String(secretBytes, StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);
        assertThat(new String(usernameBytes, StandardCharsets.ISO_8859_1)).doesNotContain(USERNAME);

        // Formato: versão(1) + nonce(12) + texto cifrado (mesmo tamanho do texto) + tag GCM (16)
        assertThat(secretBytes[0]).isEqualTo((byte) 1);
        assertThat(secretBytes.length).isEqualTo(1 + 12 + SECRET.getBytes(StandardCharsets.UTF_8).length + 16);

        // Nenhuma coluna da linha contém o segredo ou o usuário em texto legível
        String wholeRow = jdbc.queryForObject(
                "SELECT t::text FROM vault_items t WHERE t.id = ?", String.class, id);
        assertThat(wholeRow).doesNotContain(SECRET).doesNotContain(USERNAME);
    }

    @Test
    @DisplayName("o mesmo segredo gerado duas vezes produz textos cifrados e nonces diferentes")
    void sameSecretProducesDifferentCiphertexts() throws Exception {
        TestUser ana = newUser("ana");
        UUID first = createItem(ana, "Um", SECRET);
        UUID second = createItem(ana, "Dois", SECRET);

        byte[] firstBytes = rawColumn("secret_encrypted", first);
        byte[] secondBytes = rawColumn("secret_encrypted", second);

        assertThat(firstBytes).isNotEqualTo(secondBytes);
        assertThat(java.util.Arrays.copyOfRange(firstBytes, 1, 13))
                .as("nonces não podem se repetir")
                .isNotEqualTo(java.util.Arrays.copyOfRange(secondBytes, 1, 13));
    }

    @Test
    @DisplayName("atualizar com o mesmo segredo gera um novo nonce")
    void updateUsesFreshNonce() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Titulo", SECRET);
        byte[] before = rawColumn("secret_encrypted", id);

        putJson(ITEMS_URL + "/" + id, itemBody("Titulo", SECRET), ana.cookie()).andExpect(status().isOk());

        byte[] after = rawColumn("secret_encrypted", id);
        assertThat(after).isNotEqualTo(before);
        getJson(ITEMS_URL + "/" + id, ana.cookie()).andExpect(jsonPath("$.secret").value(SECRET));
    }

    @Test
    @DisplayName("listagem, criação e edição nunca devolvem o segredo; só o detalhe autorizado devolve")
    void decryptionOnlyHappensInAuthorizedDetailFlow() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");

        String created = postJson(ITEMS_URL, itemBody("Titulo", SECRET), ana.cookie())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());
        assertThat(created).doesNotContain(SECRET).doesNotContain(USERNAME);

        String list = getJson(ITEMS_URL, ana.cookie()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(SECRET).doesNotContain(USERNAME).doesNotContain("\"secret\"");

        String updated = putJson(ITEMS_URL + "/" + id, itemBody("Titulo 2", SECRET), ana.cookie())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(updated).doesNotContain(SECRET);

        String foreign = getJson(ITEMS_URL + "/" + id, bia.cookie()).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(foreign).doesNotContain(SECRET);

        getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value(SECRET));
    }

    @Test
    @DisplayName("texto cifrado adulterado no banco falha com 500 genérico, sem vazar detalhes")
    void tamperedCiphertextFailsSafely() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Titulo", SECRET);

        byte[] tampered = rawColumn("secret_encrypted", id);
        tampered[tampered.length - 1] ^= 0x01; // altera um bit da tag de autenticação
        jdbc.update("UPDATE vault_items SET secret_encrypted = ? WHERE id = ?", tampered, id);

        String body = getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain(SECRET)
                .doesNotContain("AEADBadTag")
                .doesNotContain("Tag mismatch")
                .doesNotContain("javax.crypto")
                .doesNotContain("Exception")
                .doesNotContain("at com.");
    }

    @Test
    @DisplayName("texto cifrado de um registro não funciona em outro registro do mesmo usuário (AAD)")
    void ciphertextCannotBeSwappedBetweenItems() throws Exception {
        TestUser ana = newUser("ana");
        UUID first = createItem(ana, "Um", "segredo-do-primeiro");
        UUID second = createItem(ana, "Dois", "segredo-do-segundo");

        jdbc.update("UPDATE vault_items SET secret_encrypted = ? WHERE id = ?",
                rawColumn("secret_encrypted", second), first);

        getJson(ITEMS_URL + "/" + first, ana.cookie()).andExpect(status().isInternalServerError());
        getJson(ITEMS_URL + "/" + second, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("segredo-do-segundo"));
    }

    @Test
    @DisplayName("texto cifrado de um usuário não funciona em registro de outro usuário (AAD)")
    void ciphertextCannotBeMovedAcrossUsers() throws Exception {
        TestUser ana = newUser("ana");
        TestUser bia = newUser("bia");
        UUID anaItem = createItem(ana, "Ana", "segredo-da-ana");
        UUID biaItem = createItem(bia, "Bia", "segredo-da-bia");

        jdbc.update("UPDATE vault_items SET secret_encrypted = ? WHERE id = ?",
                rawColumn("secret_encrypted", anaItem), biaItem);

        String body = getJson(ITEMS_URL + "/" + biaItem, bia.cookie())
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("segredo-da-ana");
    }

    @Test
    @DisplayName("dado corrompido (formato inválido) também falha com 500 genérico")
    void corruptedPayloadFailsSafely() throws Exception {
        TestUser ana = newUser("ana");
        UUID id = createItem(ana, "Titulo", SECRET);
        jdbc.update("UPDATE vault_items SET secret_encrypted = ? WHERE id = ?", new byte[]{9, 9, 9}, id);

        JsonNode error = readTree(getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isInternalServerError()));
        assertThat(error.get("code").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.get("message").asText()).doesNotContain("9");
    }

    @Test
    @DisplayName("registro sem usuário/identificador opcional é gravado e lido corretamente")
    void optionalUsernameIsSupported() throws Exception {
        TestUser ana = newUser("ana");
        var body = itemBody("Nota privada", "conteudo da nota");
        body.put("type", "SECURE_NOTE");
        body.remove("username");
        body.remove("url");
        body.remove("description");

        String created = postJson(ITEMS_URL, body, ana.cookie()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        assertThat(rawColumnOrNull("username_encrypted", id)).isNull();
        getJson(ITEMS_URL + "/" + id, ana.cookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SECURE_NOTE"))
                .andExpect(jsonPath("$.secret").value("conteudo da nota"))
                .andExpect(jsonPath("$.username").isEmpty());
    }

    private byte[] rawColumn(String column, UUID id) {
        byte[] value = rawColumnOrNull(column, id);
        assertThat(value).as("coluna %s do registro %s", column, id).isNotNull();
        return value;
    }

    private byte[] rawColumnOrNull(String column, UUID id) {
        // O nome da coluna vem de constantes do teste, nunca de entrada externa.
        return jdbc.queryForObject("SELECT " + column + " FROM vault_items WHERE id = ?", byte[].class, id);
    }
}
