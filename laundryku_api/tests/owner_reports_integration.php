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

function requestReport(int $userId, string $period): array
{
    $handle = curl_init(BASE_URL . "owner_reports.php?id_user={$userId}&period={$period}");
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
    string $enteredAt,
    string $status = 'menunggu',
    ?string $completedAt = null,
    array $serviceIds = []
): int {
    $statement = $pdo->prepare(
        'INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, tanggal_masuk, estimasi_selesai, tanggal_selesai,
          total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, :entered_at, DATE_ADD(:estimate_base, INTERVAL 2 DAY), :completed_at,
                 50000, :status, :payment_status)'
    );
    $statement->execute([
        'code' => 'RPT' . $suffix . bin2hex(random_bytes(2)),
        'customer' => CUSTOMER_ID,
        'entered_at' => $enteredAt,
        'estimate_base' => $enteredAt,
        'completed_at' => $completedAt,
        'status' => $status,
        'payment_status' => $status === 'selesai' ? 'sudah_dibayar' : 'belum_dibayar',
    ]);
    $transactionId = (int) $pdo->lastInsertId();
    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, :service, :qty, 10000, 10000)'
    );
    foreach ($serviceIds as $index => $serviceId) {
        $detail->execute([
            'transaction' => $transactionId,
            'service' => $serviceId,
            'qty' => $index === 0 ? 4.5 : 1,
        ]);
    }
    return $transactionId;
}

function createPayment(
    PDO $pdo,
    int $transactionId,
    string $status,
    int $amount,
    ?string $paidAt
): int {
    $statement = $pdo->prepare(
        "INSERT INTO pembayaran
         (id_transaksi, metode, jumlah, status, tanggal_bayar)
         VALUES (:transaction, 'cash', :amount, :status, :paid_at)"
    );
    $statement->execute([
        'transaction' => $transactionId,
        'amount' => $amount,
        'status' => $status,
        'paid_at' => $paidAt,
    ]);
    return (int) $pdo->lastInsertId();
}

function createCancellation(PDO $pdo, int $transactionId, string $cancelledAt, int $rows = 1): void
{
    $statement = $pdo->prepare(
        "INSERT INTO riwayat_status (id_transaksi, id_user, status_laundry, waktu, catatan)
         VALUES (:transaction, :user, 'dibatalkan', :cancelled_at, 'Temporary report fixture')"
    );
    for ($index = 0; $index < $rows; $index++) {
        $statement->execute([
            'transaction' => $transactionId,
            'user' => OWNER_ID,
            'cancelled_at' => $cancelledAt,
        ]);
    }
}

function deleteTransactions(PDO $pdo, array $ids): void
{
    if ($ids === []) {
        return;
    }
    $placeholders = implode(',', array_fill(0, count($ids), '?'));
    foreach (['riwayat_status', 'pembayaran', 'detail_transaksi'] as $table) {
        $delete = $pdo->prepare("DELETE FROM {$table} WHERE id_transaksi IN ({$placeholders})");
        $delete->execute($ids);
    }
    $delete = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
    $delete->execute($ids);
}

function reportIds(array $rows): array
{
    return array_map(static fn(array $row): int => (int) $row['id_transaksi'], $rows);
}

function containsKeyRecursively(mixed $value, string $key): bool
{
    if (!is_array($value)) {
        return false;
    }
    if (array_key_exists($key, $value)) {
        return true;
    }
    foreach ($value as $child) {
        if (containsKeyRecursively($child, $key)) {
            return true;
        }
    }
    return false;
}

$pdo = getDatabaseConnection();
$fixtureIds = [];
$inactiveOwnerId = null;

try {
    foreach (['today' => 'Hari Ini', 'week' => 'Minggu Ini', 'month' => 'Bulan Ini'] as $period => $label) {
        $empty = requestReport(OWNER_ID, $period);
        assertSameValue(200, $empty['status'], "Empty {$period} report failed");
        assertSameValue($period, $empty['body']['data']['period'], 'Period key is wrong');
        assertSameValue($label, $empty['body']['data']['period_label'], 'Period label is wrong');
        assertSameValue([
            'pendapatan' => 0,
            'total_transaksi' => 0,
            'selesai' => 0,
            'dibatalkan' => 0,
        ], $empty['body']['data']['summary'], "Empty {$period} summary is wrong");
        assertSameValue([], $empty['body']['data']['financial_report'], "Empty {$period} financial report is not empty");
        assertSameValue([], $empty['body']['data']['transaction_report'], "Empty {$period} transaction report is not empty");
        assertSameValue([], $empty['body']['data']['popular_services'], "Empty {$period} services are not empty");
    }

    assertSameValue(400, requestReport(OWNER_ID, 'year')['status'], 'Invalid period was accepted');
    foreach ([CUSTOMER_ID, CASHIER_ID, STAFF_ID] as $userId) {
        assertSameValue(403, requestReport($userId, 'today')['status'], "Forbidden Level for {$userId} loaded reports");
    }
    assertSameValue(403, requestReport(999999, 'today')['status'], 'Unknown user loaded reports');

    $inactive = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT 'Rpt Owner', :phone, :username, password, 4, 'nonaktif'
         FROM users WHERE id_user = :owner"
    );
    $inactive->execute([
        'phone' => '089' . random_int(100000000, 999999999),
        'username' => 'rpto' . random_int(1000, 9999),
        'owner' => OWNER_ID,
    ]);
    $inactiveOwnerId = (int) $pdo->lastInsertId();
    assertSameValue(403, requestReport($inactiveOwnerId, 'today')['status'], 'Inactive Owner loaded reports');

    $clock = $pdo->query(
        "SELECT NOW() AS now_value,
                DATE_SUB(NOW(), INTERVAL 1 DAY) AS yesterday_value,
                DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) + INTERVAL 1 HOUR AS week_start,
                DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) - INTERVAL 1 SECOND AS previous_week,
                DATE_FORMAT(CURDATE(), '%Y-%m-01') + INTERVAL 1 HOUR AS month_start,
                DATE_FORMAT(CURDATE(), '%Y-%m-01') - INTERVAL 1 SECOND AS previous_month"
    )->fetch();
    $today = $clock['now_value'];
    $yesterday = $clock['yesterday_value'];

    $todayCompleted = $fixtureIds[] = createTransaction($pdo, 'TC', $today, 'selesai', $today, [2, 1]);
    $todayCancelled = $fixtureIds[] = createTransaction($pdo, 'TX', $today, 'dibatalkan', null, [2, 3]);
    $crossCompleted = $fixtureIds[] = createTransaction($pdo, 'XC', $yesterday, 'selesai', $today);
    $crossCancelled = $fixtureIds[] = createTransaction($pdo, 'XX', $yesterday, 'dibatalkan');
    $yesterdayPaid = $fixtureIds[] = createTransaction($pdo, 'YP', $yesterday, 'selesai', $yesterday);

    $todayPaymentId = createPayment($pdo, $todayCompleted, 'berhasil', 100000, $today);
    $crossPaymentId = createPayment($pdo, $crossCompleted, 'berhasil', 200000, $today);
    createPayment($pdo, $todayCancelled, 'menunggu', 50000, null);
    createPayment($pdo, $crossCancelled, 'gagal', 30000, $today);
    createPayment($pdo, $yesterdayPaid, 'berhasil', 400000, $yesterday);
    createCancellation($pdo, $todayCancelled, $today, 2);
    createCancellation($pdo, $crossCancelled, $today, 2);

    $todayReport = requestReport(OWNER_ID, 'today');
    assertSameValue(200, $todayReport['status'], 'Today fixture report failed');
    $todayData = $todayReport['body']['data'];
    assertSameValue([
        'pendapatan' => 300000,
        'total_transaksi' => 2,
        'selesai' => 2,
        'dibatalkan' => 2,
    ], $todayData['summary'], 'Today metrics mixed their timestamp definitions');
    assertSameValue([$crossPaymentId, $todayPaymentId], array_column($todayData['financial_report'], 'id_pembayaran'), 'Financial rows or ordering are wrong');
    assertSameValue([$todayCancelled, $todayCompleted], reportIds($todayData['transaction_report']), 'Transaction rows are wrong or duplicated');
    $completedRows = array_values(array_filter(
        $todayData['transaction_report'],
        static fn(array $row): bool => (int) $row['id_transaksi'] === $todayCompleted
    ));
    assertSameValue(1, count($completedRows), 'Multi-detail transaction was duplicated');
    assertSameValue(2, (int) $completedRows[0]['jumlah_layanan'], 'Multi-detail count is wrong');
    assertSameValue(false, containsKeyRecursively($todayReport['body'], 'password'), 'Password leaked in report');

    $popularExtra = $fixtureIds[] = createTransaction($pdo, 'POP', $today, 'menunggu', null, [2, 3]);
    $outsidePopular = $fixtureIds[] = createTransaction($pdo, 'OUT', $clock['previous_month']);
    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, 4, 1, 10000, 10000)'
    );
    for ($index = 0; $index < 20; $index++) {
        $detail->execute(['transaction' => $outsidePopular]);
    }
    $popularRows = requestReport(OWNER_ID, 'today')['body']['data']['popular_services'];
    assertSameValue(
        ['Cuci Setrika:3', 'Express:2', 'Cuci Kering:1'],
        array_map(static fn(array $row): string => $row['nama_layanan'] . ':' . $row['jumlah_pesanan'], $popularRows),
        'Period popular-service ranking is wrong or includes outside Bed Cover rows'
    );

    deleteTransactions($pdo, $fixtureIds);
    $fixtureIds = [];

    $weekInside = $fixtureIds[] = createTransaction($pdo, 'WI', $clock['week_start'], 'selesai', $clock['week_start'], [2]);
    $weekOutside = $fixtureIds[] = createTransaction($pdo, 'WO', $clock['previous_week'], 'selesai', $clock['previous_week'], [4]);
    createPayment($pdo, $weekInside, 'berhasil', 50000, $clock['week_start']);
    createPayment($pdo, $weekOutside, 'berhasil', 60000, $clock['previous_week']);
    createCancellation($pdo, $weekInside, $clock['week_start']);
    createCancellation($pdo, $weekOutside, $clock['previous_week']);
    $weekData = requestReport(OWNER_ID, 'week')['body']['data'];
    assertSameValue([
        'pendapatan' => 50000,
        'total_transaksi' => 1,
        'selesai' => 1,
        'dibatalkan' => 1,
    ], $weekData['summary'], 'Calendar-week boundaries are wrong');
    assertSameValue([$weekInside], reportIds($weekData['transaction_report']), 'Previous calendar week leaked into transaction report');
    assertSameValue([$weekInside], reportIds($weekData['financial_report']), 'Previous calendar week leaked into financial report');

    deleteTransactions($pdo, $fixtureIds);
    $fixtureIds = [];

    $monthCompleted = $fixtureIds[] = createTransaction($pdo, 'MI', $clock['month_start'], 'selesai', $clock['month_start'], [3]);
    $monthCancelled = $fixtureIds[] = createTransaction($pdo, 'MX', $clock['month_start'], 'dibatalkan');
    $previousMonthCompleted = $fixtureIds[] = createTransaction($pdo, 'MO', $clock['previous_month'], 'selesai', $clock['previous_month'], [4]);
    $previousMonthCancelled = $fixtureIds[] = createTransaction($pdo, 'MY', $clock['previous_month'], 'dibatalkan');
    createPayment($pdo, $monthCompleted, 'berhasil', 70000, $clock['month_start']);
    createPayment($pdo, $previousMonthCompleted, 'berhasil', 80000, $clock['previous_month']);
    createCancellation($pdo, $monthCancelled, $clock['month_start'], 2);
    createCancellation($pdo, $previousMonthCancelled, $clock['previous_month'], 2);
    $monthData = requestReport(OWNER_ID, 'month')['body']['data'];
    assertSameValue([
        'pendapatan' => 70000,
        'total_transaksi' => 2,
        'selesai' => 1,
        'dibatalkan' => 1,
    ], $monthData['summary'], 'Calendar-month boundaries are wrong');
    assertSameValue([$monthCancelled, $monthCompleted], reportIds($monthData['transaction_report']), 'Previous month leaked into transaction report');
    assertSameValue([$monthCompleted], reportIds($monthData['financial_report']), 'Previous month leaked into financial report');
    assertSameValue(['Express:1'], array_map(
        static fn(array $row): string => $row['nama_layanan'] . ':' . $row['jumlah_pesanan'],
        $monthData['popular_services']
    ), 'Month service ranking included previous-month details');

    echo "PASS: empty today/week/month responses return zero summaries and empty arrays\n";
    echo "PASS: income, orders, completion and cancellation use their own timestamps; cancellation is distinct\n";
    echo "PASS: financial/transaction reports filter and order correctly without multi-detail duplication\n";
    echo "PASS: popular services use transaction entry period and exclude outside-period Bed Cover usage\n";
    echo "PASS: calendar Monday-week and calendar-month boundaries are enforced\n";
    echo "PASS: invalid period and Level 1/2/3, inactive/unknown Owner are rejected\n";
} finally {
    deleteTransactions($pdo, $fixtureIds);
    if ($inactiveOwnerId !== null) {
        $deleteOwner = $pdo->prepare('DELETE FROM users WHERE id_user = :id');
        $deleteOwner->execute(['id' => $inactiveOwnerId]);
    }
}
