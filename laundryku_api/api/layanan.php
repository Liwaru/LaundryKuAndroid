<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, array $data = []): void
{
    http_response_code($status);
    echo json_encode([
        'success' => $success,
        'message' => $message,
        'data' => $data,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    header('Allow: GET');
    respond(405, false, 'Metode request tidak diizinkan');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();
    $statement = $pdo->prepare(
        'SELECT id_layanan, nama_layanan, harga, satuan, minimal_order,
                estimasi_hari, perlu_setrika
         FROM layanan
         WHERE status = :status
         ORDER BY id_layanan ASC'
    );
    $statement->execute(['status' => 'aktif']);

    $services = array_map(
        static fn(array $service): array => [
            'id_layanan' => (int) $service['id_layanan'],
            'nama_layanan' => $service['nama_layanan'],
            'harga' => (float) $service['harga'],
            'satuan' => $service['satuan'],
            'minimal_order' => (float) $service['minimal_order'],
            'estimasi_hari' => (int) $service['estimasi_hari'],
            'perlu_setrika' => (bool) $service['perlu_setrika'],
        ],
        $statement->fetchAll()
    );

    respond(200, true, 'Data layanan berhasil diambil', $services);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
