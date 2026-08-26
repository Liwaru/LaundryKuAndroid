<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, ?array $data = null): void
{
    http_response_code($status);
    $response = ['success' => $success, 'message' => $message];
    if ($data !== null) {
        $response['data'] = $data;
    }
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
    exit;
}

function positiveInteger(mixed $value): ?int
{
    return is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1
        ? (int) $value
        : null;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

$period = (string) ($_GET['period'] ?? '');
$periodLabels = [
    'today' => 'Hari Ini',
    'week' => 'Minggu Ini',
    'month' => 'Bulan Ini',
];
if (!array_key_exists($period, $periodLabels)) {
    respond(400, false, 'Periode laporan tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();

    requireRole($pdo, [4]);

    $rangeSql = match ($period) {
        'today' => 'SELECT CURDATE() AS period_start, CURDATE() + INTERVAL 1 DAY AS period_end',
        'week' => 'SELECT DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS period_start,
                          DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) + INTERVAL 7 DAY AS period_end',
        'month' => "SELECT DATE_FORMAT(CURDATE(), '%Y-%m-01') AS period_start,
                           DATE_FORMAT(CURDATE(), '%Y-%m-01') + INTERVAL 1 MONTH AS period_end",
    };
    $range = $pdo->query($rangeSql)->fetch();
    $rangeParameters = ['period_start' => $range['period_start'], 'period_end' => $range['period_end']];

    $incomeStatement = $pdo->prepare(
        "SELECT COALESCE(SUM(jumlah), 0)
         FROM pembayaran
         WHERE status = 'berhasil'
           AND tanggal_bayar >= :period_start
           AND tanggal_bayar < :period_end"
    );
    $incomeStatement->execute($rangeParameters);

    $transactionsCountStatement = $pdo->prepare(
        'SELECT COUNT(id_transaksi) FROM transaksi
         WHERE tanggal_masuk >= :period_start AND tanggal_masuk < :period_end'
    );
    $transactionsCountStatement->execute($rangeParameters);

    $completedStatement = $pdo->prepare(
        "SELECT COUNT(id_transaksi) FROM transaksi
         WHERE status_laundry = 'selesai'
           AND tanggal_selesai >= :period_start
           AND tanggal_selesai < :period_end"
    );
    $completedStatement->execute($rangeParameters);

    $cancelledStatement = $pdo->prepare(
        "SELECT COUNT(DISTINCT id_transaksi) FROM riwayat_status
         WHERE status_laundry = 'dibatalkan'
           AND waktu >= :period_start
           AND waktu < :period_end"
    );
    $cancelledStatement->execute($rangeParameters);

    $financialStatement = $pdo->prepare(
        "SELECT payment.id_pembayaran, transaction_row.id_transaksi,
                transaction_row.kode_transaksi, customer.nama AS nama_pelanggan,
                payment.jumlah, payment.metode, payment.payment_channel, payment.tanggal_bayar
         FROM pembayaran payment
         INNER JOIN transaksi transaction_row ON transaction_row.id_transaksi = payment.id_transaksi
         INNER JOIN users customer ON customer.id_user = transaction_row.id_pelanggan
         WHERE payment.status = 'berhasil'
           AND payment.tanggal_bayar >= :period_start
           AND payment.tanggal_bayar < :period_end
         ORDER BY payment.tanggal_bayar DESC, payment.id_pembayaran DESC"
    );
    $financialStatement->execute($rangeParameters);
    $financialReport = array_map(static fn(array $row): array => [
        'id_pembayaran' => (int) $row['id_pembayaran'],
        'id_transaksi' => (int) $row['id_transaksi'],
        'kode_transaksi' => $row['kode_transaksi'],
        'nama_pelanggan' => $row['nama_pelanggan'],
        'jumlah' => (float) $row['jumlah'],
        'metode' => $row['metode'],
        'payment_channel' => $row['payment_channel'],
        'tanggal_bayar' => $row['tanggal_bayar'],
    ], $financialStatement->fetchAll());

    $transactionStatement = $pdo->prepare(
        'SELECT transaction_row.id_transaksi, transaction_row.kode_transaksi,
                customer.nama AS nama_pelanggan, transaction_row.total_harga,
                transaction_row.status_laundry, transaction_row.status_pembayaran,
                transaction_row.tanggal_masuk, transaction_row.tanggal_selesai,
                (SELECT service.nama_layanan
                 FROM detail_transaksi detail
                 INNER JOIN layanan service ON service.id_layanan = detail.id_layanan
                 WHERE detail.id_transaksi = transaction_row.id_transaksi
                 ORDER BY detail.id_detail ASC LIMIT 1) AS nama_layanan,
                (SELECT detail.qty FROM detail_transaksi detail
                 WHERE detail.id_transaksi = transaction_row.id_transaksi
                 ORDER BY detail.id_detail ASC LIMIT 1) AS qty,
                (SELECT service.satuan
                 FROM detail_transaksi detail
                 INNER JOIN layanan service ON service.id_layanan = detail.id_layanan
                 WHERE detail.id_transaksi = transaction_row.id_transaksi
                 ORDER BY detail.id_detail ASC LIMIT 1) AS satuan,
                (SELECT COUNT(*) FROM detail_transaksi detail
                 WHERE detail.id_transaksi = transaction_row.id_transaksi) AS jumlah_layanan
         FROM transaksi transaction_row
         INNER JOIN users customer ON customer.id_user = transaction_row.id_pelanggan
         WHERE transaction_row.tanggal_masuk >= :period_start
           AND transaction_row.tanggal_masuk < :period_end
         ORDER BY transaction_row.tanggal_masuk DESC, transaction_row.id_transaksi DESC'
    );
    $transactionStatement->execute($rangeParameters);
    $transactionReport = array_map(static fn(array $row): array => [
        'id_transaksi' => (int) $row['id_transaksi'],
        'kode_transaksi' => $row['kode_transaksi'],
        'nama_pelanggan' => $row['nama_pelanggan'],
        'nama_layanan' => $row['nama_layanan'],
        'qty' => $row['qty'] === null ? null : (float) $row['qty'],
        'satuan' => $row['satuan'],
        'jumlah_layanan' => (int) $row['jumlah_layanan'],
        'total_harga' => (float) $row['total_harga'],
        'status_laundry' => $row['status_laundry'],
        'status_pembayaran' => $row['status_pembayaran'],
        'tanggal_masuk' => $row['tanggal_masuk'],
        'tanggal_selesai' => $row['tanggal_selesai'],
    ], $transactionStatement->fetchAll());

    $servicesStatement = $pdo->prepare(
        'SELECT service.id_layanan, service.nama_layanan, COUNT(detail.id_detail) AS jumlah_pesanan
         FROM detail_transaksi detail
         INNER JOIN layanan service ON service.id_layanan = detail.id_layanan
         INNER JOIN transaksi transaction_row ON transaction_row.id_transaksi = detail.id_transaksi
         WHERE transaction_row.tanggal_masuk >= :period_start
           AND transaction_row.tanggal_masuk < :period_end
         GROUP BY service.id_layanan, service.nama_layanan
         ORDER BY jumlah_pesanan DESC, service.nama_layanan ASC
         LIMIT 4'
    );
    $servicesStatement->execute($rangeParameters);
    $popularServices = array_map(static fn(array $row): array => [
        'id_layanan' => (int) $row['id_layanan'],
        'nama_layanan' => $row['nama_layanan'],
        'jumlah_pesanan' => (int) $row['jumlah_pesanan'],
    ], $servicesStatement->fetchAll());

    respond(200, true, 'Laporan Owner berhasil dimuat', [
        'period' => $period,
        'period_label' => $periodLabels[$period],
        'summary' => [
            'pendapatan' => (float) $incomeStatement->fetchColumn(),
            'total_transaksi' => (int) $transactionsCountStatement->fetchColumn(),
            'selesai' => (int) $completedStatement->fetchColumn(),
            'dibatalkan' => (int) $cancelledStatement->fetchColumn(),
        ],
        'financial_report' => $financialReport,
        'transaction_report' => $transactionReport,
        'popular_services' => $popularServices,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
