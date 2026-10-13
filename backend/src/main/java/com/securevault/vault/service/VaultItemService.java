package com.securevault.vault.service;

import com.securevault.crypto.EncryptionService;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.vault.dto.VaultItemDetailResponse;
import com.securevault.vault.dto.VaultItemRequest;
import com.securevault.vault.dto.VaultItemSummaryResponse;
import com.securevault.vault.entity.VaultItem;
import com.securevault.vault.repository.VaultItemRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras do cofre. Toda operação recebe o {@code ownerId} do usuário AUTENTICADO e o repassa ao repositório,
 * que só oferece consultas filtradas por proprietário. Registro inexistente e registro de outro usuário
 * produzem a mesma {@link ResourceNotFoundException} (HTTP 404).
 */
@Service
public class VaultItemService {

    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_SECRET = "secret";

    private final VaultItemRepository repository;
    private final EncryptionService encryptionService;

    public VaultItemService(VaultItemRepository repository, EncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public VaultItemSummaryResponse create(UUID ownerId, VaultItemRequest request) {
        UUID id = UUID.randomUUID();
        VaultItem item = new VaultItem(
                id,
                ownerId,
                request.title().trim(),
                request.type(),
                encryptOptional(request.username(), ownerId, id, FIELD_USERNAME),
                encryptionService.encrypt(request.secret(), aad(ownerId, id, FIELD_SECRET)),
                blankToNull(request.url()),
                blankToNull(request.description()));
        return VaultItemSummaryResponse.from(repository.save(item));
    }

    /** A listagem não descriptografa nada: só metadados. */
    @Transactional(readOnly = true)
    public List<VaultItemSummaryResponse> list(UUID ownerId) {
        return repository.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(VaultItemSummaryResponse::from)
                .toList();
    }

    /** Único ponto em que os campos confidenciais são descriptografados, após verificar a propriedade. */
    @Transactional(readOnly = true)
    public VaultItemDetailResponse get(UUID ownerId, UUID itemId) {
        VaultItem item = findOwned(ownerId, itemId);
        String username = item.getUsernameEncrypted() == null
                ? null
                : encryptionService.decrypt(item.getUsernameEncrypted(), aad(ownerId, itemId, FIELD_USERNAME));
        String secret = encryptionService.decrypt(item.getSecretEncrypted(), aad(ownerId, itemId, FIELD_SECRET));
        return new VaultItemDetailResponse(item.getId(), item.getTitle(), item.getType(), username, secret,
                item.getUrl(), item.getDescription(), item.getCreatedAt(), item.getUpdatedAt());
    }

    @Transactional
    public VaultItemSummaryResponse update(UUID ownerId, UUID itemId, VaultItemRequest request) {
        VaultItem item = findOwned(ownerId, itemId);
        item.update(
                request.title().trim(),
                request.type(),
                encryptOptional(request.username(), ownerId, itemId, FIELD_USERNAME),
                encryptionService.encrypt(request.secret(), aad(ownerId, itemId, FIELD_SECRET)),
                blankToNull(request.url()),
                blankToNull(request.description()));
        return VaultItemSummaryResponse.from(repository.save(item));
    }

    @Transactional
    public void delete(UUID ownerId, UUID itemId) {
        if (repository.deleteByIdAndOwnerId(itemId, ownerId) == 0) {
            throw new ResourceNotFoundException();
        }
    }

    private VaultItem findOwned(UUID ownerId, UUID itemId) {
        return repository.findByIdAndOwnerId(itemId, ownerId).orElseThrow(ResourceNotFoundException::new);
    }

    private byte[] encryptOptional(String value, UUID ownerId, UUID itemId, String field) {
        String normalized = blankToNull(value);
        return normalized == null ? null : encryptionService.encrypt(normalized, aad(ownerId, itemId, field));
    }

    /** Vincula cada texto cifrado ao proprietário, ao registro e ao campo (impede troca/reaproveitamento). */
    static byte[] aad(UUID ownerId, UUID itemId, String field) {
        return (ownerId + ":" + itemId + ":" + field).getBytes(StandardCharsets.UTF_8);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
