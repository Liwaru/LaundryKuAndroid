<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, array $data = []): void
{
    http_response_code($status);
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data' => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

function positiveInteger(mixed $value): ?int
{
    if (is_int($value)) {
        return $value > 0 ? $value : null;
    }
    if (is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1) {
        return (int) $value;
    }
    return null;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();

    $user = requireRole($pdo, [1]);
    $userId = $user['id_user'];

    $ordersStatement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi, t.id_pelanggan,
                l.nama_layanan, d.qty, l.satuan, d.harga_satuan, d.subtotal,
                t.total_harga, t.tanggal_masuk, t.estimasi_selesai,
                t.status_laundry, t.status_pembayaran
         FROM transaksi t
         INNER JOIN detail_transaksi d ON d.id_transaksi = t.id_transaksi
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE t.id_pelanggan = :id_pelanggan
           AND t.status_laundry IN (
               'menunggu', 'dicuci', 'dikeringkan',
               'disetrika', 'dipacking', 'siap_diambil'
           )
         ORDER BY t.tanggal_masuk DESC, t.id_transaksi DESC"
    );
    $ordersStatement->execute(['id_pelanggan' => $userId]);

    $orders = array_map(
        static fn(array $order): array => [
            'id_transaksi' => (int) $order['id_transaksi'],
            'kode_transaksi' => $order['kode_transaksi'],
            'id_pelanggan' => (int) $order['id_pelanggan'],
            'nama_layanan' => $order['nama_layanan'],
            'qty' => (float) $order['qty'],
            'satuan' => $order['satuan'],
            'harga_satuan' => (float) $order['harga_satuan'],
            'subtotal' => (float) $order['subtotal'],
            'total_harga' => (float) $order['total_harga'],
            'tanggal_masuk' => $order['tanggal_masuk'],
            'estimasi_selesai' => $order['estimasi_selesai'],
            'status_laundry' => $order['status_laundry'],
            'status_pembayaran' => $order['status_pembayaran'],
        ],
        $ordersStatement->fetchAll()
    );

    respond(200, true, 'Data pesanan berhasil diambil', $orders);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
