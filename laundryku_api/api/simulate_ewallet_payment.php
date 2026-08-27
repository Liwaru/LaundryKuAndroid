<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, ?array $data = null): never
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

function responseData(array $transaction, array $payment): array
{
    return [
        'id_transaksi' => (int) $transaction['id_transaksi'],
        'kode_transaksi' => $transaction['kode_transaksi'],
        'metode' => 'e_wallet',
        'payment_channel' => $payment['payment_channel'],
        'jumlah' => (float) $payment['jumlah'],
        'status' => 'berhasil',
        'status_pembayaran' => 'sudah_dibayar',
    ];
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, false, 'Metode request tidak diizinkan');
}

$payload = json_decode(file_get_contents('php://input'), true);
if (!is_array($payload) || array_diff(array_keys($payload), ['id_transaksi', 'payment_channel']) !== []) {
    respond(400, false, 'Data pembayaran tidak valid');
}

$transactionId = positiveInteger($payload['id_transaksi'] ?? null);
$channel = strtolower(trim((string) ($payload['payment_channel'] ?? '')));
$allowedChannels = ['gopay', 'dana', 'ovo', 'shopeepay'];
if ($transactionId === null || !in_array($channel, $allowedChannels, true)) {
    respond(400, false, 'Data pembayaran tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $user = requireRole($pdo, [1]);
    $pdo->beginTransaction();

    $transactionStatement = $pdo->prepare(
        'SELECT id_transaksi, kode_transaksi, total_harga, status_laundry, status_pembayaran
         FROM transaksi
         WHERE id_transaksi = :id_transaksi
           AND id_pelanggan = :id_pelanggan
         LIMIT 1
         FOR UPDATE'
    );
    $transactionStatement->execute([
        'id_transaksi' => $transactionId,
        'id_pelanggan' => $user['id_user'],
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

    $paymentStatement = $pdo->prepare(
        'SELECT id_pembayaran, metode, payment_channel, jumlah, status
         FROM pembayaran
         WHERE id_transaksi = :id_transaksi
         LIMIT 1
         FOR UPDATE'
    );
    $paymentStatement->execute(['id_transaksi' => $transactionId]);
    $payment = $paymentStatement->fetch();

    if ($transaction['status_pembayaran'] === 'sudah_dibayar') {
        if ($payment && $payment['metode'] === 'e_wallet' && $payment['status'] === 'berhasil' &&
            $payment['payment_channel'] === $channel) {
            $pdo->commit();
            respond(200, true, 'Pembayaran simulasi sudah berhasil', responseData($transaction, $payment));
        }
        $pdo->rollBack();
        respond(409, false, 'Transaksi sudah dibayar');
    }
    if ($transaction['status_pembayaran'] !== 'belum_dibayar') {
        $pdo->rollBack();
        respond(409, false, 'Status pembayaran transaksi tidak valid');
    }

    $amount = (float) $transaction['total_harga'];
    if ($amount <= 0) {
        throw new RuntimeException('Invalid transaction total');
    }
    if ($payment && $payment['status'] !== 'menunggu') {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran transaksi tidak dapat diproses');
    }

    if ($payment) {
        $savePayment = $pdo->prepare(
            "UPDATE pembayaran
             SET metode = 'e_wallet', payment_channel = :payment_channel,
                 jumlah = :jumlah, status = 'berhasil', tanggal_bayar = NOW(),
                 gateway_order_id = NULL, gateway_transaction_id = NULL,
                 gateway_qr_url = NULL, gateway_expiry_time = NULL,
                 updated_at = NOW()
             WHERE id_pembayaran = :id_pembayaran AND status = 'menunggu'"
        );
        $savePayment->execute([
            'payment_channel' => $channel,
            'jumlah' => $amount,
            'id_pembayaran' => $payment['id_pembayaran'],
        ]);
        if ($savePayment->rowCount() !== 1) {
            throw new RuntimeException('Atomic simulated payment update failed');
        }
        $paymentId = (int) $payment['id_pembayaran'];
    } else {
        $savePayment = $pdo->prepare(
            "INSERT INTO pembayaran (
                id_transaksi, metode, payment_channel, jumlah, status,
                gateway_order_id, gateway_transaction_id, gateway_qr_url,
                gateway_expiry_time, tanggal_bayar
             ) VALUES (
                :id_transaksi, 'e_wallet', :payment_channel, :jumlah, 'berhasil',
                NULL, NULL, NULL, NULL, NOW()
             )"
        );
        $savePayment->execute([
            'id_transaksi' => $transactionId,
            'payment_channel' => $channel,
            'jumlah' => $amount,
        ]);
        $paymentId = (int) $pdo->lastInsertId();
    }

    $transactionUpdate = $pdo->prepare(
        "UPDATE transaksi
         SET status_pembayaran = 'sudah_dibayar', updated_at = NOW()
         WHERE id_transaksi = :id_transaksi AND status_pembayaran = 'belum_dibayar'"
    );
    $transactionUpdate->execute(['id_transaksi' => $transactionId]);
    if ($transactionUpdate->rowCount() !== 1) {
        throw new RuntimeException('Atomic simulated transaction update failed');
    }

    $resultStatement = $pdo->prepare(
        'SELECT id_pembayaran, payment_channel, jumlah
         FROM pembayaran WHERE id_pembayaran = :id_pembayaran'
    );
    $resultStatement->execute(['id_pembayaran' => $paymentId]);
    $savedPayment = $resultStatement->fetch();
    if (!$savedPayment) {
        throw new RuntimeException('Simulated payment result missing');
    }

    $pdo->commit();
    respond(200, true, 'Pembayaran simulasi berhasil diproses', responseData($transaction, $savedPayment));
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
