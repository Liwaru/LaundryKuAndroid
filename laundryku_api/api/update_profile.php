<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function respond(int $status, bool $success, string $message, ?array $data = null): never
{
    http_response_code($status);
    $response = ['success' => $success, 'message' => $message];
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

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/auth.php';

try {
    $pdo = getDatabaseConnection();
    $user = requireRole($pdo, [1, 2, 3, 4]);
    $userId = $user['id_user'];

    $nama = trim((string) ($payload['nama'] ?? ''));
    $username = trim((string) ($payload['username'] ?? ''));
    $noHp = trim((string) ($payload['no_hp'] ?? ''));

    if ($nama === '' || $username === '' || $noHp === '') {
        respond(400, false, 'Semua field wajib diisi');
    }
    if (mb_strlen($nama) > 9) {
        respond(400, false, 'Nama maksimal 9 karakter');
    }
    if (mb_strlen($username) > 12) {
        respond(400, false, 'Username maksimal 12 karakter');
    }
    if (!preg_match('/^[0-9]{1,12}$/', $noHp)) {
        respond(400, false, 'No. HP hanya boleh berisi maksimal 12 digit');
    }

    $usernameCheck = $pdo->prepare(
        'SELECT id_user FROM users WHERE username = :username AND id_user <> :id_user LIMIT 1'
    );
    $usernameCheck->execute(['username' => $username, 'id_user' => $userId]);
    if ($usernameCheck->fetch()) {
        respond(409, false, 'Username sudah digunakan');
    }

    $phoneCheck = $pdo->prepare(
        'SELECT id_user FROM users WHERE no_hp = :no_hp AND id_user <> :id_user LIMIT 1'
    );
    $phoneCheck->execute(['no_hp' => $noHp, 'id_user' => $userId]);
    if ($phoneCheck->fetch()) {
        respond(409, false, 'Nomor HP sudah digunakan');
    }

    $update = $pdo->prepare(
        'UPDATE users SET nama = :nama, username = :username, no_hp = :no_hp WHERE id_user = :id_user'
    );
    $update->execute([
        'nama' => $nama,
        'username' => $username,
        'no_hp' => $noHp,
        'id_user' => $userId,
    ]);

    respond(200, true, 'Profil berhasil diperbarui', [
        'nama' => $nama,
        'username' => $username,
        'no_hp' => $noHp,
    ]);
} catch (PDOException $exception) {
    if ($exception->getCode() === '23000') {
        respond(409, false, 'Username atau nomor HP sudah digunakan');
    }
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    respond(500, false, 'Terjadi kesalahan pada server');
}
