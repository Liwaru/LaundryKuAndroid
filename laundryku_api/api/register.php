<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message): void
{
    http_response_code($status);
    echo json_encode([
        'success' => $success,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
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

$nama = trim((string) ($payload['nama'] ?? ''));
$noHp = trim((string) ($payload['no_hp'] ?? ''));
$username = trim((string) ($payload['username'] ?? ''));
$password = (string) ($payload['password'] ?? '');
$konfirmasiPassword = (string) ($payload['konfirmasi_password'] ?? '');

if ($nama === '' || $noHp === '' || $username === '' || $password === '' || $konfirmasiPassword === '') {
    respond(400, false, 'Semua field wajib diisi');
}

if (mb_strlen($nama) > 8) {
    respond(400, false, 'Nama maksimal 8 karakter');
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

if (!hash_equals($password, $konfirmasiPassword)) {
    respond(400, false, 'Password dan konfirmasi password tidak sama');
}

require_once __DIR__ . '/../config/database.php';

try {
    $pdo = getDatabaseConnection();

    $checkUsername = $pdo->prepare('SELECT id_user FROM users WHERE username = :username LIMIT 1');
    $checkUsername->execute(['username' => $username]);
    if ($checkUsername->fetch()) {
        respond(409, false, 'Username sudah digunakan');
    }

    $checkPhone = $pdo->prepare('SELECT id_user FROM users WHERE no_hp = :no_hp LIMIT 1');
    $checkPhone->execute(['no_hp' => $noHp]);
    if ($checkPhone->fetch()) {
        respond(409, false, 'No. HP sudah digunakan');
    }

    $passwordHash = password_hash($password, PASSWORD_DEFAULT);
    if ($passwordHash === false) {
        throw new RuntimeException('Password hashing failed');
    }

    $level = 1;
    $statusAkun = 'aktif';
    $insert = $pdo->prepare(
        'INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         VALUES (:nama, :no_hp, :username, :password, :level, :status_akun)'
    );
    $insert->execute([
        'nama' => $nama,
        'no_hp' => $noHp,
        'username' => $username,
        'password' => $passwordHash,
        'level' => $level,
        'status_akun' => $statusAkun,
    ]);

    respond(200, true, 'Register berhasil, silakan login');
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
