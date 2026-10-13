package com.securevault.vault.repository;

import com.securevault.vault.entity.VaultItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repositório propositalmente restrito: estende o marcador {@link Repository} (e não JpaRepository),
 * de modo que NÃO existe findById/findAll/deleteById globais. Todo acesso a registros exige o ID do proprietário,
 * o que torna estruturalmente impossível "esquecer" a verificação de propriedade.
 */
public interface VaultItemRepository extends Repository<VaultItem, UUID> {

    VaultItem save(VaultItem item);

    List<VaultItem> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<VaultItem> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** @return quantidade de registros removidos (0 se não existir ou não pertencer ao proprietário) */
    long deleteByIdAndOwnerId(UUID id, UUID ownerId);
}
