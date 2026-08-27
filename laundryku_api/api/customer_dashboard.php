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

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

function dashboardOrder(array $row): array
{
    return [
        'id_transaksi' => (int) $row['id_transaksi'],
        'kode_transaksi' => $row['kode_transaksi'],
        'nama_layanan' => $row['nama_layanan'],
        'jumlah_layanan' => (int) $row['jumlah_layanan'],
        'total_harga' => (float) $row['total_harga'],
        'tanggal_masuk' => $row['tanggal_masuk'],
        'estimasi_selesai' => $row['estimasi_selesai'],
        'tanggal_selesai' => $row['tanggal_selesai'],
        'status_laundry' => $row['status_laundry'],
        'status_pembayaran' => $row['status_pembayaran'],
    ];
}

try {
    $pdo = getDatabaseConnection();
    $customer = requireRole($pdo, [1]);
    $customerId = $customer['id_user'];

    $baseSelect =
        "SELECT t.id_transaksi, t.kode_transaksi, t.total_harga,
                t.tanggal_masuk, t.estimasi_selesai, t.tanggal_selesai,
                t.status_laundry, t.status_pembayaran,
                (SELECT l.nama_layanan
                 FROM detail_transaksi d
                 INNER JOIN layanan l ON l.id_layanan = d.id_layanan
                 WHERE d.id_transaksi = t.id_transaksi
                 ORDER BY d.id_detail ASC
                 LIMIT 1) AS nama_layanan,
                (SELECT COUNT(*)
                 FROM detail_transaksi d
                 WHERE d.id_transaksi = t.id_transaksi) AS jumlah_layanan
         FROM transaksi t
         WHERE t.id_pelanggan = :id_pelanggan";

    $activeStatement = $pdo->prepare(
        $baseSelect .
        " AND t.status_laundry IN (
              'menunggu', 'dicuci', 'dikeringkan',
              'disetrika', 'dipacking', 'siap_diambil'
          )
          ORDER BY t.tanggal_masuk DESC, t.id_transaksi DESC
          LIMIT 1"
    );
    $activeStatement->execute(['id_pelanggan' => $customerId]);
    $activeRow = $activeStatement->fetch();

    $recentStatement = $pdo->prepare(
        $baseSelect .
        ' ORDER BY t.tanggal_masuk DESC, t.id_transaksi DESC LIMIT 2'
    );
    $recentStatement->execute(['id_pelanggan' => $customerId]);
    $recentRows = $recentStatement->fetchAll();

    respond(200, true, 'Dashboard pelanggan berhasil dimuat', [
        'active_order' => $activeRow ? dashboardOrder($activeRow) : null,
        'recent_orders' => array_map('dashboardOrder', $recentRows),
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
