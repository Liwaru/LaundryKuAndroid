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

function nextStatus(string $current, bool $requiresIron): ?string
{
    return match ($current) {
        'menunggu' => 'dicuci',
        'dicuci' => 'dikeringkan',
        'dikeringkan' => $requiresIron ? 'disetrika' : 'dipacking',
        'disetrika' => 'dipacking',
        'dipacking' => 'siap_diambil',
        default => null,
    };
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

    $statement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi, customer.nama AS nama_pelanggan,
                t.estimasi_selesai, t.status_laundry, d.id_detail,
                l.nama_layanan, d.qty, l.satuan, l.perlu_setrika,
                (SELECT COALESCE(MAX(l2.perlu_setrika), 0)
                 FROM detail_transaksi d2
                 INNER JOIN layanan l2 ON l2.id_layanan = d2.id_layanan
                 WHERE d2.id_transaksi = t.id_transaksi) AS transaksi_perlu_setrika
         FROM transaksi t
         INNER JOIN users customer ON customer.id_user = t.id_pelanggan
         INNER JOIN detail_transaksi d ON d.id_transaksi = t.id_transaksi
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE t.status_laundry IN (
             'menunggu', 'dicuci', 'dikeringkan', 'disetrika', 'dipacking', 'siap_diambil'
         )
         ORDER BY t.tanggal_masuk ASC, t.id_transaksi ASC, d.id_detail ASC"
    );
    $statement->execute();

    $jobs = [];
    foreach ($statement->fetchAll() as $row) {
        $id = (int) $row['id_transaksi'];
        $requiresIron = (int) $row['transaksi_perlu_setrika'] === 1;
        if (!isset($jobs[$id])) {
            $jobs[$id] = [
                'id_transaksi' => $id,
                'kode_transaksi' => $row['kode_transaksi'],
                'nama_pelanggan' => $row['nama_pelanggan'],
                'nama_layanan' => $row['nama_layanan'],
                'qty' => (float) $row['qty'],
                'satuan' => $row['satuan'],
                'estimasi_selesai' => $row['estimasi_selesai'],
                'status_laundry' => $row['status_laundry'],
                'perlu_setrika' => $requiresIron,
                'next_status' => nextStatus($row['status_laundry'], $requiresIron),
                'details' => [],
            ];
        }
        $jobs[$id]['details'][] = [
            'nama_layanan' => $row['nama_layanan'],
            'qty' => (float) $row['qty'],
            'satuan' => $row['satuan'],
            'perlu_setrika' => (int) $row['perlu_setrika'] === 1,
        ];
    }

    respond(200, true, 'Data pekerjaan berhasil diambil', array_values($jobs));
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
