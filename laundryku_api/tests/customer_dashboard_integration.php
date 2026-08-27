<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/auth_test_helper.php';

const BASE_URL = 'http://127.0.0.1/laundryku_api/api/';
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

function requestDashboard(?int $userId): array
{
    $handle = curl_init(BASE_URL . 'customer_dashboard.php');
    $options = [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10];
    if ($userId !== null) {
        $options[CURLOPT_HTTPHEADER] = testAuthHeaders($userId);
    }
    curl_setopt_array($handle, $options);
    $body = curl_exec($handle);
    if ($body === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($body, true, 512, JSON_THROW_ON_ERROR)];
}

function insertCustomer(PDO $pdo, string $suffix): int
{
    $statement = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT :name, :phone, :username, password, 1, 'aktif'
         FROM users WHERE id_user = :owner"
    );
    $statement->execute([
        'name' => 'Dash' . $suffix,
        'phone' => '089' . random_int(100000000, 999999999),
        'username' => 'dsh' . strtolower($suffix) . random_int(10, 99),
        'owner' => OWNER_ID,
    ]);
    return (int) $pdo->lastInsertId();
}

function insertOrder(
    PDO $pdo,
    int $customerId,
    int $serviceId,
    string $suffix,
    string $status,
    string $date,
    bool $secondDetail = false
): int {
    $statement = $pdo->prepare(
        "INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, tanggal_masuk, estimasi_selesai, tanggal_selesai,
          total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, :entered, DATE_ADD(:estimate_base, INTERVAL 2 DAY), :completed,
                 :total, :status, :payment)"
    );
    $statement->execute([
        'code' => 'DASHTEST' . $suffix . random_int(10, 99),
        'customer' => $customerId,
        'entered' => $date,
        'estimate_base' => $date,
        'completed' => $status === 'selesai' ? $date : null,
        'total' => $secondDetail ? 20000 : 10000,
        'status' => $status,
        'payment' => $status === 'selesai' ? 'sudah_dibayar' : 'belum_dibayar',
    ]);
    $transactionId = (int) $pdo->lastInsertId();

    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, :service, 1, 10000, 10000)'
    );
    $detail->execute(['transaction' => $transactionId, 'service' => $serviceId]);
    if ($secondDetail) {
        $secondService = (int) $pdo->query(
            "SELECT id_layanan FROM layanan WHERE id_layanan <> {$serviceId} ORDER BY id_layanan LIMIT 1"
        )->fetchColumn();
        $detail->execute(['transaction' => $transactionId, 'service' => $secondService]);
    }
    return $transactionId;
}

$pdo = getDatabaseConnection();
$customerIds = [];
$transactionIds = [];

try {
    assertSameValue(401, requestDashboard(null)['status'], 'Missing token was allowed');
    foreach ([CASHIER_ID, STAFF_ID, OWNER_ID] as $forbiddenUser) {
        assertSameValue(403, requestDashboard($forbiddenUser)['status'], "Role {$forbiddenUser} was allowed");
    }

    $serviceId = (int) $pdo->query('SELECT id_layanan FROM layanan ORDER BY id_layanan LIMIT 1')->fetchColumn();
    if ($serviceId < 1) {
        throw new RuntimeException('Service fixture is unavailable');
    }

    $emptyCustomer = $customerIds[] = insertCustomer($pdo, 'Empty');
    $empty = requestDashboard($emptyCustomer);
    assertSameValue(200, $empty['status'], 'Empty customer dashboard failed');
    assertSameValue(null, $empty['body']['data']['active_order'], 'Empty active order is not null');
    assertSameValue([], $empty['body']['data']['recent_orders'], 'Empty recent order list is wrong');

    $customerA = $customerIds[] = insertCustomer($pdo, 'A');
    $customerB = $customerIds[] = insertCustomer($pdo, 'B');
    $activeId = $transactionIds[] = insertOrder(
        $pdo, $customerA, $serviceId, 'ACTIVE', 'dicuci', '2026-08-27 08:00:00', true
    );
    $completedId = $transactionIds[] = insertOrder(
        $pdo, $customerA, $serviceId, 'DONE', 'selesai', '2026-08-27 09:00:00'
    );
    $cancelledId = $transactionIds[] = insertOrder(
        $pdo, $customerA, $serviceId, 'CANCEL', 'dibatalkan', '2026-08-27 10:00:00'
    );
    $otherCustomerOrderId = $transactionIds[] = insertOrder(
        $pdo, $customerB, $serviceId, 'OTHER', 'siap_diambil', '2026-08-27 11:00:00'
    );

    $response = requestDashboard($customerA);
    assertSameValue(200, $response['status'], 'Customer fixture dashboard failed');
    $data = $response['body']['data'];
    assertSameValue($activeId, (int) $data['active_order']['id_transaksi'], 'Wrong active order selected');
    assertSameValue('dicuci', $data['active_order']['status_laundry'], 'Active status is wrong');
    assertSameValue(2, (int) $data['active_order']['jumlah_layanan'], 'Multi-service summary is wrong');
    assertSameValue(2, count($data['recent_orders']), 'Recent order limit is not two');
    assertSameValue(
        [$cancelledId, $completedId],
        array_map('intval', array_column($data['recent_orders'], 'id_transaksi')),
        'Recent orders are not newest first'
    );
    assertSameValue(
        false,
        in_array($otherCustomerOrderId, array_map('intval', array_column($data['recent_orders'], 'id_transaksi')), true),
        'Customer B order leaked to Customer A'
    );

    $otherCustomer = requestDashboard($customerB)['body']['data'];
    assertSameValue(1, count($otherCustomer['recent_orders']), 'Customer B did not receive only its own order');
    assertSameValue(
        $otherCustomerOrderId,
        (int) $otherCustomer['recent_orders'][0]['id_transaksi'],
        'Customer B received another customer order'
    );
    assertSameValue('siap_diambil', $otherCustomer['active_order']['status_laundry'], 'Customer B active order is wrong');

    echo "PASS: no-order customer returns active null and recent empty\n";
    echo "PASS: latest active order uses database data and summarizes multiple services\n";
    echo "PASS: recent orders are limited to two newest records including terminal statuses\n";
    echo "PASS: selesai/dibatalkan are excluded from active order\n";
    echo "PASS: ownership isolates customers\n";
    echo "PASS: no token receives 401 and Levels 2/3/4 receive 403\n";
} finally {
    if ($transactionIds !== []) {
        $placeholders = implode(',', array_fill(0, count($transactionIds), '?'));
        $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})")->execute($transactionIds);
    }
    if ($customerIds !== []) {
        $placeholders = implode(',', array_fill(0, count($customerIds), '?'));
        $pdo->prepare("DELETE FROM users WHERE id_user IN ({$placeholders})")->execute($customerIds);
    }
}
