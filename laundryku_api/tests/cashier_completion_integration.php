<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';

const BASE_URL = 'http://127.0.0.1/laundryku_api/api/';
const CUSTOMER_ID = 1;
const CASHIER_ID = 2;
const STAFF_ID = 3;
const OWNER_ID = 4;

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException(
            $message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual)
        );
    }
}

function request(string $method, string $path, ?array $payload = null): array
{
    $handle = curl_init(BASE_URL . $path);
    curl_setopt_array($handle, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
        CURLOPT_POSTFIELDS => $payload === null ? null : json_encode($payload, JSON_THROW_ON_ERROR),
    ]);
    $body = curl_exec($handle);
    if ($body === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($body, true, 512, JSON_THROW_ON_ERROR)];
}

function completionRequest(int $userId, int $transactionId): array
{
    return request('POST', 'complete_transaction.php', [
        'id_user' => $userId,
        'id_transaksi' => $transactionId,
    ]);
}

function concurrentCompletion(int $transactionId): array
{
    $multi = curl_multi_init();
    $handles = [];
    for ($index = 0; $index < 2; $index++) {
        $handle = curl_init(BASE_URL . 'complete_transaction.php');
        curl_setopt_array($handle, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_POST => true,
            CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
            CURLOPT_POSTFIELDS => json_encode([
                'id_user' => CASHIER_ID,
                'id_transaksi' => $transactionId,
            ], JSON_THROW_ON_ERROR),
        ]);
        curl_multi_add_handle($multi, $handle);
        $handles[] = $handle;
    }
    do {
        $result = curl_multi_exec($multi, $running);
        if ($running > 0) {
            curl_multi_select($multi, 1.0);
        }
    } while ($running > 0 && $result === CURLM_OK);

    $responses = [];
    foreach ($handles as $handle) {
        $responses[] = [
            'status' => curl_getinfo($handle, CURLINFO_RESPONSE_CODE),
            'body' => json_decode(curl_multi_getcontent($handle), true, 512, JSON_THROW_ON_ERROR),
        ];
        curl_multi_remove_handle($multi, $handle);
        curl_close($handle);
    }
    curl_multi_close($multi);
    return $responses;
}

function createFixture(
    PDO $pdo,
    string $suffix,
    string $laundryStatus,
    string $paymentStatus,
    ?string $completionDate = null
): int {
    $code = 'COMPTEST' . date('His') . $suffix . bin2hex(random_bytes(2));
    $statement = $pdo->prepare(
        'INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, tanggal_masuk, estimasi_selesai, tanggal_selesai,
          total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY), :completion,
                 10000, :laundry, :payment)'
    );
    $statement->execute([
        'code' => $code,
        'customer' => CUSTOMER_ID,
        'completion' => $completionDate,
        'laundry' => $laundryStatus,
        'payment' => $paymentStatus,
    ]);
    $transactionId = (int) $pdo->lastInsertId();

    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, 1, 1, 10000, 10000)'
    );
    $detail->execute(['transaction' => $transactionId]);

    if ($paymentStatus === 'sudah_dibayar') {
        $payment = $pdo->prepare(
            "INSERT INTO pembayaran
             (id_transaksi, metode, jumlah, status, tanggal_bayar)
             VALUES (:transaction, 'cash', 10000, 'berhasil', NOW())"
        );
        $payment->execute(['transaction' => $transactionId]);
    }

    $history = $pdo->prepare(
        'INSERT INTO riwayat_status (id_transaksi, id_user, status_laundry, waktu, catatan)
         VALUES (:transaction, :user, :status, NOW(), :note)'
    );
    $history->execute([
        'transaction' => $transactionId,
        'user' => $laundryStatus === 'selesai' ? CASHIER_ID : STAFF_ID,
        'status' => $laundryStatus,
        'note' => $laundryStatus === 'selesai'
            ? 'Laundry telah diserahkan kepada pelanggan'
            : 'Fixture cashier completion',
    ]);
    return $transactionId;
}

function transactionRow(PDO $pdo, int $transactionId): array
{
    $statement = $pdo->prepare(
        'SELECT status_laundry, status_pembayaran, tanggal_selesai, id_kasir
         FROM transaksi WHERE id_transaksi = :id'
    );
    $statement->execute(['id' => $transactionId]);
    return $statement->fetch();
}

function timeline(PDO $pdo, int $transactionId): array
{
    $statement = $pdo->prepare(
        'SELECT status_laundry, id_user, catatan
         FROM riwayat_status WHERE id_transaksi = :id ORDER BY id_riwayat'
    );
    $statement->execute(['id' => $transactionId]);
    return $statement->fetchAll();
}

function containsTransaction(array $items, int $transactionId): bool
{
    foreach ($items as $item) {
        if ((int) $item['id_transaksi'] === $transactionId) {
            return true;
        }
    }
    return false;
}

$pdo = getDatabaseConnection();
$fixtureIds = [];

try {
    $eligible = $fixtureIds[] = createFixture($pdo, 'OK', 'siap_diambil', 'sudah_dibayar');
    $result = completionRequest(CASHIER_ID, $eligible);
    assertSameValue(200, $result['status'], 'Eligible completion failed');
    assertSameValue('selesai', $result['body']['data']['status_laundry'], 'Backend did not select selesai');
    $completedRow = transactionRow($pdo, $eligible);
    assertSameValue('selesai', $completedRow['status_laundry'], 'Transaction status was not completed');
    assertSameValue(CASHIER_ID, (int) $completedRow['id_kasir'], 'Completing cashier was not stored');
    if ($completedRow['tanggal_selesai'] === null) {
        throw new RuntimeException('tanggal_selesai was not set');
    }
    $completedTimeline = timeline($pdo, $eligible);
    assertSameValue(2, count($completedTimeline), 'Completion timeline was not inserted once');
    assertSameValue('selesai', $completedTimeline[1]['status_laundry'], 'Timeline status is wrong');
    assertSameValue(CASHIER_ID, (int) $completedTimeline[1]['id_user'], 'Timeline cashier is wrong');
    assertSameValue(
        'Laundry telah diserahkan kepada pelanggan',
        $completedTimeline[1]['catatan'],
        'Timeline note is wrong'
    );

    $originalDate = $completedRow['tanggal_selesai'];
    assertSameValue(200, completionRequest(CASHIER_ID, $eligible)['status'], 'Idempotent request failed');
    assertSameValue($originalDate, transactionRow($pdo, $eligible)['tanggal_selesai'], 'Idempotent request changed date');
    assertSameValue(2, count(timeline($pdo, $eligible)), 'Idempotent request duplicated timeline');

    $unpaid = $fixtureIds[] = createFixture($pdo, 'UNPAID', 'siap_diambil', 'belum_dibayar');
    assertSameValue(409, completionRequest(CASHIER_ID, $unpaid)['status'], 'Unpaid ready transaction was completed');
    assertSameValue('siap_diambil', transactionRow($pdo, $unpaid)['status_laundry'], 'Unpaid status changed');
    assertSameValue(null, transactionRow($pdo, $unpaid)['tanggal_selesai'], 'Unpaid completion date changed');

    $packing = $fixtureIds[] = createFixture($pdo, 'PACK', 'dipacking', 'sudah_dibayar');
    assertSameValue(409, completionRequest(CASHIER_ID, $packing)['status'], 'Packing transaction was completed');
    assertSameValue('dipacking', transactionRow($pdo, $packing)['status_laundry'], 'Packing status changed');

    $existingDate = '2026-08-20 10:00:00';
    $alreadyCompleted = $fixtureIds[] = createFixture(
        $pdo,
        'DONE',
        'selesai',
        'sudah_dibayar',
        $existingDate
    );
    assertSameValue(200, completionRequest(CASHIER_ID, $alreadyCompleted)['status'], 'Already-completed request failed');
    assertSameValue($existingDate, transactionRow($pdo, $alreadyCompleted)['tanggal_selesai'], 'Existing date changed');
    assertSameValue(1, count(timeline($pdo, $alreadyCompleted)), 'Existing timeline was duplicated');

    $roleFixture = $fixtureIds[] = createFixture($pdo, 'ROLE', 'siap_diambil', 'sudah_dibayar');
    foreach ([CUSTOMER_ID, STAFF_ID, OWNER_ID] as $forbiddenUser) {
        assertSameValue(403, completionRequest($forbiddenUser, $roleFixture)['status'], "Role {$forbiddenUser} was allowed");
    }
    assertSameValue('siap_diambil', transactionRow($pdo, $roleFixture)['status_laundry'], 'Forbidden role changed status');

    $race = $fixtureIds[] = createFixture($pdo, 'RACE', 'siap_diambil', 'sudah_dibayar');
    foreach (concurrentCompletion($race) as $response) {
        assertSameValue(200, $response['status'], 'Concurrent idempotent completion failed');
    }
    assertSameValue('selesai', transactionRow($pdo, $race)['status_laundry'], 'Race did not complete transaction');
    assertSameValue(2, count(timeline($pdo, $race)), 'Race inserted duplicate completion timeline');

    $customerOrders = request('GET', 'customer_orders.php?id_user=' . CUSTOMER_ID);
    assertSameValue(false, containsTransaction($customerOrders['body']['data'], $eligible), 'Completed item remains active');
    $customerHistory = request('GET', 'customer_history.php?id_user=' . CUSTOMER_ID);
    assertSameValue(true, containsTransaction($customerHistory['body']['data'], $eligible), 'Completed item missing in history');
    $customerDetail = request(
        'GET',
        'customer_order_detail.php?id_user=' . CUSTOMER_ID . '&id_transaksi=' . $eligible
    );
    assertSameValue('selesai', $customerDetail['body']['data']['status_laundry'], 'Customer detail status is stale');
    assertSameValue('sudah_dibayar', $customerDetail['body']['data']['status_pembayaran'], 'Payment status is stale');
    assertSameValue($originalDate, $customerDetail['body']['data']['tanggal_selesai'], 'Customer completion date is stale');
    $customerTimeline = $customerDetail['body']['data']['timeline'];
    assertSameValue('selesai', end($customerTimeline)['status_laundry'], 'Customer timeline is stale');

    $staffHistory = request('GET', 'staff_history.php?id_user=' . STAFF_ID);
    assertSameValue(true, containsTransaction($staffHistory['body']['data'], $eligible), 'Staff History lost completed item');
    $cashierTransactions = request('GET', 'cashier_transactions.php?id_user=' . CASHIER_ID);
    assertSameValue(true, containsTransaction($cashierTransactions['body']['data'], $eligible), 'Cashier list did not reload completed data');

    echo "PASS: eligible paid-ready transaction completes atomically\n";
    echo "PASS: unpaid and non-ready transactions are rejected\n";
    echo "PASS: completed requests are idempotent\n";
    echo "PASS: Customer/Staff/Owner receive HTTP 403\n";
    echo "PASS: concurrent completion inserts one selesai timeline\n";
    echo "PASS: Customer Orders/History/Detail and Staff History are synchronized\n";
    echo "PASS: Cashier transaction data exposes the new selesai status\n";
} finally {
    if ($fixtureIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureIds), '?'));
        $delete = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
        $delete->execute($fixtureIds);
    }
}
