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
        'SELECT id_user, level, status_akun FROM users WHERE id_user = :id_user LIMIT 1'
    );
    $ownerStatement->execute(['id_user' => $ownerId]);
    $owner = $ownerStatement->fetch();
    if (!$owner || (int) $owner['level'] !== 4 || $owner['status_akun'] !== 'aktif') {
        respond(403, false, 'Akun Owner tidak valid atau tidak aktif');
    }

    $summaryStatement = $pdo->prepare(
        "SELECT
            COALESCE(SUM(level = 2), 0) AS jumlah_kasir,
            COALESCE(SUM(level = 3), 0) AS jumlah_staff_laundry,
            COALESCE(SUM(level IN (2, 3) AND status_akun = 'aktif'), 0) AS jumlah_staff_aktif
         FROM users
         WHERE level IN (2, 3)"
    );
    $summaryStatement->execute();
    $summary = $summaryStatement->fetch();

    $staffStatement = $pdo->prepare(
        'SELECT id_user, nama, username, no_hp, level, status_akun, created_at
         FROM users
         WHERE level IN (2, 3)
         ORDER BY level ASC, nama ASC, id_user ASC'
    );
    $staffStatement->execute();
    $staff = array_map(static fn(array $row): array => [
        'id_user' => (int) $row['id_user'],
        'nama' => $row['nama'],
        'username' => $row['username'],
        'no_hp' => $row['no_hp'],
        'level' => (int) $row['level'],
        'status_akun' => $row['status_akun'],
        'created_at' => $row['created_at'],
    ], $staffStatement->fetchAll());

    respond(200, true, 'Data Staff berhasil dimuat', [
        'summary' => [
            'jumlah_kasir' => (int) $summary['jumlah_kasir'],
            'jumlah_staff_laundry' => (int) $summary['jumlah_staff_laundry'],
            'jumlah_staff_aktif' => (int) $summary['jumlah_staff_aktif'],
        ],
        'staff' => $staff,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
