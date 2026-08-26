<?php
declare(strict_types=1);

final class QrisPaymentException extends RuntimeException
{
    public function __construct(string $message, public readonly int $responseStatus = 409)
    {
        parent::__construct($message);
    }
}
function qrisAmount(mixed $value): string
{
    if (!is_numeric($value)) {
        throw new QrisPaymentException('Jumlah pembayaran tidak valid');
    }
    return number_format((float) $value, 2, '.', '');
}

function qrisGatewayState(array $status): string
{
    $transactionStatus = strtolower((string) ($status['transaction_status'] ?? ''));
    $statusCode = (string) ($status['status_code'] ?? '');
    $fraudStatus = strtolower((string) ($status['fraud_status'] ?? 'accept'));

    if (in_array($transactionStatus, ['settlement', 'capture'], true) &&
        $statusCode === '200' && $fraudStatus === 'accept') {
        return 'berhasil';
    }
    if ($transactionStatus === 'pending') {
        return 'menunggu';
    }
    if (in_array($transactionStatus, ['deny', 'cancel', 'expire', 'failure'], true)) {
        return 'gagal';
    }
    throw new QrisPaymentException('Status pembayaran gateway tidak dikenali', 422);
}

function qrisSettlementTime(array $status): ?string
{
    $value = (string) ($status['settlement_time'] ?? $status['transaction_time'] ?? '');
    if ($value === '') {
        return null;
    }
    $date = DateTimeImmutable::createFromFormat('!Y-m-d H:i:s', $value);
    return $date && $date->format('Y-m-d H:i:s') === $value ? $value : null;
}

function applyVerifiedQrisStatus(PDO $pdo, array $status): array
{
    $orderId = trim((string) ($status['order_id'] ?? ''));
    $transactionId = trim((string) ($status['transaction_id'] ?? ''));
    if ($orderId === '' || $transactionId === '' || ($status['payment_type'] ?? null) !== 'qris') {
        throw new QrisPaymentException('Data status Midtrans tidak valid', 422);
    }
    $gatewayState = qrisGatewayState($status);
    $gatewayAmount = qrisAmount($status['gross_amount'] ?? null);

    $pdo->beginTransaction();
    try {
        $statement = $pdo->prepare(
            "SELECT p.id_pembayaran, p.id_transaksi, p.metode, p.jumlah, p.status,
                    t.total_harga, t.status_pembayaran, t.status_laundry
             FROM pembayaran p
             INNER JOIN transaksi t ON t.id_transaksi = p.id_transaksi
             WHERE p.gateway_order_id = :gateway_order_id
             LIMIT 1
             FOR UPDATE"
        );
        $statement->execute(['gateway_order_id' => $orderId]);
        $payment = $statement->fetch();
        if (!$payment || $payment['metode'] !== 'qris') {
            throw new QrisPaymentException('Pembayaran QRIS tidak ditemukan', 404);
        }
        if ($gatewayAmount !== qrisAmount($payment['jumlah']) ||
            $gatewayAmount !== qrisAmount($payment['total_harga'])) {
            throw new QrisPaymentException('Jumlah pembayaran Midtrans tidak sesuai');
        }

        if ($payment['status'] !== 'berhasil') {
            if ($gatewayState === 'berhasil') {
                if ($payment['status_pembayaran'] !== 'belum_dibayar') {
                    throw new QrisPaymentException('Status transaksi tidak dapat diperbarui');
                }
                $paymentUpdate = $pdo->prepare(
                    "UPDATE pembayaran
                     SET status = 'berhasil', gateway_transaction_id = :gateway_transaction_id,
                         tanggal_bayar = COALESCE(:tanggal_bayar, NOW()), updated_at = NOW()
                     WHERE id_pembayaran = :id_pembayaran AND status <> 'berhasil'"
                );
                $paymentUpdate->execute([
                    'gateway_transaction_id' => $transactionId,
                    'tanggal_bayar' => qrisSettlementTime($status),
                    'id_pembayaran' => $payment['id_pembayaran'],
                ]);
                $transactionUpdate = $pdo->prepare(
                    "UPDATE transaksi SET status_pembayaran = 'sudah_dibayar', updated_at = NOW()
                     WHERE id_transaksi = :id_transaksi AND status_pembayaran = 'belum_dibayar'"
                );
                $transactionUpdate->execute(['id_transaksi' => $payment['id_transaksi']]);
                if ($paymentUpdate->rowCount() !== 1 || $transactionUpdate->rowCount() !== 1) {
                    throw new RuntimeException('Atomic QRIS settlement failed');
                }
            } elseif ($gatewayState === 'gagal') {
                $update = $pdo->prepare(
                    "UPDATE pembayaran
                     SET status = 'gagal', gateway_transaction_id = :gateway_transaction_id,
                         tanggal_bayar = NULL, updated_at = NOW()
                     WHERE id_pembayaran = :id_pembayaran AND status <> 'berhasil'"
                );
                $update->execute([
                    'gateway_transaction_id' => $transactionId,
                    'id_pembayaran' => $payment['id_pembayaran'],
                ]);
            } else {
                $update = $pdo->prepare(
                    "UPDATE pembayaran SET gateway_transaction_id = :gateway_transaction_id, updated_at = NOW()
                     WHERE id_pembayaran = :id_pembayaran AND status = 'menunggu'"
                );
                $update->execute([
                    'gateway_transaction_id' => $transactionId,
                    'id_pembayaran' => $payment['id_pembayaran'],
                ]);
            }
        }

        $result = $pdo->prepare(
            'SELECT p.id_transaksi, p.metode, p.status AS payment_status,
                    p.jumlah, p.gateway_order_id, p.gateway_expiry_time,
                    t.status_pembayaran
             FROM pembayaran p
             INNER JOIN transaksi t ON t.id_transaksi = p.id_transaksi
             WHERE p.id_pembayaran = :id_pembayaran'
        );
        $result->execute(['id_pembayaran' => $payment['id_pembayaran']]);
        $row = $result->fetch();
        $pdo->commit();
        return [
            'id_transaksi' => (int) $row['id_transaksi'],
            'status_pembayaran' => $row['status_pembayaran'],
            'payment_status' => $row['payment_status'],
            'metode' => $row['metode'],
            'total' => (float) $row['jumlah'],
            'gateway_order_id' => $row['gateway_order_id'],
            'expiry_time' => $row['gateway_expiry_time'],
        ];
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        throw $exception;
    }
}
