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

function concurrentUpdates(array $payload): array
{
    $multi = curl_multi_init();
    $handles = [];
    for ($index = 0; $index < 2; $index++) {
        $handle = curl_init(BASE_URL . 'update_laundry_status.php');
        curl_setopt_array($handle, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_POST => true,
            CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
            CURLOPT_POSTFIELDS => json_encode($payload, JSON_THROW_ON_ERROR),
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

function createFixture(PDO $pdo, int $serviceId, string $suffix, string $status = 'menunggu'): int
{
    $code = 'WFTEST' . date('His') . $suffix . bin2hex(random_bytes(2));
    $statement = $pdo->prepare(
        "INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, tanggal_masuk, estimasi_selesai, total_harga,
          status_laundry, status_pembayaran)
         VALUES (:code, :customer, NOW(), DATE_ADD(NOW(), INTERVAL 3 DAY), 10000, :status, 'belum_dibayar')"
    );
    $statement->execute(['code' => $code, 'customer' => CUSTOMER_ID, 'status' => $status]);
    $transactionId = (int) $pdo->lastInsertId();

    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, :service, 1, 10000, 10000)'
    );
    $detail->execute(['transaction' => $transactionId, 'service' => $serviceId]);

    $history = $pdo->prepare(
        'INSERT INTO riwayat_status (id_transaksi, id_user, status_laundry, waktu, catatan)
         VALUES (:transaction, :user, :status, NOW(), :note)'
    );
    $history->execute([
        'transaction' => $transactionId,
        'user' => CUSTOMER_ID,
        'status' => $status,
        'note' => 'Fixture workflow integration',
    ]);
    return $transactionId;
}

function updateStatus(int $transactionId, int $userId, string $expectedStatus): array
{
    return request('POST', 'update_laundry_status.php', [
        'id_user' => $userId,
        'id_transaksi' => $transactionId,
        'current_status' => $expectedStatus,
    ]);
}

function assertWorkflow(PDO $pdo, int $transactionId, array $expectedTimeline): void
{
    foreach (array_slice($expectedTimeline, 0, -1) as $index => $currentStatus) {
        $expectedNext = $expectedTimeline[$index + 1];
        $response = updateStatus($transactionId, STAFF_ID, $currentStatus);
        assertSameValue(200, $response['status'], "Update {$currentStatus} failed");
        assertSameValue($expectedNext, $response['body']['data']['status_laundry'], 'Backend selected wrong next status');
    }

    $status = $pdo->query("SELECT status_laundry FROM transaksi WHERE id_transaksi = {$transactionId}")->fetchColumn();
    assertSameValue(end($expectedTimeline), $status, 'Final transaction status is wrong');
    $history = $pdo->query(
        "SELECT status_laundry FROM riwayat_status WHERE id_transaksi = {$transactionId} ORDER BY id_riwayat"
    )->fetchAll(PDO::FETCH_COLUMN);
    assertSameValue($expectedTimeline, $history, 'Customer timeline does not match workflow');
}

$pdo = getDatabaseConnection();
$fixtureIds = [];

try {
    foreach (['staff_jobs.php', 'staff_history.php', 'staff_dashboard.php'] as $endpoint) {
        assertSameValue(200, request('GET', $endpoint . '?id_user=' . STAFF_ID)['status'], "{$endpoint} is not active");
    }

    $roleFixture = $fixtureIds[] = createFixture($pdo, 1, 'ROLE');
    foreach ([CUSTOMER_ID, CASHIER_ID, OWNER_ID] as $forbiddenUser) {
        assertSameValue(403, updateStatus($roleFixture, $forbiddenUser, 'menunggu')['status'], "Role {$forbiddenUser} was allowed");
    }
    assertSameValue(
        'menunggu',
        $pdo->query("SELECT status_laundry FROM transaksi WHERE id_transaksi = {$roleFixture}")->fetchColumn(),
        'Forbidden role changed transaction status'
    );

    $doubleTapFixture = $fixtureIds[] = createFixture($pdo, 1, 'RACE');
    $responses = concurrentUpdates([
        'id_user' => STAFF_ID,
        'id_transaksi' => $doubleTapFixture,
        'current_status' => 'menunggu',
    ]);
    $statuses = array_column($responses, 'status');
    sort($statuses);
    assertSameValue([200, 409], $statuses, 'Concurrent double tap was not rejected exactly once');
    assertSameValue(
        'dicuci',
        $pdo->query("SELECT status_laundry FROM transaksi WHERE id_transaksi = {$doubleTapFixture}")->fetchColumn(),
        'Double tap skipped more than one status'
    );
    assertSameValue(
        2,
        (int) $pdo->query("SELECT COUNT(*) FROM riwayat_status WHERE id_transaksi = {$doubleTapFixture}")->fetchColumn(),
        'Double tap inserted duplicate timeline rows'
    );

    $workflows = [
        1 => ['menunggu', 'dicuci', 'dikeringkan', 'dipacking', 'siap_diambil'],
        2 => ['menunggu', 'dicuci', 'dikeringkan', 'disetrika', 'dipacking', 'siap_diambil'],
        3 => ['menunggu', 'dicuci', 'dikeringkan', 'disetrika', 'dipacking', 'siap_diambil'],
        4 => ['menunggu', 'dicuci', 'dikeringkan', 'dipacking', 'siap_diambil'],
    ];
    foreach ($workflows as $serviceId => $timeline) {
        $transactionId = $fixtureIds[] = createFixture($pdo, $serviceId, 'S' . $serviceId);
        $customerBefore = request('GET', 'customer_order_detail.php?id_user=' . CUSTOMER_ID . '&id_transaksi=' . $transactionId);
        assertSameValue(1, count($customerBefore['body']['data']['timeline']), 'Initial customer timeline is wrong');
        assertWorkflow($pdo, $transactionId, $timeline);
        $customerAfter = request('GET', 'customer_order_detail.php?id_user=' . CUSTOMER_ID . '&id_transaksi=' . $transactionId);
        assertSameValue($timeline, array_column($customerAfter['body']['data']['timeline'], 'status_laundry'), 'Customer timeline did not grow');
    }

    foreach (['siap_diambil', 'selesai', 'dibatalkan'] as $terminalStatus) {
        $terminalFixture = $fixtureIds[] = createFixture($pdo, 1, 'TERM', $terminalStatus);
        assertSameValue(409, updateStatus($terminalFixture, STAFF_ID, 'dipacking')['status'], "{$terminalStatus} was updateable");
        assertSameValue(
            $terminalStatus,
            $pdo->query("SELECT status_laundry FROM transaksi WHERE id_transaksi = {$terminalFixture}")->fetchColumn(),
            "Terminal status {$terminalStatus} changed"
        );
    }

    echo "PASS: staff jobs/history/dashboard read database\n";
    echo "PASS: Cuci Kering and Bed Cover skip Disetrika\n";
    echo "PASS: Cuci Setrika and Express include Disetrika\n";
    echo "PASS: Customer/Kasir/Owner denied\n";
    echo "PASS: selesai/dibatalkan/siap_diambil are terminal for Staff\n";
    echo "PASS: double submit advances exactly one status\n";
    echo "PASS: riwayat_status and Customer timeline stay synchronized\n";
} finally {
    if ($fixtureIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureIds), '?'));
        $delete = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
        $delete->execute($fixtureIds);
    }
}
