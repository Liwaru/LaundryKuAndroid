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
if (!is_array($payload) || array_diff(array_keys($payload), ['id_transaksi']) !== []) {
    respond(400, false, 'Request pembayaran QRIS tidak valid');
}
$transactionId = positiveInteger($payload['id_transaksi'] ?? null);
if ($transactionId === null) {
    respond(400, false, 'Data pembayaran QRIS tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';
require_once __DIR__ . '/../config/midtrans.php';
require_once __DIR__ . '/../config/qris_payment.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $customer = requireRole($pdo, [1]);
    $pdo->beginTransaction();

    $transactionStatement = $pdo->prepare(
        "SELECT t.id_transaksi, t.kode_transaksi, t.total_harga,
                t.status_laundry, t.status_pembayaran,
                u.nama, u.no_hp
         FROM transaksi t
         INNER JOIN users u ON u.id_user = t.id_pelanggan
         WHERE t.id_transaksi = :id_transaksi
           AND t.id_pelanggan = :id_pelanggan
         LIMIT 1
         FOR UPDATE"
    );
    $transactionStatement->execute([
        'id_transaksi' => $transactionId,
        'id_pelanggan' => $customer['id_user'],
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

    $amount = qrisAmount($transaction['total_harga']);
    if ((float) $amount <= 0 || !str_ends_with($amount, '.00')) {
        throw new QrisPaymentException('Total transaksi tidak dapat diproses sebagai QRIS');
    }

    $paymentStatement = $pdo->prepare(
        'SELECT id_pembayaran, metode, status, gateway_order_id,
                gateway_transaction_id, gateway_qr_url, gateway_expiry_time
         FROM pembayaran
         WHERE id_transaksi = :id_transaksi
         LIMIT 1
         FOR UPDATE'
    );
    $paymentStatement->execute(['id_transaksi' => $transactionId]);
    $payment = $paymentStatement->fetch();

    if ($payment && $payment['status'] === 'berhasil') {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran transaksi sudah berhasil');
    }
    if ($payment && $payment['status'] === 'menunggu' && $payment['metode'] === 'qris') {
        if (!$payment['gateway_order_id'] || !$payment['gateway_qr_url']) {
            $pdo->rollBack();
            respond(409, false, 'Pembayaran QRIS sedang diproses. Silakan cek status pembayaran');
        }
        $pdo->commit();
        respond(200, true, 'QRIS masih menunggu pembayaran', [
            'id_transaksi' => $transactionId,
            'gateway_order_id' => $payment['gateway_order_id'],
            'total' => (float) $amount,
            'qr_url' => $payment['gateway_qr_url'],
            'status' => 'menunggu',
            'expiry_time' => $payment['gateway_expiry_time'],
        ]);
    }
    if ($payment && $payment['status'] === 'menunggu' && $payment['metode'] !== 'cash') {
        $pdo->rollBack();
        respond(409, false, 'Metode pembayaran lain sedang diproses');
    }

    $gatewayOrderId = sprintf(
        'LDY-%d-%s-%s',
        $transactionId,
        gmdate('YmdHis'),
        bin2hex(random_bytes(4))
    );
    $charge = midtransCreateQris([
        'payment_type' => 'qris',
        'transaction_details' => [
            'order_id' => $gatewayOrderId,
            'gross_amount' => (int) $amount,
        ],
        'customer_details' => [
            'first_name' => $transaction['nama'],
            'phone' => $transaction['no_hp'],
        ],
        'item_details' => [[
            'id' => (string) $transaction['kode_transaksi'],
            'price' => (int) $amount,
            'quantity' => 1,
            'name' => 'Laundry ' . $transaction['kode_transaksi'],
        ]],
    ]);

    $qrUrl = midtransQrisUrl(is_array($charge['actions'] ?? null) ? $charge['actions'] : []);
    $gatewayTransactionId = trim((string) ($charge['transaction_id'] ?? ''));
    if (($charge['status_code'] ?? null) !== '201' ||
        ($charge['transaction_status'] ?? null) !== 'pending' ||
        ($charge['payment_type'] ?? null) !== 'qris' ||
        ($charge['order_id'] ?? null) !== $gatewayOrderId ||
        qrisAmount($charge['gross_amount'] ?? null) !== $amount ||
        $gatewayTransactionId === '' || $qrUrl === null) {
        throw new MidtransRequestException('Midtrans returned incomplete QRIS data');
    }
    $expiryTime = isset($charge['expiry_time']) && is_string($charge['expiry_time'])
        ? $charge['expiry_time']
        : null;

    if ($payment) {
        $save = $pdo->prepare(
            "UPDATE pembayaran
             SET metode = 'qris', payment_channel = 'qris', jumlah = :jumlah,
                 status = 'menunggu', gateway_order_id = :gateway_order_id,
                 gateway_transaction_id = :gateway_transaction_id,
                 gateway_qr_url = :gateway_qr_url, gateway_expiry_time = :gateway_expiry_time,
                 tanggal_bayar = NULL, updated_at = NOW()
             WHERE id_pembayaran = :id_pembayaran AND status <> 'berhasil'"
        );
        $save->execute([
            'jumlah' => $amount,
            'gateway_order_id' => $gatewayOrderId,
            'gateway_transaction_id' => $gatewayTransactionId,
            'gateway_qr_url' => $qrUrl,
            'gateway_expiry_time' => $expiryTime,
            'id_pembayaran' => $payment['id_pembayaran'],
        ]);
    } else {
        $save = $pdo->prepare(
            "INSERT INTO pembayaran (
                id_transaksi, metode, payment_channel, jumlah, status,
                gateway_order_id, gateway_transaction_id, gateway_qr_url,
                gateway_expiry_time, tanggal_bayar
             ) VALUES (
                :id_transaksi, 'qris', 'qris', :jumlah, 'menunggu',
                :gateway_order_id, :gateway_transaction_id, :gateway_qr_url,
                :gateway_expiry_time, NULL
             )"
        );
        $save->execute([
            'id_transaksi' => $transactionId,
            'jumlah' => $amount,
            'gateway_order_id' => $gatewayOrderId,
            'gateway_transaction_id' => $gatewayTransactionId,
            'gateway_qr_url' => $qrUrl,
            'gateway_expiry_time' => $expiryTime,
        ]);
    }

    $pdo->commit();
    respond(201, true, 'QRIS berhasil dibuat', [
        'id_transaksi' => $transactionId,
        'gateway_order_id' => $gatewayOrderId,
        'total' => (float) $amount,
        'qr_url' => $qrUrl,
        'status' => 'menunggu',
        'expiry_time' => $expiryTime,
    ]);
} catch (MidtransConfigurationException $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(503, false, 'Konfigurasi QRIS belum tersedia');
} catch (MidtransRequestException $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(502, false, 'Layanan QRIS sedang tidak tersedia');
} catch (QrisPaymentException $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    respond($exception->responseStatus, false, $exception->getMessage());
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
