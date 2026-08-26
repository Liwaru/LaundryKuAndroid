<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message): never
{
    http_response_code($status);
    echo json_encode(['success' => $success, 'message' => $message], JSON_UNESCAPED_UNICODE);
    exit;
}
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, false, 'Metode request tidak diizinkan');
}
$notification = json_decode(file_get_contents('php://input'), true);
if (!is_array($notification)) {
    respond(400, false, 'Notification tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/midtrans.php';
require_once __DIR__ . '/../config/qris_payment.php';

try {
    if (!midtransNotificationSignatureIsValid($notification)) {
        respond(401, false, 'Signature notification tidak valid');
    }

    $verifiedStatus = midtransGetStatus((string) $notification['order_id']);
    if (($verifiedStatus['order_id'] ?? null) !== $notification['order_id'] ||
        qrisAmount($verifiedStatus['gross_amount'] ?? null) !== qrisAmount($notification['gross_amount'] ?? null)) {
        throw new QrisPaymentException('Status Midtrans tidak cocok dengan notification');
    }

    applyVerifiedQrisStatus(getDatabaseConnection(), $verifiedStatus);
    respond(200, true, 'Notification pembayaran diproses');
} catch (MidtransConfigurationException $exception) {
    error_log($exception->getMessage());
    respond(503, false, 'Konfigurasi pembayaran belum tersedia');
} catch (MidtransRequestException $exception) {
    error_log($exception->getMessage());
    respond(502, false, 'Verifikasi status pembayaran gagal');
} catch (QrisPaymentException $exception) {
    error_log($exception->getMessage());
    respond($exception->responseStatus, false, 'Notification pembayaran ditolak');
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
