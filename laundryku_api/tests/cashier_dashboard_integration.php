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

function requestDashboard(int $userId): array
{
    $handle = curl_init(BASE_URL . 'cashier_dashboard.php?id_user=' . $userId);
    curl_setopt_array($handle, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10]);
    $body = curl_exec($handle);
    if ($body === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($body, true, 512, JSON_THROW_ON_ERROR)];
}

function createTransaction(
    PDO $pdo,
    string $suffix,
    string $laundryStatus,
    string $transactionPaymentStatus,
    string $paymentStatus,
    int $amount,
    bool $today = true,
    int $detailCount = 1
): int {
    $code = 'DASHTEST' . $suffix . bin2hex(random_bytes(2));
    $dateExpression = $today ? 'NOW()' : 'DATE_SUB(NOW(), INTERVAL 1 DAY)';
    $statement = $pdo->prepare(
        "INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, tanggal_masuk, estimasi_selesai,
          total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, {$dateExpression}, DATE_ADD({$dateExpression}, INTERVAL 2 DAY),
                 :total, :laundry, :payment)"
    );
    $statement->execute([
        'code' => $code,
        'customer' => CUSTOMER_ID,
        'total' => $amount,
        'laundry' => $laundryStatus,
        'payment' => $transactionPaymentStatus,
    ]);
    $transactionId = (int) $pdo->lastInsertId();

    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, :service, :qty, :price, :subtotal)'
    );
    for ($index = 0; $index < $detailCount; $index++) {
        $detail->execute([
            'transaction' => $transactionId,
            'service' => $index + 1,
            'qty' => $index === 0 ? 2.5 : 1,
            'price' => $amount / $detailCount,
            'subtotal' => $amount / $detailCount,
        ]);
    }

    $paymentDate = $paymentStatus === 'menunggu' ? null : ($today ? date('Y-m-d H:i:s') : date('Y-m-d H:i:s', strtotime('-1 day')));
    $payment = $pdo->prepare(
        "INSERT INTO pembayaran
         (id_transaksi, metode, jumlah, status, tanggal_bayar)
         VALUES (:transaction, 'cash', :amount, :status, :paid_at)"
    );
    $payment->execute([
        'transaction' => $transactionId,
        'amount' => $amount,
        'status' => $paymentStatus,
        'paid_at' => $paymentDate,
    ]);

    return $transactionId;
}

function ids(array $rows): array
{
    return array_map(static fn(array $row): int => (int) $row['id_transaksi'], $rows);
}

$pdo = getDatabaseConnection();
$fixtureIds = [];
$inactiveUserId = null;

try {
    $empty = requestDashboard(CASHIER_ID);
    assertSameValue(200, $empty['status'], 'Level 2 could not load dashboard');
    $baseline = $empty['body']['data']['summary'];

    foreach ([CUSTOMER_ID, STAFF_ID, OWNER_ID] as $userId) {
        assertSameValue(403, requestDashboard($userId)['status'], "Forbidden level for user {$userId} was allowed");
    }
    assertSameValue(403, requestDashboard(999999)['status'], 'Unknown user was allowed');

    $inactiveName = 'Dash Test';
    $inactiveUsername = 'dash' . random_int(1000, 9999);
    $inactivePhone = '089' . random_int(100000000, 999999999);
    $inactive = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT :name, :phone, :username, password, 2, 'nonaktif'
         FROM users WHERE id_user = :cashier"
    );
    $inactive->execute([
        'name' => $inactiveName,
        'phone' => $inactivePhone,
        'username' => $inactiveUsername,
        'cashier' => CASHIER_ID,
    ]);
    $inactiveUserId = (int) $pdo->lastInsertId();
    assertSameValue(403, requestDashboard($inactiveUserId)['status'], 'Inactive cashier was allowed');

    $fixtureIds[] = createTransaction($pdo, 'WAIT', 'menunggu', 'belum_dibayar', 'menunggu', 10000);
    $fixtureIds[] = createTransaction($pdo, 'WASH', 'dicuci', 'sudah_dibayar', 'berhasil', 20000);
    $readyId = $fixtureIds[] = createTransaction(
        $pdo,
        'READY',
        'siap_diambil',
        'sudah_dibayar',
        'berhasil',
        30000,
        true,
        2
    );
    $fixtureIds[] = createTransaction($pdo, 'DONE', 'selesai', 'sudah_dibayar', 'berhasil', 40000);
    $fixtureIds[] = createTransaction($pdo, 'CANCEL', 'dibatalkan', 'belum_dibayar', 'gagal', 50000);
    $fixtureIds[] = createTransaction($pdo, 'OLD', 'selesai', 'sudah_dibayar', 'berhasil', 60000, false);

    $response = requestDashboard(CASHIER_ID);
    assertSameValue(200, $response['status'], 'Fixture dashboard request failed');
    $data = $response['body']['data'];
    $summary = $data['summary'];
    assertSameValue((int) $baseline['pesanan_aktif'] + 3, $summary['pesanan_aktif'], 'Active count is wrong');
    assertSameValue((int) $baseline['belum_dibayar'] + 1, $summary['belum_dibayar'], 'Unpaid count is wrong');
    assertSameValue((int) $baseline['siap_diambil'] + 1, $summary['siap_diambil'], 'Ready count is wrong');
    assertSameValue((int) $baseline['transaksi_hari_ini'] + 5, $summary['transaksi_hari_ini'], 'Today count is wrong');
    assertSameValue((float) $baseline['pendapatan_hari_ini'] + 90000.0, (float) $summary['pendapatan_hari_ini'], 'Income is wrong');

    $recentIds = ids($data['recent_transactions']);
    assertSameValue(count($recentIds), count(array_unique($recentIds)), 'Recent transactions contain duplicates');
    assertSameValue(5, count($recentIds), 'Recent transaction limit is wrong');
    $readyRows = array_values(array_filter(
        $data['ready_transactions'],
        static fn(array $row): bool => (int) $row['id_transaksi'] === $readyId
    ));
    assertSameValue(1, count($readyRows), 'Ready transaction is missing or duplicated');
    assertSameValue(2, (int) $readyRows[0]['jumlah_layanan'], 'Multiple services were not summarized');

    echo "PASS: summary follows active, unpaid, ready, today and paid-today definitions\n";
    echo "PASS: waiting/failed/yesterday payments are excluded from today's income\n";
    echo "PASS: recent list is limited to five and multi-detail transactions are not duplicated\n";
    echo "PASS: Level 1/3/4, inactive and unknown users receive HTTP 403\n";
} finally {
    if ($fixtureIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureIds), '?'));
        $delete = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
        $delete->execute($fixtureIds);
    }
    if ($inactiveUserId !== null) {
        $deleteUser = $pdo->prepare('DELETE FROM users WHERE id_user = :id');
        $deleteUser->execute(['id' => $inactiveUserId]);
    }
}
