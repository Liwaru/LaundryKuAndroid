ALTER TABLE pembayaran
    MODIFY COLUMN metode ENUM('cash', 'qris', 'e_wallet', 'paylater') NOT NULL,
    ADD COLUMN IF NOT EXISTS payment_channel VARCHAR(30) NULL AFTER metode,
    ADD COLUMN IF NOT EXISTS gateway_order_id VARCHAR(100) NULL AFTER status,
    ADD COLUMN IF NOT EXISTS gateway_transaction_id VARCHAR(100) NULL AFTER gateway_order_id;
