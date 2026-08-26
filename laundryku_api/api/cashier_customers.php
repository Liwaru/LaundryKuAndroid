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

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();

    requireRole($pdo, [2]);

    $totalStatement = $pdo->prepare('SELECT COUNT(*) FROM users WHERE level = 1');
    $totalStatement->execute();
    $totalCustomers = (int) $totalStatement->fetchColumn();

    $customersStatement = $pdo->prepare(
        'SELECT u.id_user, u.nama, u.username, u.no_hp, u.status_akun,
                COUNT(t.id_transaksi) AS total_transaksi
         FROM users u
         LEFT JOIN transaksi t ON t.id_pelanggan = u.id_user
         WHERE u.level = 1
         GROUP BY u.id_user, u.nama, u.username, u.no_hp, u.status_akun
         ORDER BY u.nama ASC, u.id_user ASC'
    );
    $customersStatement->execute();
    $customers = array_map(static fn(array $row): array => [
        'id_user' => (int) $row['id_user'],
        'nama' => $row['nama'],
        'username' => $row['username'],
        'no_hp' => $row['no_hp'],
        'status_akun' => $row['status_akun'],
        'total_transaksi' => (int) $row['total_transaksi'],
    ], $customersStatement->fetchAll());

    respond(200, true, 'Data pelanggan berhasil dimuat', [
        'total_pelanggan' => $totalCustomers,
        'customers' => $customers,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
