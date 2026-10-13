CREATE TABLE vault_items (
    id                 UUID PRIMARY KEY,
    owner_id           UUID NOT NULL,
    title              VARCHAR(150) NOT NULL,
    type               VARCHAR(30) NOT NULL,

    -- Campos confidenciais criptografados com AES-GCM
    -- Formato: versão (1 byte) | nonce (12 bytes) | texto cifrado + tag GCM
    username_encrypted VARBINARY(4096),
    secret_encrypted   VARBINARY(4096) NOT NULL,
    url                VARCHAR(2048),
    description        VARCHAR(1000),

    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vault_items_owner
        FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT ck_vault_items_type
        CHECK (type IN ('CREDENTIAL', 'API_KEY', 'SECURE_NOTE', 'OTHER'))
);

-- Índice para consultas por proprietário e data de atualização
CREATE INDEX idx_vault_items_owner_updated
    ON vault_items (owner_id, updated_at DESC);
