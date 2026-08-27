<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/auth_test_helper.php';

const EW_CUSTOMER_ID = 1;
const EW_CASHIER_ID = 2;
const EW_STAFF_ID = 3;
const EW_OWNER_ID = 4;

function ewAssert(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual));
    }
}

function ewRequest(string $method, string $path, ?array $body = null, ?int $userId = null): array
{
    $baseUrl = rtrim((string) (getenv('EWALLET_TEST_BASE_URL') ?: 'http://127.0.0.1:8092/api'), '/') . '/';
    $headers = ['Content-Type: application/json'];
    if ($userId !== null) {
        $headers = array_merge($headers, testAuthHeaders($userId));
    }
    $handle = curl_init($baseUrl . $path);
    curl_setopt_array($handle, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_TIMEOUT => 10,
    ]);
    if ($body !== null) {
        curl_setopt($handle, CURLOPT_POSTFIELDS, json_encode($body, JSON_THROW_ON_ERROR));
    }
    $response = curl_exec($handle);
    if ($response === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($response, true, 512, JSON_THROW_ON_ERROR)];
}

function ewCustomer(PDO $pdo): int
{
    $statement = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         VALUES ('EWallet Fixture', :phone, :username, :password, 1, 'aktif')"
    );
    $statement->execute([
        'phone' => '085' . random_int(100000000, 999999999),
        'username' => 'ewallet' . random_int(10000, 99999),
        'password' => password_hash(bin2hex(random_bytes(8)), PASSWORD_DEFAULT),
    ]);
    return (int) $pdo->lastInsertId();
}

function ewTransaction(PDO $pdo, int $customerId, string $laundry = 'menunggu', string $payment = 'belum_dibayar', float $amount = 45000): int
{
    $statement = $pdo->prepare(
        'INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, estimasi_selesai, total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, NOW() + INTERVAL 2 DAY, :amount, :laundry, :payment)'
    );
    $statement->execute([
        'code' => 'EWSIM' . bin2hex(random_bytes(3)),
        'customer' => $customerId,
        'amount' => $amount,
        'laundry' => $laundry,
        'payment' => $payment,
    ]);
    $id = (int) $pdo->lastInsertId();
    $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, 2, 4.5, 10000, :amount)'
    )->execute(['transaction' => $id, 'amount' => $amount]);
    return $id;
}

function ewPayment(PDO $pdo, int $transactionId, string $method, string $status, float $amount, ?string $orderId = null): void
{
    $pdo->prepare(
        'INSERT INTO pembayaran
         (id_transaksi, metode, payment_channel, jumlah, status, gateway_order_id,
          gateway_transaction_id, gateway_qr_url, gateway_expiry_time, tanggal_bayar)
         VALUES (:transaction, :method, :channel, :amount, :status, :order_id,
                 :gateway_transaction, :qr_url, :expiry, :paid_at)'
    )->execute([
        'transaction' => $transactionId,
        'method' => $method,
        'channel' => $method === 'qris' ? 'qris' : null,
        'amount' => $amount,
        'status' => $status,
        'order_id' => $orderId,
        'gateway_transaction' => $orderId === null ? null : 'old-qris-transaction',
        'qr_url' => $orderId === null ? null : 'https://api.sandbox.midtrans.com/v2/qris/old/qr-code',
        'expiry' => $orderId === null ? null : '2099-01-01 00:00:00',
        'paid_at' => $status === 'berhasil' ? date('Y-m-d H:i:s') : null,
    ]);
}

function ewNotification(string $orderId, string $amount, string $transactionId): array
{
    $statusCode = '200';
    $key = (string) getenv('LAUNDRYKU_MIDTRANS_SERVER_KEY');
    return [
        'transaction_status' => 'settlement',
        'transaction_id' => $transactionId,
        'status_code' => $statusCode,
        'signature_key' => hash('sha512', $orderId . $statusCode . $amount . $key),
        'payment_type' => 'qris',
        'order_id' => $orderId,
        'gross_amount' => $amount,
        'fraud_status' => 'accept',
    ];
}

function ewDeleteTransactions(PDO $pdo, array $ids): void
{
    if ($ids === []) return;
    $placeholders = implode(',', array_fill(0, count($ids), '?'));
    foreach (['riwayat_status', 'pembayaran', 'detail_transaksi'] as $table) {
        $pdo->prepare("DELETE FROM {$table} WHERE id_transaksi IN ({$placeholders})")->execute($ids);
    }
    $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})")->execute($ids);
}

$pdo = getDatabaseConnection();
$transactions = [];
$otherCustomer = null;

try {
    $otherCustomer = ewCustomer($pdo);
    $securityFixture = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID);
    $otherFixture = $transactions[] = ewTransaction($pdo, $otherCustomer);
    $cancelledFixture = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID, 'dibatalkan');
    $paidFixture = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID, 'menunggu', 'sudah_dibayar');
    ewPayment($pdo, $paidFixture, 'cash', 'berhasil', 45000);

    $payload = ['id_transaksi' => $securityFixture, 'payment_channel' => 'gopay'];
    ewAssert(401, ewRequest('POST', 'simulate_ewallet_payment.php', $payload)['status'], 'Missing token was accepted');
    foreach ([EW_CASHIER_ID, EW_STAFF_ID, EW_OWNER_ID] as $roleUser) {
        ewAssert(403, ewRequest('POST', 'simulate_ewallet_payment.php', $payload, $roleUser)['status'], 'Non-customer role was accepted');
    }
    ewAssert(404, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $otherFixture, 'payment_channel' => 'gopay'], EW_CUSTOMER_ID)['status'], 'Another customer transaction was accepted');
    ewAssert(400, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $securityFixture, 'payment_channel' => 'linkaja'], EW_CUSTOMER_ID)['status'], 'Unknown channel was accepted');
    ewAssert(400, ewRequest('POST', 'simulate_ewallet_payment.php', $payload + ['jumlah' => 1], EW_CUSTOMER_ID)['status'], 'Client amount was accepted');
    ewAssert(409, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $cancelledFixture, 'payment_channel' => 'gopay'], EW_CUSTOMER_ID)['status'], 'Cancelled transaction was accepted');
    ewAssert(409, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $paidFixture, 'payment_channel' => 'gopay'], EW_CUSTOMER_ID)['status'], 'Paid transaction was accepted');

    foreach (['gopay', 'dana', 'ovo', 'shopeepay'] as $channel) {
        $id = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID, 'siap_diambil', 'belum_dibayar', 45000);
        $laundryBefore = $pdo->query("SELECT status_laundry FROM transaksi WHERE id_transaksi={$id}")->fetchColumn();
        $result = ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $id, 'payment_channel' => $channel], EW_CUSTOMER_ID);
        ewAssert(200, $result['status'], "{$channel} simulation failed");
        ewAssert(45000.0, (float) $result['body']['data']['jumlah'], "{$channel} amount did not come from database");
        $row = $pdo->query(
            "SELECT p.metode, p.payment_channel, p.jumlah, p.status, p.tanggal_bayar,
                    p.gateway_order_id, p.gateway_transaction_id, p.gateway_qr_url, p.gateway_expiry_time,
                    t.status_pembayaran, t.status_laundry
             FROM pembayaran p JOIN transaksi t ON t.id_transaksi=p.id_transaksi
             WHERE p.id_transaksi={$id}"
        )->fetch();
        ewAssert('e_wallet', $row['metode'], "{$channel} method mismatch");
        ewAssert($channel, $row['payment_channel'], "{$channel} channel mismatch");
        ewAssert('berhasil', $row['status'], "{$channel} payment was not successful");
        ewAssert('sudah_dibayar', $row['status_pembayaran'], "{$channel} transaction was not paid");
        ewAssert($laundryBefore, $row['status_laundry'], "{$channel} changed laundry workflow");
        ewAssert(false, $row['tanggal_bayar'] === null, "{$channel} payment date missing");
        foreach (['gateway_order_id', 'gateway_transaction_id', 'gateway_qr_url', 'gateway_expiry_time'] as $gatewayField) {
            ewAssert(null, $row[$gatewayField], "{$channel} created fake gateway data");
        }
    }

    $cashSwitch = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID);
    ewPayment($pdo, $cashSwitch, 'cash', 'menunggu', 45000);
    ewAssert(200, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $cashSwitch, 'payment_channel' => 'dana'], EW_CUSTOMER_ID)['status'], 'Pending Cash did not switch');
    ewAssert(1, (int) $pdo->query("SELECT COUNT(*) FROM pembayaran WHERE id_transaksi={$cashSwitch}")->fetchColumn(), 'Cash switch duplicated payment');

    $idempotent = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID);
    $idempotentPayload = ['id_transaksi' => $idempotent, 'payment_channel' => 'ovo'];
    ewAssert(200, ewRequest('POST', 'simulate_ewallet_payment.php', $idempotentPayload, EW_CUSTOMER_ID)['status'], 'First idempotent request failed');
    $paidAt = $pdo->query("SELECT tanggal_bayar FROM pembayaran WHERE id_transaksi={$idempotent}")->fetchColumn();
    ewAssert(200, ewRequest('POST', 'simulate_ewallet_payment.php', $idempotentPayload, EW_CUSTOMER_ID)['status'], 'Duplicate request was not idempotent');
    ewAssert(1, (int) $pdo->query("SELECT COUNT(*) FROM pembayaran WHERE id_transaksi={$idempotent}")->fetchColumn(), 'Duplicate request created payment');
    ewAssert($paidAt, $pdo->query("SELECT tanggal_bayar FROM pembayaran WHERE id_transaksi={$idempotent}")->fetchColumn(), 'Duplicate request changed settlement date');
    ewAssert(409, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $idempotent, 'payment_channel' => 'gopay'], EW_CUSTOMER_ID)['status'], 'Paid transaction switched channel');

    $stale = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID);
    $oldOrderId = 'EW-STALE-' . bin2hex(random_bytes(5));
    $oldTransactionId = 'mock-' . bin2hex(random_bytes(8));
    ewPayment($pdo, $stale, 'qris', 'menunggu', 45000, $oldOrderId);
    ewAssert(200, ewRequest('POST', 'simulate_ewallet_payment.php', ['id_transaksi' => $stale, 'payment_channel' => 'gopay'], EW_CUSTOMER_ID)['status'], 'Pending QRIS did not switch');
    $stateFile = (string) getenv('LAUNDRYKU_MIDTRANS_TEST_STATE_FILE');
    file_put_contents($stateFile, json_encode([
        $oldOrderId => ['status' => 'settlement', 'amount' => '45000.00', 'transaction_id' => $oldTransactionId],
    ], JSON_THROW_ON_ERROR), LOCK_EX);
    ewAssert(404, ewRequest('POST', 'midtrans_notification.php', ewNotification($oldOrderId, '45000.00', $oldTransactionId))['status'], 'Stale QRIS webhook was accepted');
    $staleRow = $pdo->query("SELECT metode, payment_channel, jumlah, status, gateway_order_id FROM pembayaran WHERE id_transaksi={$stale}")->fetch();
    ewAssert('e_wallet', $staleRow['metode'], 'Stale webhook overwrote method');
    ewAssert('gopay', $staleRow['payment_channel'], 'Stale webhook overwrote channel');
    ewAssert('berhasil', $staleRow['status'], 'Stale webhook changed payment status');
    ewAssert(null, $staleRow['gateway_order_id'], 'QRIS gateway order was not cleared');

    $cashier = ewRequest('GET', 'cashier_transactions.php', null, EW_CASHIER_ID);
    ewAssert(200, $cashier['status'], 'Cashier transactions failed');
    $cashierRows = array_values(array_filter($cashier['body']['data'], static fn(array $row): bool => (int) $row['id_transaksi'] === $stale));
    ewAssert(1, count($cashierRows), 'E-Wallet transaction missing for Cashier');
    ewAssert('e_wallet', $cashierRows[0]['metode_pembayaran'], 'Cashier method mismatch');
    ewAssert('sudah_dibayar', $cashierRows[0]['status_pembayaran'], 'Cashier payment status mismatch');

    $owner = ewRequest('GET', 'owner_reports.php?period=today', null, EW_OWNER_ID);
    ewAssert(200, $owner['status'], 'Owner report failed');
    $ownerRows = array_values(array_filter($owner['body']['data']['financial_report'], static fn(array $row): bool => (int) $row['id_transaksi'] === $stale));
    ewAssert(1, count($ownerRows), 'E-Wallet payment missing from Owner report');
    ewAssert('e_wallet', $ownerRows[0]['metode'], 'Owner report method mismatch');
    ewAssert('gopay', $ownerRows[0]['payment_channel'], 'Owner report channel mismatch');

    $cashRegression = $transactions[] = ewTransaction($pdo, EW_CUSTOMER_ID);
    ewAssert(200, ewRequest('POST', 'select_cash_payment.php', ['id_transaksi' => $cashRegression], EW_CUSTOMER_ID)['status'], 'Cash selection regression failed');
    $cashPending = $pdo->query("SELECT metode, status FROM pembayaran WHERE id_transaksi={$cashRegression}")->fetch();
    ewAssert('cash', $cashPending['metode'], 'Cash method regression mismatch');
    ewAssert('menunggu', $cashPending['status'], 'Cash did not remain pending before Cashier confirmation');
    ewAssert(200, ewRequest('POST', 'confirm_cash_payment.php', ['id_transaksi' => $cashRegression], EW_CASHIER_ID)['status'], 'Cashier confirmation regression failed');
    ewAssert('sudah_dibayar', $pdo->query("SELECT status_pembayaran FROM transaksi WHERE id_transaksi={$cashRegression}")->fetchColumn(), 'Cashier confirmation did not mark Cash paid');

    echo "PASS: E-Wallet Simulation security, ownership, whitelist and database amount\n";
    echo "PASS: GoPay, DANA, OVO and ShopeePay settle atomically without gateway IDs\n";
    echo "PASS: pending Cash/QRIS switching, idempotency and stale QRIS webhook protection\n";
    echo "PASS: Cashier and Owner receive successful E-Wallet payment data\n";
    echo "PASS: Cash selection remains pending until Cashier confirmation\n";
} finally {
    ewDeleteTransactions($pdo, $transactions);
    if ($otherCustomer !== null) {
        $pdo->prepare('DELETE FROM users WHERE id_user = :id')->execute(['id' => $otherCustomer]);
    }
}
