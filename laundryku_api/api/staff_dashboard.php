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
    return is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1 ? (int) $value : null;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

$staffId = positiveInteger($_GET['id_user'] ?? null);
if ($staffId === null) {
    respond(400, false, 'ID staff tidak valid');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();
    // TODO: Replace client id_user with authenticated bearer token identity.
    $staffStatement = $pdo->prepare('SELECT level, status_akun FROM users WHERE id_user = :id LIMIT 1');
    $staffStatement->execute(['id' => $staffId]);
    $staff = $staffStatement->fetch();
    if (!$staff || (int) $staff['level'] !== 3 || $staff['status_akun'] !== 'aktif') {
        respond(403, false, 'Akun Staff Laundry tidak valid atau tidak aktif');
    }

    $counts = [
        'menunggu' => 0,
        'dicuci' => 0,
        'dikeringkan' => 0,
        'disetrika' => 0,
        'dipacking' => 0,
        'siap_diambil' => 0,
    ];
    $countStatement = $pdo->prepare(
        "SELECT status_laundry, COUNT(id_transaksi) AS jumlah
         FROM transaksi
         WHERE status_laundry IN ('menunggu','dicuci','dikeringkan','disetrika','dipacking','siap_diambil')
         GROUP BY status_laundry"
    );
    $countStatement->execute();
    foreach ($countStatement->fetchAll() as $row) {
        $counts[$row['status_laundry']] = (int) $row['jumlah'];
    }

    $nextStatement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi, customer.nama AS nama_pelanggan,
                t.status_laundry, t.estimasi_selesai,
                d.id_detail, l.nama_layanan, d.qty, l.satuan
         FROM transaksi t
         INNER JOIN users customer ON customer.id_user = t.id_pelanggan
         INNER JOIN detail_transaksi d ON d.id_transaksi = t.id_transaksi
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE t.id_transaksi = (
             SELECT oldest.id_transaksi
             FROM transaksi oldest
             WHERE oldest.status_laundry IN ('menunggu','dicuci','dikeringkan','disetrika','dipacking')
             ORDER BY oldest.tanggal_masuk ASC, oldest.id_transaksi ASC
             LIMIT 1
         )
         ORDER BY d.id_detail ASC"
    );
    $nextStatement->execute();
    $rows = $nextStatement->fetchAll();
    $nextJob = null;
    if ($rows !== []) {
        $first = $rows[0];
        $nextJob = [
            'id_transaksi' => (int) $first['id_transaksi'],
            'kode_transaksi' => $first['kode_transaksi'],
            'nama_pelanggan' => $first['nama_pelanggan'],
            'nama_layanan' => $first['nama_layanan'],
            'qty' => (float) $first['qty'],
            'satuan' => $first['satuan'],
            'status_laundry' => $first['status_laundry'],
            'estimasi_selesai' => $first['estimasi_selesai'],
            'details' => array_map(static fn(array $row): array => [
                'nama_layanan' => $row['nama_layanan'],
                'qty' => (float) $row['qty'],
                'satuan' => $row['satuan'],
            ], $rows),
        ];
    }

    respond(200, true, 'Dashboard Staff berhasil dimuat', [
        'summary' => $counts,
        'next_job' => $nextJob,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
