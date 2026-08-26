ALTER TABLE pembayaran
    ADD COLUMN IF NOT EXISTS gateway_qr_url VARCHAR(500) NULL AFTER gateway_transaction_id,
    ADD COLUMN IF NOT EXISTS gateway_expiry_time DATETIME NULL AFTER gateway_qr_url;

SET @gateway_order_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'pembayaran'
      AND index_name = 'uq_pembayaran_gateway_order'
);
SET @gateway_order_index_sql = IF(
    @gateway_order_index_exists = 0,
    'ALTER TABLE pembayaran ADD UNIQUE INDEX uq_pembayaran_gateway_order (gateway_order_id)',
    'SELECT 1'
);
PREPARE gateway_order_index_statement FROM @gateway_order_index_sql;
EXECUTE gateway_order_index_statement;
DEALLOCATE PREPARE gateway_order_index_statement;
