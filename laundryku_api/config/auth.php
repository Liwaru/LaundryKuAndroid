<?php
declare(strict_types=1);

const AUTH_INVALID_MESSAGE = 'Sesi tidak valid atau telah berakhir';

function authRespond(int $status, string $message): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    echo json_encode([
        'success' => false,
        'message' => $message,
    ], JSON_UNESCAPED_UNICODE);
    exit;
}

function bearerToken(): string
{
    $authorization = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if ($authorization === '' && function_exists('getallheaders')) {
        $headers = getallheaders();
        $authorization = (string) ($headers['Authorization'] ?? $headers['authorization'] ?? '');
    }

    if (preg_match('/^Bearer ([a-fA-F0-9]{64})$/D', trim($authorization), $matches) !== 1) {
        authRespond(401, AUTH_INVALID_MESSAGE);
    }
    return $matches[1];
}

function requireAuthenticatedUser(PDO $pdo): array
{
    $tokenHash = hash('sha256', bearerToken());
    $statement = $pdo->prepare(
        "SELECT user.id_user, user.nama, user.username, user.no_hp, user.level
         FROM auth_tokens token
         INNER JOIN users user ON user.id_user = token.id_user
         WHERE token.token_hash = :token_hash
           AND token.revoked_at IS NULL
           AND token.expires_at > NOW()
           AND user.status_akun = 'aktif'
         LIMIT 1"
    );
    $statement->execute(['token_hash' => $tokenHash]);
    $user = $statement->fetch();
    if (!$user) {
        authRespond(401, AUTH_INVALID_MESSAGE);
    }

    $touch = $pdo->prepare(
        'UPDATE auth_tokens SET last_used_at = NOW()
         WHERE token_hash = :token_hash AND revoked_at IS NULL AND expires_at > NOW()'
    );
    $touch->execute(['token_hash' => $tokenHash]);

    return [
        'id_user' => (int) $user['id_user'],
        'nama' => $user['nama'],
        'username' => $user['username'],
        'no_hp' => $user['no_hp'],
        'level' => (int) $user['level'],
    ];
}

function requireRole(PDO $pdo, array $allowedLevels): array
{
    $user = requireAuthenticatedUser($pdo);
    if (!in_array($user['level'], $allowedLevels, true)) {
        authRespond(403, 'Anda tidak memiliki akses');
    }
    return $user;
}

function revokeCurrentToken(PDO $pdo): void
{
    $tokenHash = hash('sha256', bearerToken());
    $statement = $pdo->prepare(
        'UPDATE auth_tokens SET revoked_at = COALESCE(revoked_at, NOW()) WHERE token_hash = :token_hash'
    );
    $statement->execute(['token_hash' => $tokenHash]);
}
