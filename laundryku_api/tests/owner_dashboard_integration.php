<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/auth_test_helper.php';

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

function requestDashboard(int $userId): array
{
    $handle = curl_init(BASE_URL . 'owner_dashboard.php');
    curl_setopt_array($handle, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10, CURLOPT_HTTPHEADER => testAuthHeaders($userId)]);
    $body = curl_exec($handle);
    if ($body === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($body, true, 512, JSON_THROW_ON_ERROR)];
}

function createTransaction(PDO $pdo, string $suffix, string $status, array $serviceIds): int
{
    $statement = $pdo->prepare(
        "INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, tanggal_masuk, estimasi_selesai,
          total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, NOW(), DATE_ADD(NOW(), INTERVAL 2 DAY),
                 10000, :status, 'belum_dibayar')"
    );
    $statement->execute([
        'code' => 'OWNERTEST' . $suffix . bin2hex(random_bytes(2)),
        'customer' => CUSTOMER_ID,
        'status' => $status,
    ]);
    $transactionId = (int) $pdo->lastInsertId();

    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, :service, 1, 10000, 10000)'
    );
    foreach ($serviceIds as $serviceId) {
        $detail->execute(['transaction' => $transactionId, 'service' => $serviceId]);
    }

    $history = $pdo->prepare(
        'INSERT INTO riwayat_status (id_transaksi, id_user, status_laundry, catatan)
         VALUES (:transaction, :user, :status, :note)'
    );
    $history->execute([
        'transaction' => $transactionId,
        'user' => OWNER_ID,
        'status' => $status,
        'note' => 'Temporary owner dashboard fixture',
    ]);
    return $transactionId;
}

function createPayment(PDO $pdo, int $transactionId, string $status, int $amount, ?string $dateExpression): void
{
    $paidAt = $dateExpression === null ? 'NULL' : $dateExpression;
    $statement = $pdo->prepare(
        "INSERT INTO pembayaran (id_transaksi, metode, jumlah, status, tanggal_bayar)
         VALUES (:transaction, 'cash', :amount, :status, {$paidAt})"
    );
    $statement->execute([
        'transaction' => $transactionId,
        'amount' => $amount,
        'status' => $status,
    ]);
}

$pdo = getDatabaseConnection();
$fixtureIds = [];
$inactiveOwnerId = null;
$customerFixtureId = null;

try {
    $initial = requestDashboard(OWNER_ID);
    assertSameValue(200, $initial['status'], 'Level 4 could not load dashboard');
    assertSameValue(true, $initial['body']['success'], 'Dashboard request must remain successful');
    $baseline = $initial['body']['data'];
    $usageRows = $pdo->query(
        'SELECT service.id_layanan, service.nama_layanan, COUNT(detail.id_detail) AS jumlah_pesanan
         FROM layanan service
         LEFT JOIN detail_transaksi detail ON detail.id_layanan = service.id_layanan
         GROUP BY service.id_layanan, service.nama_layanan'
    )->fetchAll();
    $fixtureUsage = [1 => 1, 2 => 3, 3 => 2, 4 => 1];
    $expectedRanking = array_values(array_filter(array_map(
        static fn(array $row): array => [
            'nama_layanan' => $row['nama_layanan'],
            'jumlah_pesanan' => (int) $row['jumlah_pesanan'] + ($fixtureUsage[(int) $row['id_layanan']] ?? 0),
        ],
        $usageRows
    ), static fn(array $row): bool => $row['jumlah_pesanan'] > 0));
    usort($expectedRanking, static fn(array $left, array $right): int =>
        $right['jumlah_pesanan'] <=> $left['jumlah_pesanan']
            ?: strcmp($left['nama_layanan'], $right['nama_layanan'])
    );
    $expectedRanking = array_slice($expectedRanking, 0, 4);

    foreach ([CUSTOMER_ID, CASHIER_ID, STAFF_ID] as $userId) {
        assertSameValue(403, requestDashboard($userId)['status'], "Forbidden level for user {$userId} was allowed");
    }
    assertSameValue(401, requestDashboard(999999)['status'], 'Unknown user was allowed');

    $inactive = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT 'Own Test', :phone, :username, password, 4, 'nonaktif'
         FROM users WHERE id_user = :owner"
    );
    $inactive->execute([
        'phone' => '088' . random_int(100000000, 999999999),
        'username' => 'own' . random_int(10000, 99999),
        'owner' => OWNER_ID,
    ]);
    $inactiveOwnerId = (int) $pdo->lastInsertId();
    assertSameValue(401, requestDashboard($inactiveOwnerId)['status'], 'Inactive owner was allowed');

    $customer = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT 'Cust Test', :phone, :username, password, 1, 'nonaktif'
         FROM users WHERE id_user = :customer"
    );
    $customer->execute([
        'phone' => '087' . random_int(100000000, 999999999),
        'username' => 'cust' . random_int(1000, 9999),
        'customer' => CUSTOMER_ID,
    ]);
    $customerFixtureId = (int) $pdo->lastInsertId();

    $waitingId = $fixtureIds[] = createTransaction($pdo, 'WAIT', 'menunggu', [2, 1]);
    $washingId = $fixtureIds[] = createTransaction($pdo, 'WASH', 'dicuci', [2, 3]);
    $readyId = $fixtureIds[] = createTransaction($pdo, 'READY', 'siap_diambil', [2, 4]);
    $doneId = $fixtureIds[] = createTransaction($pdo, 'DONE', 'selesai', [3]);
    $fixtureIds[] = createTransaction($pdo, 'CANCEL', 'dibatalkan', []);

    createPayment($pdo, $waitingId, 'menunggu', 10000, null);
    createPayment($pdo, $washingId, 'berhasil', 150000, 'NOW()');
    createPayment($pdo, $readyId, 'gagal', 30000, 'NOW()');
    createPayment($pdo, $doneId, 'berhasil', 40000, 'DATE_SUB(NOW(), INTERVAL 1 DAY)');

    $response = requestDashboard(OWNER_ID);
    assertSameValue(200, $response['status'], 'Fixture dashboard request failed');
    $data = $response['body']['data'];
    $summary = $data['summary'];
    assertSameValue((float) $baseline['summary']['pendapatan_hari_ini'] + 150000.0, (float) $summary['pendapatan_hari_ini'], 'Income is wrong');
    assertSameValue((int) $baseline['summary']['transaksi_hari_ini'] + 5, $summary['transaksi_hari_ini'], 'Today transaction count is wrong');
    assertSameValue((int) $baseline['summary']['pesanan_aktif'] + 3, $summary['pesanan_aktif'], 'Active order count is wrong');
    assertSameValue((int) $baseline['summary']['total_pelanggan'] + 1, $summary['total_pelanggan'], 'All Level 1 users must be counted');

    assertSameValue((int) $baseline['operational_status']['menunggu'] + 1, $data['operational_status']['menunggu'], 'Waiting status is wrong');
    assertSameValue((int) $baseline['operational_status']['dicuci'] + 1, $data['operational_status']['dicuci'], 'Washing status is wrong');
    assertSameValue((int) $baseline['operational_status']['siap_diambil'] + 1, $data['operational_status']['siap_diambil'], 'Ready status is wrong');
    assertSameValue((int) $baseline['operational_status']['dikeringkan'], $data['operational_status']['dikeringkan'], 'Drying status changed unexpectedly');
    assertSameValue((int) $baseline['operational_status']['disetrika'], $data['operational_status']['disetrika'], 'Ironing status changed unexpectedly');
    assertSameValue((int) $baseline['operational_status']['dipacking'], $data['operational_status']['dipacking'], 'Packing status changed unexpectedly');

    assertSameValue($expectedRanking, array_map(static fn(array $row): array => [
        'nama_layanan' => $row['nama_layanan'],
        'jumlah_pesanan' => (int) $row['jumlah_pesanan'],
    ], $data['popular_services']), 'Popular-service ranking or deterministic tie-break is wrong');

    echo "PASS: Level 1/2/3 receive HTTP 403; inactive/unknown sessions receive HTTP 401; active Level 4 receives HTTP 200\n";
    echo "PASS: summary includes only successful payment today, all orders today, active statuses and every Level 1 user\n";
    echo "PASS: operational counts exclude completed/cancelled transactions\n";
    echo "PASS: popular services count detail rows and sort by usage then name\n";
} finally {
    if ($fixtureIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureIds), '?'));
        foreach (['riwayat_status', 'pembayaran', 'detail_transaksi'] as $table) {
            $delete = $pdo->prepare("DELETE FROM {$table} WHERE id_transaksi IN ({$placeholders})");
            $delete->execute($fixtureIds);
        }
        $deleteTransactions = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
        $deleteTransactions->execute($fixtureIds);
    }
    foreach ([$inactiveOwnerId, $customerFixtureId] as $userId) {
        if ($userId !== null) {
            $deleteUser = $pdo->prepare('DELETE FROM users WHERE id_user = :id');
            $deleteUser->execute(['id' => $userId]);
        }
    }
}
