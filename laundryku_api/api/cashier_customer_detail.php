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
    requireRole($pdo, [2]);

    $customerId = positiveInteger($_GET['id_pelanggan'] ?? null);
    if ($customerId === null) {
        respond(400, false, 'Pelanggan tidak valid');
    }

    $customerStatement = $pdo->prepare(
        'SELECT u.id_user, u.nama, u.username, u.no_hp, u.status_akun, u.created_at,
                (SELECT COUNT(*) FROM transaksi t
                 WHERE t.id_pelanggan = u.id_user) AS total_transaksi,
                (SELECT COALESCE(SUM(p.jumlah), 0)
                 FROM transaksi t
                 INNER JOIN pembayaran p ON p.id_transaksi = t.id_transaksi
                 WHERE t.id_pelanggan = u.id_user AND p.status = \'berhasil\') AS total_pengeluaran
         FROM users u
         WHERE u.id_user = :id_pelanggan AND u.level = 1
         LIMIT 1'
    );
    $customerStatement->execute(['id_pelanggan' => $customerId]);
    $customer = $customerStatement->fetch();
    if (!$customer) {
        respond(404, false, 'Pelanggan tidak ditemukan');
    }

    $recentStatement = $pdo->prepare(
        'SELECT id_transaksi, kode_transaksi, tanggal_masuk, total_harga,
                status_laundry, status_pembayaran
         FROM transaksi
         WHERE id_pelanggan = :id_pelanggan
         ORDER BY tanggal_masuk DESC, id_transaksi DESC
         LIMIT 3'
    );
    $recentStatement->execute(['id_pelanggan' => $customerId]);
    $recentTransactions = array_map(static fn(array $row): array => [
        'id_transaksi' => (int) $row['id_transaksi'],
        'kode_transaksi' => $row['kode_transaksi'],
        'tanggal_masuk' => $row['tanggal_masuk'],
        'total_harga' => (float) $row['total_harga'],
        'status_laundry' => $row['status_laundry'],
        'status_pembayaran' => $row['status_pembayaran'],
    ], $recentStatement->fetchAll());

    respond(200, true, 'Detail pelanggan berhasil dimuat', [
        'customer' => [
            'id_user' => (int) $customer['id_user'],
            'nama' => $customer['nama'],
            'username' => $customer['username'],
            'no_hp' => $customer['no_hp'],
            'status_akun' => $customer['status_akun'],
            'total_transaksi' => (int) $customer['total_transaksi'],
            'total_pengeluaran' => (float) $customer['total_pengeluaran'],
            'created_at' => $customer['created_at'],
        ],
        'recent_transactions' => $recentTransactions,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
