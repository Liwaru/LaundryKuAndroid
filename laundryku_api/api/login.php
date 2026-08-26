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

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    header('Allow: POST');
    respond(405, false, 'Metode request tidak diizinkan');
}

$payload = json_decode(file_get_contents('php://input'), true);

if (!is_array($payload)) {
    respond(400, false, 'Request JSON tidak valid');
}

$username = trim((string) ($payload['username'] ?? ''));
$password = (string) ($payload['password'] ?? '');

if ($username === '' || $password === '') {
    respond(400, false, 'Username dan password wajib diisi');
}

if (mb_strlen($username) > 12 || mb_strlen($password) > 16) {
    respond(401, false, 'Username atau password salah');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();
    $statement = $pdo->prepare(
        'SELECT id_user, nama, no_hp, username, password, level, status_akun
         FROM users
         WHERE username = :username
         LIMIT 1'
    );
    $statement->execute(['username' => $username]);
    $user = $statement->fetch();

    if (!$user || !password_verify($password, $user['password'])) {
        respond(401, false, 'Username atau password salah');
    }

    if ($user['status_akun'] !== 'aktif') {
        respond(403, false, 'Akun Anda sedang nonaktif');
    }

    $level = (int) $user['level'];
    if (!in_array($level, [1, 2, 3, 4], true)) {
        respond(403, false, 'Role akun tidak valid');
    }

    respond(200, true, 'Login berhasil', [
        'id_user' => (int) $user['id_user'],
        'nama' => $user['nama'],
        'no_hp' => $user['no_hp'],
        'username' => $user['username'],
        'level' => $level,
    ]);
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
