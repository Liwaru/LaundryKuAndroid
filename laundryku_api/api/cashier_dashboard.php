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

function transactionRows(PDO $pdo, ?string $status = null): array
{
    $where = $status === null ? '' : 'WHERE t.status_laundry = :status';
    $statement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi, customer.nama AS nama_pelanggan,
                t.total_harga, t.status_laundry, t.status_pembayaran, t.tanggal_masuk,
                payment.metode AS metode_pembayaran,
                (SELECT service.nama_layanan
                 FROM detail_transaksi detail
                 INNER JOIN layanan service ON service.id_layanan = detail.id_layanan
                 WHERE detail.id_transaksi = t.id_transaksi
                 ORDER BY detail.id_detail ASC LIMIT 1) AS nama_layanan,
                (SELECT detail.qty FROM detail_transaksi detail
                 WHERE detail.id_transaksi = t.id_transaksi
                 ORDER BY detail.id_detail ASC LIMIT 1) AS qty,
                (SELECT service.satuan
                 FROM detail_transaksi detail
                 INNER JOIN layanan service ON service.id_layanan = detail.id_layanan
                 WHERE detail.id_transaksi = t.id_transaksi
                 ORDER BY detail.id_detail ASC LIMIT 1) AS satuan,
                (SELECT COUNT(*) FROM detail_transaksi detail
                 WHERE detail.id_transaksi = t.id_transaksi) AS jumlah_layanan
         FROM transaksi t
         INNER JOIN users customer ON customer.id_user = t.id_pelanggan
         LEFT JOIN pembayaran payment ON payment.id_transaksi = t.id_transaksi
         {$where}
         ORDER BY t.tanggal_masuk DESC, t.id_transaksi DESC
         LIMIT 5"
    );
    $statement->execute($status === null ? [] : ['status' => $status]);

    return array_map(static fn(array $row): array => [
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
        'metode_pembayaran' => $row['metode_pembayaran'],
        'tanggal_masuk' => $row['tanggal_masuk'],
    ], $statement->fetchAll());
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();

    requireRole($pdo, [2]);

    $summaryStatement = $pdo->prepare(
        "SELECT
            COALESCE(SUM(status_laundry IN
                ('menunggu','dicuci','dikeringkan','disetrika','dipacking','siap_diambil')), 0)
                AS pesanan_aktif,
            COALESCE(SUM(status_pembayaran = 'belum_dibayar'
                AND status_laundry <> 'dibatalkan'), 0) AS belum_dibayar,
            COALESCE(SUM(status_laundry = 'siap_diambil'), 0) AS siap_diambil,
            COALESCE(SUM(tanggal_masuk >= CURDATE()
                AND tanggal_masuk < CURDATE() + INTERVAL 1 DAY), 0) AS transaksi_hari_ini
         FROM transaksi"
    );
    $summaryStatement->execute();
    $summary = $summaryStatement->fetch();

    $incomeStatement = $pdo->prepare(
        "SELECT COALESCE(SUM(jumlah), 0) AS pendapatan_hari_ini
         FROM pembayaran
         WHERE status = 'berhasil'
           AND tanggal_bayar >= CURDATE()
           AND tanggal_bayar < CURDATE() + INTERVAL 1 DAY"
    );
    $incomeStatement->execute();

    respond(200, true, 'Dashboard Kasir berhasil dimuat', [
        'summary' => [
            'pesanan_aktif' => (int) $summary['pesanan_aktif'],
            'belum_dibayar' => (int) $summary['belum_dibayar'],
            'siap_diambil' => (int) $summary['siap_diambil'],
            'transaksi_hari_ini' => (int) $summary['transaksi_hari_ini'],
            'pendapatan_hari_ini' => (float) $incomeStatement->fetchColumn(),
        ],
        'recent_transactions' => transactionRows($pdo),
        'ready_transactions' => transactionRows($pdo, 'siap_diambil'),
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
