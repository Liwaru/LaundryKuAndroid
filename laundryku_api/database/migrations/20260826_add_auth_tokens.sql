CREATE TABLE IF NOT EXISTS auth_tokens (
    id_token BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    id_user INT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NULL,
    revoked_at DATETIME NULL,

    CONSTRAINT fk_auth_tokens_user
        FOREIGN KEY (id_user)
        REFERENCES users(id_user)
        ON DELETE CASCADE,

    INDEX idx_auth_tokens_user (id_user),
    INDEX idx_auth_tokens_expires (expires_at)
);
