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

$cashierId = positiveInteger($payload['id_user'] ?? null);
$transactionId = positiveInteger($payload['id_transaksi'] ?? null);
if ($cashierId === null || $transactionId === null) {
    respond(400, false, 'Data konfirmasi pembayaran tidak valid');
}

require_once __DIR__ . '/../config/database.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $pdo->beginTransaction();

    // TODO: Replace client id_user with authenticated bearer token identity.
    $cashierStatement = $pdo->prepare(
        'SELECT id_user, level, status_akun
         FROM users
         WHERE id_user = :id_user
         LIMIT 1
         FOR UPDATE'
    );
    $cashierStatement->execute(['id_user' => $cashierId]);
    $cashier = $cashierStatement->fetch();
    if (!$cashier || (int) $cashier['level'] !== 2 || $cashier['status_akun'] !== 'aktif') {
        $pdo->rollBack();
        respond(403, false, 'Hanya Kasir/Admin aktif yang dapat mengonfirmasi pembayaran');
    }

    $transactionStatement = $pdo->prepare(
        'SELECT id_transaksi, total_harga, status_laundry, status_pembayaran, id_kasir
         FROM transaksi
         WHERE id_transaksi = :id_transaksi
         LIMIT 1
         FOR UPDATE'
    );
    $transactionStatement->execute(['id_transaksi' => $transactionId]);
    $transaction = $transactionStatement->fetch();
    if (!$transaction) {
        $pdo->rollBack();
        respond(404, false, 'Transaksi tidak ditemukan');
    }

    $paymentStatement = $pdo->prepare(
        'SELECT id_pembayaran, metode, jumlah, status, tanggal_bayar
         FROM pembayaran
         WHERE id_transaksi = :id_transaksi
         LIMIT 1
         FOR UPDATE'
    );
    $paymentStatement->execute(['id_transaksi' => $transactionId]);
    $payment = $paymentStatement->fetch();

    $amount = (float) $transaction['total_harga'];
    $paymentAmountMatches = $payment && abs((float) $payment['jumlah'] - $amount) < 0.005;

    if ($transaction['status_pembayaran'] === 'sudah_dibayar' &&
        $payment && $payment['metode'] === 'cash' && $payment['status'] === 'berhasil' &&
        $paymentAmountMatches) {
        $pdo->commit();
        respond(200, true, 'Pembayaran sudah dikonfirmasi', [
            'id_transaksi' => $transactionId,
            'status_pembayaran' => 'sudah_dibayar',
            'payment_status' => 'berhasil',
            'metode' => 'cash',
            'jumlah' => $amount,
        ]);
    }

    if ($transaction['status_laundry'] === 'dibatalkan') {
        $pdo->rollBack();
        respond(409, false, 'Transaksi yang dibatalkan tidak dapat dikonfirmasi');
    }
    if ($transaction['status_pembayaran'] !== 'belum_dibayar') {
        $pdo->rollBack();
        respond(409, false, 'Status pembayaran transaksi tidak dapat dikonfirmasi');
    }
    if (!$payment) {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran cash belum dipilih');
    }
    if ($payment['metode'] !== 'cash') {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran bukan metode cash');
    }
    if ($payment['status'] !== 'menunggu') {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran cash tidak sedang menunggu konfirmasi');
    }
    if (!$paymentAmountMatches || $amount <= 0) {
        $pdo->rollBack();
        respond(409, false, 'Jumlah pembayaran tidak sesuai dengan total transaksi');
    }

    $updatePayment = $pdo->prepare(
        "UPDATE pembayaran
         SET status = 'berhasil', tanggal_bayar = NOW(), updated_at = NOW()
         WHERE id_pembayaran = :id_pembayaran AND status = 'menunggu'"
    );
    $updatePayment->execute(['id_pembayaran' => $payment['id_pembayaran']]);

    $updateTransaction = $pdo->prepare(
        "UPDATE transaksi
         SET status_pembayaran = 'sudah_dibayar', id_kasir = :id_kasir, updated_at = NOW()
         WHERE id_transaksi = :id_transaksi AND status_pembayaran = 'belum_dibayar'"
    );
    $updateTransaction->execute([
        'id_kasir' => $cashierId,
        'id_transaksi' => $transactionId,
    ]);

    if ($updatePayment->rowCount() !== 1 || $updateTransaction->rowCount() !== 1) {
        throw new RuntimeException('Atomic cash confirmation failed');
    }

    $pdo->commit();
    respond(200, true, 'Pembayaran berhasil dikonfirmasi', [
        'id_transaksi' => $transactionId,
        'status_pembayaran' => 'sudah_dibayar',
        'payment_status' => 'berhasil',
        'metode' => 'cash',
        'jumlah' => $amount,
    ]);
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
