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

$payload = json_decode(file_get_contents('php://input'), true);
if (!is_array($payload)) {
    respond(400, false, 'Request JSON tidak valid');
}

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();
    $user = requireRole($pdo, [1, 2, 3, 4]);
    $userId = $user['id_user'];

    $oldPassword = (string) ($payload['old_password'] ?? '');
    $newPassword = (string) ($payload['new_password'] ?? '');
    $confirmation = (string) ($payload['confirm_password'] ?? '');

    if ($oldPassword === '' || $newPassword === '' || $confirmation === '') {
        respond(400, false, 'Semua field wajib diisi');
    }
    if (mb_strlen($newPassword) < 6) {
        respond(400, false, 'Password minimal 6 karakter');
    }
    if (mb_strlen($newPassword) > 16) {
        respond(400, false, 'Password maksimal 16 karakter');
    }
    if (!hash_equals($newPassword, $confirmation)) {
        respond(400, false, 'Konfirmasi password tidak cocok');
    }

    $passwordStatement = $pdo->prepare('SELECT password FROM users WHERE id_user = :id_user LIMIT 1');
    $passwordStatement->execute(['id_user' => $userId]);
    $passwordHash = $passwordStatement->fetchColumn();
    if (!is_string($passwordHash) || !password_verify($oldPassword, $passwordHash)) {
        respond(400, false, 'Password lama salah');
    }
    if (password_verify($newPassword, $passwordHash)) {
        respond(400, false, 'Password baru tidak boleh sama dengan password lama');
    }

    $newHash = password_hash($newPassword, PASSWORD_DEFAULT);
    if ($newHash === false) {
        throw new RuntimeException('Password hashing failed');
    }
    $update = $pdo->prepare('UPDATE users SET password = :password WHERE id_user = :id_user');
    $update->execute(['password' => $newHash, 'id_user' => $userId]);

    respond(200, true, 'Password berhasil diubah');
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
