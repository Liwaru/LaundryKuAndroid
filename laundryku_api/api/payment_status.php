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
    if (is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1) {
        return (int) $value;
    }
    return null;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}
$transactionId = positiveInteger($_GET['id_transaksi'] ?? null);
if ($transactionId === null) {
    respond(400, false, 'Parameter pembayaran tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';
require_once __DIR__ . '/../config/midtrans.php';
require_once __DIR__ . '/../config/qris_payment.php';

try {
    $pdo = getDatabaseConnection();
    $customer = requireRole($pdo, [1]);
    $statement = $pdo->prepare(
        'SELECT t.id_transaksi, t.status_pembayaran,
                p.metode, p.status AS payment_status, p.jumlah,
                p.gateway_order_id, p.gateway_qr_url, p.gateway_expiry_time
         FROM transaksi t
         LEFT JOIN pembayaran p ON p.id_transaksi = t.id_transaksi
         WHERE t.id_transaksi = :id_transaksi AND t.id_pelanggan = :id_pelanggan
         LIMIT 1'
    );
    $statement->execute([
        'id_transaksi' => $transactionId,
        'id_pelanggan' => $customer['id_user'],
    ]);
    $payment = $statement->fetch();
    if (!$payment) {
        respond(404, false, 'Pesanan tidak ditemukan');
    }

    if ($payment['metode'] === 'qris' && $payment['payment_status'] === 'menunggu' && $payment['gateway_order_id']) {
        try {
            $verified = midtransGetStatus($payment['gateway_order_id']);
            $updated = applyVerifiedQrisStatus($pdo, $verified);
            $payment['status_pembayaran'] = $updated['status_pembayaran'];
            $payment['payment_status'] = $updated['payment_status'];
        } catch (MidtransConfigurationException | MidtransRequestException $exception) {
            error_log($exception->getMessage());
        }
    }

    respond(200, true, 'Status pembayaran berhasil diambil', [
        'id_transaksi' => (int) $payment['id_transaksi'],
        'status_pembayaran' => $payment['status_pembayaran'],
        'payment_status' => $payment['payment_status'],
        'metode' => $payment['metode'],
        'total' => $payment['jumlah'] === null ? null : (float) $payment['jumlah'],
        'gateway_order_id' => $payment['gateway_order_id'],
        'qr_url' => $payment['gateway_qr_url'],
        'expiry_time' => $payment['gateway_expiry_time'],
    ]);
} catch (QrisPaymentException $exception) {
    error_log($exception->getMessage());
    respond($exception->responseStatus, false, 'Status pembayaran tidak dapat diproses');
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
