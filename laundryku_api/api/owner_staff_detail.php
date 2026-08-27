<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, ?array $data = null): never
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

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();
    requireRole($pdo, [4]);

    $staffId = positiveInteger($_GET['id_staff'] ?? null);
    if ($staffId === null) {
        respond(400, false, 'Staff tidak valid');
    }

    $statement = $pdo->prepare(
        'SELECT id_user, nama, username, no_hp, level, status_akun, created_at
         FROM users
         WHERE id_user = :id_staff AND level IN (2, 3)
         LIMIT 1'
    );
    $statement->execute(['id_staff' => $staffId]);
    $staff = $statement->fetch();
    if (!$staff) {
        respond(404, false, 'Staff tidak ditemukan');
    }

    respond(200, true, 'Detail staff berhasil dimuat', [
        'staff' => [
            'id_user' => (int) $staff['id_user'],
            'nama' => $staff['nama'],
            'username' => $staff['username'],
            'no_hp' => $staff['no_hp'],
            'level' => (int) $staff['level'],
            'status_akun' => $staff['status_akun'],
            'created_at' => $staff['created_at'],
        ],
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
