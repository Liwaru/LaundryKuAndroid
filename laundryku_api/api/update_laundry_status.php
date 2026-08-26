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
    return is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1 ? (int) $value : null;
}

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, false, 'Metode request tidak diizinkan');
}

$payload = json_decode(file_get_contents('php://input'), true);
if (!is_array($payload)) {
    respond(400, false, 'Request JSON tidak valid');
}

$transactionId = positiveInteger($payload['id_transaksi'] ?? null);
$expectedStatus = is_string($payload['current_status'] ?? null) ? $payload['current_status'] : null;
$validExpectedStatuses = ['menunggu', 'dicuci', 'dikeringkan', 'disetrika', 'dipacking'];
if ($transactionId === null || !in_array($expectedStatus, $validExpectedStatuses, true)) {
    respond(400, false, 'Data update status tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $staff = requireRole($pdo, [3]);
    $staffId = $staff['id_user'];
    $pdo->beginTransaction();

    $transactionStatement = $pdo->prepare(
        'SELECT id_transaksi, status_laundry FROM transaksi WHERE id_transaksi = :id LIMIT 1 FOR UPDATE'
    );
    $transactionStatement->execute(['id' => $transactionId]);
    $transaction = $transactionStatement->fetch();
    if (!$transaction) {
        $pdo->rollBack();
        respond(404, false, 'Transaksi tidak ditemukan');
    }

    $currentStatus = $transaction['status_laundry'];
    if (in_array($currentStatus, ['selesai', 'dibatalkan', 'siap_diambil'], true)) {
        $pdo->rollBack();
        respond(409, false, 'Status transaksi tidak dapat diperbarui oleh Staff');
    }
    if ($currentStatus !== $expectedStatus) {
        $pdo->rollBack();
        respond(409, false, 'Status transaksi sudah berubah. Muat ulang data pekerjaan.');
    }

    $detailStatement = $pdo->prepare(
        'SELECT COUNT(d.id_detail) AS jumlah_detail,
                COALESCE(MAX(l.perlu_setrika), 0) AS perlu_setrika
         FROM detail_transaksi d
         INNER JOIN layanan l ON l.id_layanan = d.id_layanan
         WHERE d.id_transaksi = :id'
    );
    $detailStatement->execute(['id' => $transactionId]);
    $detailInfo = $detailStatement->fetch();
    if (!$detailInfo || (int) $detailInfo['jumlah_detail'] < 1) {
        throw new RuntimeException('Transaction detail missing');
    }

    $requiresIron = (int) $detailInfo['perlu_setrika'] === 1;
    $nextStatus = match ($currentStatus) {
        'menunggu' => 'dicuci',
        'dicuci' => 'dikeringkan',
        'dikeringkan' => $requiresIron ? 'disetrika' : 'dipacking',
        'disetrika' => 'dipacking',
        'dipacking' => 'siap_diambil',
        default => null,
    };
    if ($nextStatus === null || $nextStatus === 'selesai') {
        $pdo->rollBack();
        respond(409, false, 'Tidak ada status lanjutan yang diizinkan');
    }

    $notes = [
        'dicuci' => 'Laundry mulai dicuci',
        'dikeringkan' => 'Laundry masuk proses pengeringan',
        'disetrika' => 'Laundry masuk proses setrika',
        'dipacking' => 'Laundry sedang dipacking',
        'siap_diambil' => 'Laundry siap diambil pelanggan',
    ];

    $updateStatement = $pdo->prepare(
        'UPDATE transaksi
         SET status_laundry = :next_status, updated_at = NOW()
         WHERE id_transaksi = :id AND status_laundry = :current_status'
    );
    $updateStatement->execute([
        'next_status' => $nextStatus,
        'id' => $transactionId,
        'current_status' => $currentStatus,
    ]);
    if ($updateStatement->rowCount() !== 1) {
        throw new RuntimeException('Concurrent laundry status update detected');
    }

    $historyStatement = $pdo->prepare(
        'INSERT INTO riwayat_status (id_transaksi, id_user, status_laundry, waktu, catatan)
         VALUES (:transaction, :staff, :status, NOW(), :note)'
    );
    $historyStatement->execute([
        'transaction' => $transactionId,
        'staff' => $staffId,
        'status' => $nextStatus,
        'note' => $notes[$nextStatus],
    ]);

    $pdo->commit();
    respond(200, true, 'Status laundry berhasil diperbarui', [
        'id_transaksi' => $transactionId,
        'status_laundry' => $nextStatus,
    ]);
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
