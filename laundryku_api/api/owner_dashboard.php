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

$ownerId = positiveInteger($_GET['id_user'] ?? null);
if ($ownerId === null) {
    respond(400, false, 'ID owner tidak valid');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();

    // TODO: Replace client id_user with authenticated bearer-token identity.
    $ownerStatement = $pdo->prepare(
        'SELECT id_user, level, status_akun
         FROM users
         WHERE id_user = :id_user
         LIMIT 1'
    );
    $ownerStatement->execute(['id_user' => $ownerId]);
    $owner = $ownerStatement->fetch();
    if (!$owner || (int) $owner['level'] !== 4 || $owner['status_akun'] !== 'aktif') {
        respond(403, false, 'Akun Owner tidak valid atau tidak aktif');
    }

    $summaryStatement = $pdo->prepare(
        "SELECT
            COALESCE(SUM(tanggal_masuk >= CURDATE()
                AND tanggal_masuk < CURDATE() + INTERVAL 1 DAY), 0) AS transaksi_hari_ini,
            COALESCE(SUM(status_laundry IN
                ('menunggu','dicuci','dikeringkan','disetrika','dipacking','siap_diambil')), 0)
                AS pesanan_aktif
         FROM transaksi"
    );
    $summaryStatement->execute();
    $summary = $summaryStatement->fetch();

    $incomeStatement = $pdo->prepare(
        "SELECT COALESCE(SUM(jumlah), 0)
         FROM pembayaran
         WHERE status = 'berhasil'
           AND tanggal_bayar >= CURDATE()
           AND tanggal_bayar < CURDATE() + INTERVAL 1 DAY"
    );
    $incomeStatement->execute();

    $customerStatement = $pdo->prepare('SELECT COUNT(id_user) FROM users WHERE level = 1');
    $customerStatement->execute();

    $servicesStatement = $pdo->prepare(
        'SELECT service.id_layanan, service.nama_layanan, COUNT(detail.id_detail) AS jumlah_pesanan
         FROM detail_transaksi detail
         INNER JOIN layanan service ON service.id_layanan = detail.id_layanan
         INNER JOIN transaksi transaction_row ON transaction_row.id_transaksi = detail.id_transaksi
         GROUP BY service.id_layanan, service.nama_layanan
         ORDER BY jumlah_pesanan DESC, service.nama_layanan ASC
         LIMIT 4'
    );
    $servicesStatement->execute();
    $popularServices = array_map(static fn(array $row): array => [
        'id_layanan' => (int) $row['id_layanan'],
        'nama_layanan' => $row['nama_layanan'],
        'jumlah_pesanan' => (int) $row['jumlah_pesanan'],
    ], $servicesStatement->fetchAll());

    $statusStatement = $pdo->prepare(
        "SELECT
            COALESCE(SUM(status_laundry = 'menunggu'), 0) AS menunggu,
            COALESCE(SUM(status_laundry = 'dicuci'), 0) AS dicuci,
            COALESCE(SUM(status_laundry = 'dikeringkan'), 0) AS dikeringkan,
            COALESCE(SUM(status_laundry = 'disetrika'), 0) AS disetrika,
            COALESCE(SUM(status_laundry = 'dipacking'), 0) AS dipacking,
            COALESCE(SUM(status_laundry = 'siap_diambil'), 0) AS siap_diambil
         FROM transaksi"
    );
    $statusStatement->execute();
    $operational = $statusStatement->fetch();

    respond(200, true, 'Dashboard Owner berhasil dimuat', [
        'summary' => [
            'pendapatan_hari_ini' => (float) $incomeStatement->fetchColumn(),
            'transaksi_hari_ini' => (int) $summary['transaksi_hari_ini'],
            'pesanan_aktif' => (int) $summary['pesanan_aktif'],
            'total_pelanggan' => (int) $customerStatement->fetchColumn(),
        ],
        'popular_services' => $popularServices,
        'operational_status' => [
            'menunggu' => (int) $operational['menunggu'],
            'dicuci' => (int) $operational['dicuci'],
            'dikeringkan' => (int) $operational['dikeringkan'],
            'disetrika' => (int) $operational['disetrika'],
            'dipacking' => (int) $operational['dipacking'],
            'siap_diambil' => (int) $operational['siap_diambil'],
        ],
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
