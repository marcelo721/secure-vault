package com.securevault.vault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Registro do cofre. Os campos confidenciais ({@code username} e {@code secret}) são mantidos
 * apenas na forma cifrada; esta entidade nunca vê o texto puro.
 *
 * <p>O ID é gerado pela aplicação antes da persistência porque faz parte dos dados autenticados (AAD)
 * da criptografia. Por isso a entidade implementa {@link Persistable}.
 */
@Entity
@Table(name = "vault_items")
public class VaultItem implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VaultItemType type;

    @Column(name = "username_encrypted")
    private byte[] usernameEncrypted;

    @Column(name = "secret_encrypted", nullable = false)
    private byte[] secretEncrypted;

    @Column(length = 2048)
    private String url;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean newEntity = true;

    protected VaultItem() {
    }

    public VaultItem(UUID id, UUID ownerId, String title, VaultItemType type,
                     byte[] usernameEncrypted, byte[] secretEncrypted, String url, String description) {
        this.id = id;
        this.ownerId = ownerId;
        this.title = title;
        this.type = type;
        this.usernameEncrypted = usernameEncrypted;
        this.secretEncrypted = secretEncrypted;
        this.url = url;
        this.description = description;
    }

    /** Atualiza os campos editáveis. O proprietário e o ID nunca mudam. */
    public void update(String title, VaultItemType type, byte[] usernameEncrypted, byte[] secretEncrypted,
                       String url, String description) {
        this.title = title;
        this.type = type;
        this.usernameEncrypted = usernameEncrypted;
        this.secretEncrypted = secretEncrypted;
        this.url = url;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public VaultItemType getType() {
        return type;
    }

    public byte[] getUsernameEncrypted() {
        return usernameEncrypted;
    }

    public byte[] getSecretEncrypted() {
        return secretEncrypted;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "VaultItem[id=" + id + "]";
    }
}
