<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, array $data = []): void
{
    http_response_code($status);
    echo json_encode(['success' => $success, 'message' => $message, 'data' => $data], JSON_UNESCAPED_UNICODE);
    exit;
}

function positiveInteger(mixed $value): ?int
{
    return is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1 ? (int) $value : null;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();
    requireRole($pdo, [3]);

    $statement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi, customer.nama AS nama_pelanggan,
                t.status_laundry, d.id_detail, l.nama_layanan, d.qty, l.satuan,
                COALESCE(
                    (SELECT MAX(r.waktu) FROM riwayat_status r WHERE r.id_transaksi = t.id_transaksi),
                    t.tanggal_selesai, t.updated_at, t.tanggal_masuk
                ) AS terakhir_diperbarui
         FROM transaksi t
         INNER JOIN users customer ON customer.id_user = t.id_pelanggan
         INNER JOIN detail_transaksi d ON d.id_transaksi = t.id_transaksi
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE t.status_laundry IN ('siap_diambil', 'selesai', 'dibatalkan')
         ORDER BY terakhir_diperbarui DESC, t.id_transaksi DESC, d.id_detail ASC"
    );
    $statement->execute();

    $history = [];
    foreach ($statement->fetchAll() as $row) {
        $id = (int) $row['id_transaksi'];
        if (!isset($history[$id])) {
            $history[$id] = [
                'id_transaksi' => $id,
                'kode_transaksi' => $row['kode_transaksi'],
                'nama_pelanggan' => $row['nama_pelanggan'],
                'nama_layanan' => $row['nama_layanan'],
                'qty' => (float) $row['qty'],
                'satuan' => $row['satuan'],
                'status_laundry' => $row['status_laundry'],
                'terakhir_diperbarui' => $row['terakhir_diperbarui'],
                'details' => [],
            ];
        }
        $history[$id]['details'][] = [
            'nama_layanan' => $row['nama_layanan'],
            'qty' => (float) $row['qty'],
            'satuan' => $row['satuan'],
        ];
    }

    respond(200, true, 'Riwayat pekerjaan berhasil diambil', array_values($history));
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
