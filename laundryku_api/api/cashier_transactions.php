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

$cashierId = positiveInteger($_GET['id_user'] ?? null);
if ($cashierId === null) {
    respond(400, false, 'ID kasir tidak valid');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();

    // TODO: Replace client id_user with authenticated bearer token identity.
    $cashierStatement = $pdo->prepare(
        'SELECT id_user, level, status_akun
         FROM users
         WHERE id_user = :id_user
         LIMIT 1'
    );
    $cashierStatement->execute(['id_user' => $cashierId]);
    $cashier = $cashierStatement->fetch();

    if (!$cashier || (int) $cashier['level'] !== 2 || $cashier['status_akun'] !== 'aktif') {
        respond(403, false, 'Akun Kasir/Admin tidak valid atau tidak aktif');
    }

    $statement = $pdo->prepare(
        'SELECT t.id_transaksi, t.kode_transaksi, t.id_pelanggan,
                customer.nama AS nama_pelanggan, customer.no_hp,
                d.id_detail, l.nama_layanan, d.qty, l.satuan,
                t.total_harga, t.status_laundry, t.status_pembayaran,
                p.metode AS metode_pembayaran,
                p.payment_channel, p.status AS status_payment_record,
                t.tanggal_masuk, t.estimasi_selesai
         FROM transaksi t
         INNER JOIN users customer ON customer.id_user = t.id_pelanggan
         INNER JOIN detail_transaksi d ON d.id_transaksi = t.id_transaksi
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         LEFT JOIN pembayaran p ON p.id_transaksi = t.id_transaksi
         ORDER BY t.tanggal_masuk DESC, t.id_transaksi DESC, d.id_detail ASC'
    );
    $statement->execute();

    $transactions = [];
    foreach ($statement->fetchAll() as $row) {
        $transactionId = (int) $row['id_transaksi'];
        if (!isset($transactions[$transactionId])) {
            $transactions[$transactionId] = [
                'id_transaksi' => $transactionId,
                'kode_transaksi' => $row['kode_transaksi'],
                'id_pelanggan' => (int) $row['id_pelanggan'],
                'nama_pelanggan' => $row['nama_pelanggan'],
                'no_hp' => $row['no_hp'],
                'nama_layanan' => $row['nama_layanan'],
                'qty' => (float) $row['qty'],
                'satuan' => $row['satuan'],
                'total_harga' => (float) $row['total_harga'],
                'status_laundry' => $row['status_laundry'],
                'status_pembayaran' => $row['status_pembayaran'],
                'metode_pembayaran' => $row['metode_pembayaran'],
                'payment_channel' => $row['payment_channel'],
                'status_payment_record' => $row['status_payment_record'],
                'tanggal_masuk' => $row['tanggal_masuk'],
                'estimasi_selesai' => $row['estimasi_selesai'],
                'details' => [],
            ];
        }
        $transactions[$transactionId]['details'][] = [
            'nama_layanan' => $row['nama_layanan'],
            'qty' => (float) $row['qty'],
            'satuan' => $row['satuan'],
        ];
    }

    respond(200, true, 'Data transaksi berhasil diambil', array_values($transactions));
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
