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

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, false, 'Metode request tidak diizinkan');
}

$payload = json_decode(file_get_contents('php://input'), true);
if (!is_array($payload)) {
    respond(400, false, 'Request JSON tidak valid');
}

$userId = positiveInteger($payload['id_user'] ?? null);
$transactionId = positiveInteger($payload['id_transaksi'] ?? null);
if ($userId === null || $transactionId === null) {
    respond(400, false, 'Data pembayaran tidak valid');
}

require_once __DIR__ . '/../config/database.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $pdo->beginTransaction();

    // TODO: Replace id_user client trust with authenticated bearer token.
    $userStatement = $pdo->prepare(
        'SELECT id_user, level, status_akun
         FROM users
         WHERE id_user = :id_user
         LIMIT 1
         FOR UPDATE'
    );
    $userStatement->execute(['id_user' => $userId]);
    $user = $userStatement->fetch();

    if (!$user || (int) $user['level'] !== 1 || $user['status_akun'] !== 'aktif') {
        $pdo->rollBack();
        respond(403, false, 'Akun pelanggan tidak valid atau tidak aktif');
    }

    $transactionStatement = $pdo->prepare(
        'SELECT id_transaksi, total_harga, status_laundry, status_pembayaran
         FROM transaksi
         WHERE id_transaksi = :id_transaksi
           AND id_pelanggan = :id_pelanggan
         LIMIT 1
         FOR UPDATE'
    );
    $transactionStatement->execute([
        'id_transaksi' => $transactionId,
        'id_pelanggan' => $userId,
    ]);
    $transaction = $transactionStatement->fetch();

    if (!$transaction) {
        $pdo->rollBack();
        respond(404, false, 'Pesanan tidak ditemukan');
    }
    if ($transaction['status_laundry'] === 'dibatalkan') {
        $pdo->rollBack();
        respond(409, false, 'Pesanan yang dibatalkan tidak dapat dibayar');
    }
    if ($transaction['status_pembayaran'] !== 'belum_dibayar') {
        $pdo->rollBack();
        respond(409, false, 'Pesanan sudah dibayar');
    }

    $amount = (float) $transaction['total_harga'];
    if ($amount <= 0) {
        throw new RuntimeException('Invalid transaction total');
    }

    $paymentStatement = $pdo->prepare(
        'SELECT id_pembayaran, metode, payment_channel, jumlah, status
         FROM pembayaran
         WHERE id_transaksi = :id_transaksi
         LIMIT 1
         FOR UPDATE'
    );
    $paymentStatement->execute(['id_transaksi' => $transactionId]);
    $payment = $paymentStatement->fetch();

    if ($payment && $payment['metode'] === 'cash' && $payment['status'] === 'menunggu') {
        $pdo->commit();
        respond(200, true, 'Pembayaran cash sudah dipilih', [
            'id_pembayaran' => (int) $payment['id_pembayaran'],
            'metode' => 'cash',
            'payment_channel' => null,
            'jumlah' => (float) $payment['jumlah'],
            'status' => 'menunggu',
            'status_pembayaran' => $transaction['status_pembayaran'],
        ]);
    }

    if ($payment && $payment['status'] === 'berhasil') {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran transaksi sudah berhasil');
    }
    if ($payment && $payment['status'] === 'menunggu') {
        $pdo->rollBack();
        respond(409, false, 'Metode pembayaran lain sedang diproses');
    }

    if ($payment) {
        $savePayment = $pdo->prepare(
            "UPDATE pembayaran
             SET metode = 'cash', payment_channel = NULL,
                 jumlah = :jumlah, status = 'menunggu',
                 gateway_order_id = NULL, gateway_transaction_id = NULL,
                 tanggal_bayar = NULL
             WHERE id_pembayaran = :id_pembayaran"
        );
        $savePayment->execute([
            'jumlah' => $amount,
            'id_pembayaran' => $payment['id_pembayaran'],
        ]);
        $paymentId = (int) $payment['id_pembayaran'];
    } else {
        $savePayment = $pdo->prepare(
            "INSERT INTO pembayaran (
                id_transaksi, metode, payment_channel, jumlah, status,
                gateway_order_id, gateway_transaction_id, tanggal_bayar
             ) VALUES (
                :id_transaksi, 'cash', NULL, :jumlah, 'menunggu',
                NULL, NULL, NULL
             )"
        );
        $savePayment->execute([
            'id_transaksi' => $transactionId,
            'jumlah' => $amount,
        ]);
        $paymentId = (int) $pdo->lastInsertId();
    }

    $pdo->commit();
    respond(200, true, 'Pembayaran cash berhasil dipilih', [
        'id_pembayaran' => $paymentId,
        'metode' => 'cash',
        'payment_channel' => null,
        'jumlah' => $amount,
        'status' => 'menunggu',
        'status_pembayaran' => $transaction['status_pembayaran'],
    ]);
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
