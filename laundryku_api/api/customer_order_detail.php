<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, ?array $data = null): void
{
    http_response_code($status);
    $response = [
        'success' => $success,
        'message' => $message,
    ];
    if ($data !== null) {
        $response['data'] = $data;
    }
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
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
$transactionId = positiveInteger($_GET['id_transaksi'] ?? null);
if ($userId === null || $transactionId === null) {
    respond(400, false, 'Parameter detail pesanan tidak valid');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();

    // TODO: Setelah bearer token tersedia, ambil id_user dari token server-side
    // dan jangan mempercayai identitas yang dikirim client.
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

    $transactionStatement = $pdo->prepare(
        'SELECT id_transaksi, kode_transaksi, total_harga,
                tanggal_masuk, estimasi_selesai, tanggal_selesai,
                status_laundry, status_pembayaran
         FROM transaksi
         WHERE id_transaksi = :id_transaksi
           AND id_pelanggan = :id_pelanggan
         LIMIT 1'
    );
    $transactionStatement->execute([
        'id_transaksi' => $transactionId,
        'id_pelanggan' => $userId,
    ]);
    $transaction = $transactionStatement->fetch();

    if (!$transaction) {
        respond(404, false, 'Pesanan tidak ditemukan');
    }

    $detailStatement = $pdo->prepare(
        'SELECT d.id_detail, d.id_layanan, l.nama_layanan,
                d.qty, l.satuan, d.harga_satuan, d.subtotal
         FROM detail_transaksi d
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE d.id_transaksi = :id_transaksi
         ORDER BY d.id_detail ASC'
    );
    $detailStatement->execute(['id_transaksi' => $transactionId]);
    $details = array_map(
        static fn(array $detail): array => [
            'id_detail' => (int) $detail['id_detail'],
            'id_layanan' => (int) $detail['id_layanan'],
            'nama_layanan' => $detail['nama_layanan'],
            'qty' => (float) $detail['qty'],
            'satuan' => $detail['satuan'],
            'harga_satuan' => (float) $detail['harga_satuan'],
            'subtotal' => (float) $detail['subtotal'],
        ],
        $detailStatement->fetchAll()
    );

    if ($details === []) {
        respond(404, false, 'Detail pesanan tidak ditemukan');
    }

    $timelineStatement = $pdo->prepare(
        'SELECT status_laundry, waktu, catatan
         FROM riwayat_status
         WHERE id_transaksi = :id_transaksi
         ORDER BY waktu ASC, id_riwayat ASC'
    );
    $timelineStatement->execute(['id_transaksi' => $transactionId]);
    $timeline = array_map(
        static fn(array $entry): array => [
            'status_laundry' => $entry['status_laundry'],
            'waktu' => $entry['waktu'],
            'catatan' => $entry['catatan'],
        ],
        $timelineStatement->fetchAll()
    );

    $paymentStatement = $pdo->prepare(
        'SELECT id_pembayaran, metode, payment_channel, jumlah, status, tanggal_bayar
         FROM pembayaran
         WHERE id_transaksi = :id_transaksi
         LIMIT 1'
    );
    $paymentStatement->execute(['id_transaksi' => $transactionId]);
    $paymentRow = $paymentStatement->fetch();
    $payment = $paymentRow ? [
        'id_pembayaran' => (int) $paymentRow['id_pembayaran'],
        'metode' => $paymentRow['metode'],
        'payment_channel' => $paymentRow['payment_channel'],
        'jumlah' => (float) $paymentRow['jumlah'],
        'status' => $paymentRow['status'],
        'tanggal_bayar' => $paymentRow['tanggal_bayar'],
    ] : null;

    $primaryDetail = $details[0];
    respond(200, true, 'Detail pesanan berhasil diambil', [
        'id_transaksi' => (int) $transaction['id_transaksi'],
        'kode_transaksi' => $transaction['kode_transaksi'],
        'nama_layanan' => $primaryDetail['nama_layanan'],
        'qty' => $primaryDetail['qty'],
        'satuan' => $primaryDetail['satuan'],
        'harga_satuan' => $primaryDetail['harga_satuan'],
        'subtotal' => $primaryDetail['subtotal'],
        'total_harga' => (float) $transaction['total_harga'],
        'tanggal_masuk' => $transaction['tanggal_masuk'],
        'estimasi_selesai' => $transaction['estimasi_selesai'],
        'tanggal_selesai' => $transaction['tanggal_selesai'],
        'status_laundry' => $transaction['status_laundry'],
        'status_pembayaran' => $transaction['status_pembayaran'],
        'details' => $details,
        'timeline' => $timeline,
        'payment' => $payment,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
