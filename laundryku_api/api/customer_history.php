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

$userId = positiveInteger($_GET['id_user'] ?? null);
if ($userId === null) {
    respond(400, false, 'ID pelanggan tidak valid');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();

    // TODO: Setelah bearer token tersedia, ambil identitas customer dari token,
    // bukan dari parameter id_user yang dikirim client.
    $userStatement = $pdo->prepare(
        'SELECT id_user, level, status_akun
         FROM users
         WHERE id_user = :id_user
         LIMIT 1'
    );
    $userStatement->execute(['id_user' => $userId]);
    $user = $userStatement->fetch();

    if (!$user || (int) $user['level'] !== 1 || $user['status_akun'] !== 'aktif') {
        respond(403, false, 'Akun pelanggan tidak valid atau tidak aktif');
    }

    $historyStatement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi,
                l.nama_layanan, d.qty, l.satuan,
                t.total_harga, t.tanggal_masuk, t.tanggal_selesai,
                t.status_laundry, t.status_pembayaran
         FROM transaksi t
         INNER JOIN detail_transaksi d ON d.id_transaksi = t.id_transaksi
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE t.id_pelanggan = :id_pelanggan
           AND t.status_laundry IN ('selesai', 'dibatalkan')
         ORDER BY COALESCE(t.tanggal_selesai, t.tanggal_masuk) DESC,
                  t.id_transaksi DESC"
    );
    $historyStatement->execute(['id_pelanggan' => $userId]);

    $history = array_map(
        static fn(array $item): array => [
            'id_transaksi' => (int) $item['id_transaksi'],
            'kode_transaksi' => $item['kode_transaksi'],
            'nama_layanan' => $item['nama_layanan'],
            'qty' => (float) $item['qty'],
            'satuan' => $item['satuan'],
            'total_harga' => (float) $item['total_harga'],
            'tanggal_masuk' => $item['tanggal_masuk'],
            'tanggal_selesai' => $item['tanggal_selesai'],
            'status_laundry' => $item['status_laundry'],
            'status_pembayaran' => $item['status_pembayaran'],
        ],
        $historyStatement->fetchAll()
    );

    respond(200, true, 'Riwayat pelanggan berhasil diambil', $history);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
