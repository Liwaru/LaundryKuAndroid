<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/auth_test_helper.php';

const CUSTOMER_ID = 1;
const CASHIER_ID = 2;
const OWNER_ID = 4;

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual));
    }
}

function requestApi(string $method, string $path, ?array $body = null, ?int $userId = null): array
{
    $baseUrl = rtrim((string) (getenv('QRIS_TEST_BASE_URL') ?: 'http://127.0.0.1:8091/api'), '/') . '/';
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

function createFixtureCustomer(PDO $pdo): int
{
    $statement = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         VALUES ('QRIS B', :phone, :username, :password, 1, 'aktif')"
    );
    $statement->execute([
        'phone' => '084' . random_int(100000000, 999999999),
        'username' => 'qrisb' . random_int(1000, 9999),
        'password' => password_hash(bin2hex(random_bytes(8)), PASSWORD_DEFAULT),
    ]);
    return (int) $pdo->lastInsertId();
}

function createFixtureTransaction(PDO $pdo, int $customerId, string $laundryStatus = 'menunggu', string $paymentStatus = 'belum_dibayar', float $amount = 45000): int
{
    $statement = $pdo->prepare(
        'INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, estimasi_selesai, total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, NOW() + INTERVAL 2 DAY, :amount, :laundry, :payment)'
    );
    $statement->execute([
        'code' => 'QRIS' . bin2hex(random_bytes(3)),
        'customer' => $customerId,
        'amount' => $amount,
        'laundry' => $laundryStatus,
        'payment' => $paymentStatus,
    ]);
    $transactionId = (int) $pdo->lastInsertId();
    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, 2, 4.5, 10000, :amount)'
    );
    $detail->execute(['transaction' => $transactionId, 'amount' => $amount]);
    return $transactionId;
}

function insertPayment(PDO $pdo, int $transactionId, string $method, string $status, float $amount): void
{
    $statement = $pdo->prepare(
        'INSERT INTO pembayaran (id_transaksi, metode, payment_channel, jumlah, status, tanggal_bayar)
         VALUES (:transaction, :method, :channel, :amount, :status, :paid_at)'
    );
    $statement->execute([
        'transaction' => $transactionId,
        'method' => $method,
        'channel' => $method === 'qris' ? 'qris' : null,
        'amount' => $amount,
        'status' => $status,
        'paid_at' => $status === 'berhasil' ? date('Y-m-d H:i:s') : null,
    ]);
}

function mockState(string $orderId, string $status, ?string $amount = null): array
{
    $file = (string) getenv('LAUNDRYKU_MIDTRANS_TEST_STATE_FILE');
    $state = json_decode((string) file_get_contents($file), true, 512, JSON_THROW_ON_ERROR);
    if (!isset($state[$orderId])) {
        throw new RuntimeException('Mock order is missing');
    }
    $state[$orderId]['status'] = $status;
    if ($amount !== null) {
        $state[$orderId]['amount'] = $amount;
    }
    file_put_contents($file, json_encode($state, JSON_THROW_ON_ERROR), LOCK_EX);
    return $state[$orderId];
}

function notification(string $orderId, string $amount, string $status, string $transactionId, bool $validSignature = true): array
{
    $key = (string) getenv('LAUNDRYKU_MIDTRANS_SERVER_KEY');
    if ($key === '') {
        throw new RuntimeException('Test Midtrans key environment is required');
    }
    $statusCode = '200';
    $signature = hash('sha512', $orderId . $statusCode . $amount . $key);
    return [
        'transaction_status' => $status,
        'transaction_id' => $transactionId,
        'status_code' => $statusCode,
        'signature_key' => $validSignature ? $signature : str_repeat('0', 128),
        'payment_type' => 'qris',
        'order_id' => $orderId,
        'gross_amount' => $amount,
        'fraud_status' => 'accept',
    ];
}

function deleteTransactions(PDO $pdo, array $ids): void
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
$customerB = null;

try {
    $customerB = createFixtureCustomer($pdo);
    $owned = $transactions[] = createFixtureTransaction($pdo, CUSTOMER_ID);
    insertPayment($pdo, $owned, 'cash', 'menunggu', 45000);
    $other = $transactions[] = createFixtureTransaction($pdo, $customerB);
    $cancelled = $transactions[] = createFixtureTransaction($pdo, CUSTOMER_ID, 'dibatalkan');
    $paid = $transactions[] = createFixtureTransaction($pdo, CUSTOMER_ID, 'menunggu', 'sudah_dibayar');
    insertPayment($pdo, $paid, 'cash', 'berhasil', 45000);

    assertSameValue(401, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $owned])['status'], 'Missing token was accepted');
    assertSameValue(403, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $owned], CASHIER_ID)['status'], 'Wrong role was accepted');
    assertSameValue(404, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $other], CUSTOMER_ID)['status'], 'Another customer transaction was accepted');
    assertSameValue(409, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $cancelled], CUSTOMER_ID)['status'], 'Cancelled transaction was accepted');
    assertSameValue(409, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $paid], CUSTOMER_ID)['status'], 'Paid transaction was accepted');
    assertSameValue(400, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $owned, 'jumlah' => 1], CUSTOMER_ID)['status'], 'Android amount was accepted');

    $created = requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $owned], CUSTOMER_ID);
    assertSameValue(201, $created['status'], 'QRIS was not created');
    $orderId = (string) $created['body']['data']['gateway_order_id'];
    assertSameValue(45000.0, (float) $created['body']['data']['total'], 'QRIS amount did not come from database');
    $payment = $pdo->query("SELECT metode, payment_channel, jumlah, status, gateway_order_id FROM pembayaran WHERE id_transaksi={$owned}")->fetch();
    assertSameValue('qris', $payment['metode'], 'Cash pending did not switch to QRIS');
    assertSameValue('menunggu', $payment['status'], 'QRIS creation marked payment successful');
    assertSameValue($orderId, $payment['gateway_order_id'], 'Gateway order was not stored');

    $samePending = requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $owned], CUSTOMER_ID);
    assertSameValue(200, $samePending['status'], 'Pending QRIS was duplicated');
    assertSameValue($orderId, $samePending['body']['data']['gateway_order_id'], 'Pending QRIS order changed');

    $pendingEntry = mockState($orderId, 'pending');
    assertSameValue(401, requestApi('POST', 'midtrans_notification.php', notification($orderId, '45000.00', 'pending', $pendingEntry['transaction_id'], false))['status'], 'Invalid signature was accepted');
    assertSameValue(200, requestApi('POST', 'midtrans_notification.php', notification($orderId, '45000.00', 'pending', $pendingEntry['transaction_id']))['status'], 'Valid pending notification failed');
    assertSameValue('belum_dibayar', $pdo->query("SELECT status_pembayaran FROM transaksi WHERE id_transaksi={$owned}")->fetchColumn(), 'Pending notification marked transaction paid');

    $mismatch = $transactions[] = createFixtureTransaction($pdo, CUSTOMER_ID, 'menunggu', 'belum_dibayar', 50000);
    $mismatchCreated = requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $mismatch], CUSTOMER_ID);
    $mismatchOrder = (string) $mismatchCreated['body']['data']['gateway_order_id'];
    $mismatchEntry = mockState($mismatchOrder, 'settlement', '1.00');
    assertSameValue(409, requestApi('POST', 'midtrans_notification.php', notification($mismatchOrder, '1.00', 'settlement', $mismatchEntry['transaction_id']))['status'], 'Amount mismatch was accepted');
    assertSameValue('belum_dibayar', $pdo->query("SELECT status_pembayaran FROM transaksi WHERE id_transaksi={$mismatch}")->fetchColumn(), 'Amount mismatch marked transaction paid');

    $successEntry = mockState($orderId, 'settlement');
    $successNotification = notification($orderId, '45000.00', 'settlement', $successEntry['transaction_id']);
    assertSameValue(200, requestApi('POST', 'midtrans_notification.php', $successNotification)['status'], 'Settlement notification failed');
    $paidAt = $pdo->query("SELECT tanggal_bayar FROM pembayaran WHERE id_transaksi={$owned}")->fetchColumn();
    assertSameValue('sudah_dibayar', $pdo->query("SELECT status_pembayaran FROM transaksi WHERE id_transaksi={$owned}")->fetchColumn(), 'Settlement did not mark transaction paid');
    $ownerReport = requestApi('GET', 'owner_reports.php?period=today', null, OWNER_ID);
    assertSameValue(200, $ownerReport['status'], 'Owner report failed after QRIS settlement');
    $qrisReportRows = array_values(array_filter(
        $ownerReport['body']['data']['financial_report'],
        static fn(array $row): bool => (int) $row['id_transaksi'] === $owned
    ));
    assertSameValue(1, count($qrisReportRows), 'QRIS settlement was missing from Owner financial report');
    assertSameValue('qris', $qrisReportRows[0]['metode'], 'Owner report did not display QRIS method');
    assertSameValue(200, requestApi('POST', 'midtrans_notification.php', $successNotification)['status'], 'Duplicate webhook was not idempotent');
    assertSameValue($paidAt, $pdo->query("SELECT tanggal_bayar FROM pembayaran WHERE id_transaksi={$owned}")->fetchColumn(), 'Duplicate webhook changed payment date');
    assertSameValue(409, requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $owned], CUSTOMER_ID)['status'], 'Paid QRIS was recreated');

    $expired = $transactions[] = createFixtureTransaction($pdo, CUSTOMER_ID);
    $expiredCreated = requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $expired], CUSTOMER_ID);
    $expiredOrder = (string) $expiredCreated['body']['data']['gateway_order_id'];
    $expiredEntry = mockState($expiredOrder, 'expire');
    assertSameValue(200, requestApi('POST', 'midtrans_notification.php', notification($expiredOrder, '45000.00', 'expire', $expiredEntry['transaction_id']))['status'], 'Expired notification failed');
    assertSameValue('gagal', $pdo->query("SELECT status FROM pembayaran WHERE id_transaksi={$expired}")->fetchColumn(), 'Expired payment was not marked failed');
    assertSameValue('belum_dibayar', $pdo->query("SELECT status_pembayaran FROM transaksi WHERE id_transaksi={$expired}")->fetchColumn(), 'Expired payment marked transaction paid');
    $retried = requestApi('POST', 'create_qris_payment.php', ['id_transaksi' => $expired], CUSTOMER_ID);
    assertSameValue(201, $retried['status'], 'Failed QRIS could not be retried');
    assertSameValue(false, $expiredOrder === $retried['body']['data']['gateway_order_id'], 'QRIS retry reused gateway order ID');

    assertSameValue(403, requestApi('GET', "payment_status.php?id_transaksi={$owned}", null, OWNER_ID)['status'], 'Wrong role read payment status');
    assertSameValue(404, requestApi('GET', "payment_status.php?id_transaksi={$other}", null, CUSTOMER_ID)['status'], 'Customer read another payment status');

    echo "PASS: Bearer auth, ownership, cancelled and already-paid QRIS validation\n";
    echo "PASS: amount comes from database and pending Cash safely switches to QRIS\n";
    echo "PASS: invalid signature and amount mismatch never mark a transaction paid\n";
    echo "PASS: pending, settlement, duplicate webhook and atomic paid state are correct\n";
    echo "PASS: expired QRIS becomes failed and retry creates a new gateway order ID\n";
} finally {
    deleteTransactions($pdo, $transactions);
    if ($customerB !== null) {
        $pdo->prepare('DELETE FROM users WHERE id_user = :id')->execute(['id' => $customerB]);
    }
}
