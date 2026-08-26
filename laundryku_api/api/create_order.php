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

function uniqueTransactionCode(): string
{
    return 'LDY' . date('Ymd') . strtoupper(bin2hex(random_bytes(4)));
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
$serviceId = positiveInteger($payload['id_layanan'] ?? null);
$rawQuantity = $payload['qty'] ?? null;

if ($userId === null || $serviceId === null || !is_numeric($rawQuantity)) {
    respond(400, false, 'Data pesanan tidak valid');
}

$quantity = (float) $rawQuantity;
if (!is_finite($quantity) || $quantity <= 0 || $quantity > 99999) {
    respond(400, false, 'Quantity harus lebih dari 0');
}

if (abs($quantity - round($quantity, 2)) > 0.000001) {
    respond(400, false, 'Quantity maksimal memiliki 2 angka desimal');
}

require_once __DIR__ . '/../config/database.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $pdo->beginTransaction();

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

    $serviceStatement = $pdo->prepare(
        'SELECT id_layanan, harga, satuan, minimal_order, estimasi_hari
         FROM layanan
         WHERE id_layanan = :id_layanan AND status = :status
         LIMIT 1
         FOR UPDATE'
    );
    $serviceStatement->execute([
        'id_layanan' => $serviceId,
        'status' => 'aktif',
    ]);
    $service = $serviceStatement->fetch();

    if (!$service) {
        $pdo->rollBack();
        respond(400, false, 'Layanan tidak tersedia');
    }

    if ($service['satuan'] === 'pcs' && abs($quantity - round($quantity)) > 0.000001) {
        $pdo->rollBack();
        respond(400, false, 'Quantity layanan pcs harus berupa bilangan bulat');
    }

    $actualQuantity = round($quantity, 2);
    $minimumOrder = (float) $service['minimal_order'];
    $billedQuantity = max($actualQuantity, $minimumOrder);
    $unitPrice = (float) $service['harga'];
    $subtotal = round($billedQuantity * $unitPrice, 2);
    $estimateDays = (int) $service['estimasi_hari'];

    $insertTransaction = $pdo->prepare(
        'INSERT INTO transaksi (
            kode_transaksi, id_pelanggan, id_kasir, tanggal_masuk,
            estimasi_selesai, total_harga, status_laundry, status_pembayaran
         ) VALUES (
            :kode_transaksi, :id_pelanggan, NULL, NOW(),
            DATE_ADD(NOW(), INTERVAL :estimasi_hari DAY), :total_harga,
            :status_laundry, :status_pembayaran
         )'
    );

    $transactionCode = '';
    $inserted = false;
    for ($attempt = 0; $attempt < 3 && !$inserted; $attempt++) {
        $transactionCode = uniqueTransactionCode();
        try {
            $insertTransaction->execute([
                'kode_transaksi' => $transactionCode,
                'id_pelanggan' => $userId,
                'estimasi_hari' => $estimateDays,
                'total_harga' => $subtotal,
                'status_laundry' => 'menunggu',
                'status_pembayaran' => 'belum_dibayar',
            ]);
            $inserted = true;
        } catch (PDOException $exception) {
            if ($exception->getCode() !== '23000') {
                throw $exception;
            }
        }
    }
    if (!$inserted) {
        throw new RuntimeException('Unable to generate a unique transaction code');
    }

    $transactionId = (int) $pdo->lastInsertId();

    $detailStatement = $pdo->prepare(
        'INSERT INTO detail_transaksi (
            id_transaksi, id_layanan, qty, harga_satuan, subtotal
         ) VALUES (
            :id_transaksi, :id_layanan, :qty, :harga_satuan, :subtotal
         )'
    );
    $detailStatement->execute([
        'id_transaksi' => $transactionId,
        'id_layanan' => $serviceId,
        'qty' => $actualQuantity,
        'harga_satuan' => $unitPrice,
        'subtotal' => $subtotal,
    ]);

    $historyStatement = $pdo->prepare(
        'INSERT INTO riwayat_status (
            id_transaksi, id_user, status_laundry, waktu, catatan
         ) VALUES (
            :id_transaksi, :id_user, :status_laundry, NOW(), :catatan
         )'
    );
    $historyStatement->execute([
        'id_transaksi' => $transactionId,
        'id_user' => $userId,
        'status_laundry' => 'menunggu',
        'catatan' => 'Pesanan dibuat',
    ]);

    $resultStatement = $pdo->prepare(
        'SELECT estimasi_selesai
         FROM transaksi
         WHERE id_transaksi = :id_transaksi'
    );
    $resultStatement->execute(['id_transaksi' => $transactionId]);
    $estimate = $resultStatement->fetchColumn();

    $pdo->commit();

    respond(200, true, 'Pesanan berhasil dibuat', [
        'id_transaksi' => $transactionId,
        'kode_transaksi' => $transactionCode,
        'total_harga' => $subtotal,
        'status_laundry' => 'menunggu',
        'status_pembayaran' => 'belum_dibayar',
        'estimasi_selesai' => $estimate,
    ]);
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
