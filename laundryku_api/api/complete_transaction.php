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
    return is_string($value) && preg_match('/^[1-9][0-9]*$/', $value) === 1
        ? (int) $value
        : null;
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
    respond(400, false, 'Data penyelesaian transaksi tidak valid');
}

require_once __DIR__ . '/../config/database.php';

$pdo = null;
try {
    $pdo = getDatabaseConnection();
    $pdo->beginTransaction();

    // TODO: Replace client id_user with authenticated bearer-token identity.
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
        respond(403, false, 'Hanya Kasir/Admin aktif yang dapat menyelesaikan transaksi');
    }

    $transactionStatement = $pdo->prepare(
        'SELECT id_transaksi, status_laundry, status_pembayaran, tanggal_selesai
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

    if ($transaction['status_laundry'] === 'selesai') {
        $pdo->commit();
        respond(200, true, 'Transaksi sudah selesai', [
            'id_transaksi' => $transactionId,
            'status_laundry' => 'selesai',
            'tanggal_selesai' => $transaction['tanggal_selesai'],
        ]);
    }
    if ($transaction['status_laundry'] !== 'siap_diambil') {
        $pdo->rollBack();
        respond(409, false, 'Laundry belum siap diambil atau status transaksi tidak dapat diselesaikan');
    }
    if ($transaction['status_pembayaran'] !== 'sudah_dibayar') {
        $pdo->rollBack();
        respond(409, false, 'Pembayaran harus diselesaikan sebelum transaksi ditandai selesai');
    }

    $updateStatement = $pdo->prepare(
        "UPDATE transaksi
         SET status_laundry = 'selesai', tanggal_selesai = NOW(),
             id_kasir = :id_kasir, updated_at = NOW()
         WHERE id_transaksi = :id_transaksi
           AND status_laundry = 'siap_diambil'
           AND status_pembayaran = 'sudah_dibayar'"
    );
    $updateStatement->execute([
        'id_kasir' => $cashierId,
        'id_transaksi' => $transactionId,
    ]);
    if ($updateStatement->rowCount() !== 1) {
        throw new RuntimeException('Atomic transaction completion failed');
    }

    $historyStatement = $pdo->prepare(
        "INSERT INTO riwayat_status
         (id_transaksi, id_user, status_laundry, waktu, catatan)
         VALUES (:id_transaksi, :id_user, 'selesai', NOW(), :catatan)"
    );
    $historyStatement->execute([
        'id_transaksi' => $transactionId,
        'id_user' => $cashierId,
        'catatan' => 'Laundry telah diserahkan kepada pelanggan',
    ]);

    $completionDate = $pdo->query(
        "SELECT tanggal_selesai FROM transaksi WHERE id_transaksi = {$transactionId}"
    )->fetchColumn();
    $pdo->commit();
    respond(200, true, 'Transaksi berhasil diselesaikan', [
        'id_transaksi' => $transactionId,
        'status_laundry' => 'selesai',
        'tanggal_selesai' => $completionDate,
    ]);
} catch (Throwable $exception) {
    if ($pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
