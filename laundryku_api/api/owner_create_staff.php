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
    if (is_int($value) && $value > 0) {
        return $value;
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

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();

    requireRole($pdo, [4]);

    $nama = trim((string) ($payload['nama'] ?? ''));
    $noHp = trim((string) ($payload['no_hp'] ?? ''));
    $username = trim((string) ($payload['username'] ?? ''));
    $password = (string) ($payload['password'] ?? '');
    $level = positiveInteger($payload['level'] ?? null);

    if ($nama === '' || $noHp === '' || $username === '' || $password === '' || $level === null) {
        respond(400, false, 'Semua field wajib diisi');
    }
    if (mb_strlen($nama) > 9) {
        respond(400, false, 'Nama maksimal 9 karakter');
    }
    if (!preg_match('/^[0-9]{1,12}$/', $noHp)) {
        respond(400, false, 'No. HP hanya boleh berisi maksimal 12 digit');
    }
    if (mb_strlen($username) > 12) {
        respond(400, false, 'Username maksimal 12 karakter');
    }
    if (mb_strlen($password) < 6) {
        respond(400, false, 'Password minimal 6 karakter');
    }
    if (mb_strlen($password) > 16) {
        respond(400, false, 'Password maksimal 16 karakter');
    }
    if (!in_array($level, [2, 3], true)) {
        respond(400, false, 'Role staff tidak valid');
    }

    $usernameStatement = $pdo->prepare('SELECT id_user FROM users WHERE username = :username LIMIT 1');
    $usernameStatement->execute(['username' => $username]);
    if ($usernameStatement->fetch()) {
        respond(409, false, 'Username sudah digunakan');
    }

    $phoneStatement = $pdo->prepare('SELECT id_user FROM users WHERE no_hp = :no_hp LIMIT 1');
    $phoneStatement->execute(['no_hp' => $noHp]);
    if ($phoneStatement->fetch()) {
        respond(409, false, 'No. HP sudah digunakan');
    }

    $passwordHash = password_hash($password, PASSWORD_DEFAULT);
    if ($passwordHash === false) {
        throw new RuntimeException('Password hashing failed');
    }

    $insert = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         VALUES (:nama, :no_hp, :username, :password, :level, 'aktif')"
    );
    $insert->execute([
        'nama' => $nama,
        'no_hp' => $noHp,
        'username' => $username,
        'password' => $passwordHash,
        'level' => $level,
    ]);

    respond(201, true, 'Staff berhasil ditambahkan', [
        'id_user' => (int) $pdo->lastInsertId(),
        'nama' => $nama,
        'username' => $username,
        'no_hp' => $noHp,
        'level' => $level,
        'status_akun' => 'aktif',
    ]);
} catch (PDOException $exception) {
    if ($exception->getCode() === '23000') {
        respond(409, false, 'Username atau no. HP sudah digunakan');
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
