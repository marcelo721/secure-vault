package com.securevault.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.securevault.crypto.EncryptionService;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.vault.dto.VaultItemDetailResponse;
import com.securevault.vault.dto.VaultItemRequest;
import com.securevault.vault.entity.VaultItem;
import com.securevault.vault.entity.VaultItemType;
import com.securevault.vault.repository.VaultItemRepository;
import com.securevault.vault.service.VaultItemService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("VaultItemService (unitário com Mockito)")
class VaultItemServiceTest {

    private final UUID ownerId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @Mock
    private VaultItemRepository repository;
    @Mock
    private EncryptionService encryptionService;
    @InjectMocks
    private VaultItemService service;

    @Test
    @DisplayName("criação usa o proprietário informado, cifra segredo e usuário e vincula o AAD ao dono e ao registro")
    void createEncryptsAndBindsOwner() {
        VaultItemRequest request = new VaultItemRequest(
                "  Meu título ", VaultItemType.CREDENTIAL, "usuario", "segredo", "", null);
        when(encryptionService.encrypt(eq("segredo"), any())).thenReturn(new byte[]{1, 2, 3});
        when(encryptionService.encrypt(eq("usuario"), any())).thenReturn(new byte[]{4, 5});
        when(repository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(ownerId, request);

        ArgumentCaptor<VaultItem> saved = ArgumentCaptor.forClass(VaultItem.class);
        verify(repository).save(saved.capture());
        VaultItem item = saved.getValue();
        assertThat(item.getOwnerId()).isEqualTo(ownerId);
        assertThat(item.getTitle()).isEqualTo("Meu título");
        assertThat(item.getUrl()).as("URL em branco vira nula").isNull();
        assertThat(item.getSecretEncrypted()).containsExactly(1, 2, 3);
        assertThat(item.getUsernameEncrypted()).containsExactly(4, 5);

        ArgumentCaptor<byte[]> aad = ArgumentCaptor.forClass(byte[].class);
        verify(encryptionService).encrypt(eq("segredo"), aad.capture());
        assertThat(new String(aad.getValue(), StandardCharsets.UTF_8))
                .isEqualTo(ownerId + ":" + item.getId() + ":secret");
    }

    @Test
    @DisplayName("criação sem usuário não cifra nem grava o campo opcional")
    void createWithoutUsername() {
        VaultItemRequest request = new VaultItemRequest(
                "Nota", VaultItemType.SECURE_NOTE, null, "conteúdo", null, null);
        when(encryptionService.encrypt(eq("conteúdo"), any())).thenReturn(new byte[]{1});
        when(repository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(ownerId, request);

        ArgumentCaptor<VaultItem> saved = ArgumentCaptor.forClass(VaultItem.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUsernameEncrypted()).isNull();
        verify(encryptionService).encrypt(eq("conteúdo"), any());
        verifyNoMoreInteractions(encryptionService);
    }

    @Test
    @DisplayName("listagem consulta somente por proprietário e não descriptografa nada")
    void listIsOwnerScopedAndDoesNotDecrypt() {
        VaultItem item = new VaultItem(itemId, ownerId, "T", VaultItemType.OTHER, null, new byte[]{1}, null, null);
        when(repository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)).thenReturn(List.of(item));

        assertThat(service.list(ownerId)).hasSize(1);

        verify(repository).findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("leitura consulta por ID + proprietário e descriptografa com o AAD correto")
    void getDecryptsWithCorrectAad() {
        VaultItem item = new VaultItem(itemId, ownerId, "T", VaultItemType.CREDENTIAL,
                new byte[]{7}, new byte[]{8}, null, null);
        when(repository.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.of(item));
        byte[] secretAad = (ownerId + ":" + itemId + ":secret").getBytes(StandardCharsets.UTF_8);
        byte[] usernameAad = (ownerId + ":" + itemId + ":username").getBytes(StandardCharsets.UTF_8);
        when(encryptionService.decrypt(aryEq(new byte[]{8}), aryEq(secretAad))).thenReturn("segredo");
        when(encryptionService.decrypt(aryEq(new byte[]{7}), aryEq(usernameAad))).thenReturn("usuario");

        VaultItemDetailResponse response = service.get(ownerId, itemId);

        assertThat(response.secret()).isEqualTo("segredo");
        assertThat(response.username()).isEqualTo("usuario");
        verify(repository).findByIdAndOwnerId(itemId, ownerId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("registro de outro usuário (ou inexistente): 404 e nenhuma descriptografia é tentada")
    void getNotOwnedNeverDecrypts() {
        when(repository.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ownerId, itemId)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("edição de registro de outro usuário: 404, nada é cifrado nem salvo")
    void updateNotOwned() {
        when(repository.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.empty());
        VaultItemRequest request = new VaultItemRequest("T", VaultItemType.OTHER, null, "s", null, null);

        assertThatThrownBy(() -> service.update(ownerId, itemId, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).save(any());
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("edição reescreve os campos cifrados e mantém o proprietário")
    void updateReEncrypts() {
        VaultItem item = new VaultItem(itemId, ownerId, "Antigo", VaultItemType.CREDENTIAL,
                new byte[]{1}, new byte[]{2}, null, null);
        when(repository.findByIdAndOwnerId(itemId, ownerId)).thenReturn(Optional.of(item));
        when(encryptionService.encrypt(eq("novo-segredo"), any())).thenReturn(new byte[]{9, 9});
        when(repository.save(any(VaultItem.class))).thenAnswer(inv -> inv.getArgument(0));
        VaultItemRequest request = new VaultItemRequest(
                "Novo", VaultItemType.API_KEY, null, "novo-segredo", "https://exemplo.com", "desc");

        service.update(ownerId, itemId, request);

        assertThat(item.getTitle()).isEqualTo("Novo");
        assertThat(item.getType()).isEqualTo(VaultItemType.API_KEY);
        assertThat(item.getSecretEncrypted()).containsExactly(9, 9);
        assertThat(item.getUsernameEncrypted()).isNull();
        assertThat(item.getOwnerId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("exclusão repassa ID + proprietário; 0 linhas removidas vira 404")
    void deleteNotOwned() {
        when(repository.deleteByIdAndOwnerId(itemId, ownerId)).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(ownerId, itemId)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository).deleteByIdAndOwnerId(itemId, ownerId);
    }

    @Test
    @DisplayName("exclusão do próprio registro conclui sem erro")
    void deleteOwned() {
        when(repository.deleteByIdAndOwnerId(itemId, ownerId)).thenReturn(1L);

        service.delete(ownerId, itemId);

        verify(repository).deleteByIdAndOwnerId(itemId, ownerId);
    }
}
