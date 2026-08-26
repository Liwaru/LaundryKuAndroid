<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';

function testTokenForUser(int $userId): string
{
    static $tokens = [];
    static $registeredCleanup = false;

    if (isset($tokens[$userId])) {
        return $tokens[$userId];
    }

    $pdo = getDatabaseConnection();
    $exists = $pdo->prepare('SELECT COUNT(*) FROM users WHERE id_user = :id');
    $exists->execute(['id' => $userId]);
    if ((int) $exists->fetchColumn() !== 1) {
        return bin2hex(random_bytes(32));
    }

    $rawToken = bin2hex(random_bytes(32));
    $tokenHash = hash('sha256', $rawToken);
    $insert = $pdo->prepare(
        'INSERT INTO auth_tokens (id_user, token_hash, expires_at)
         VALUES (:user, :hash, NOW() + INTERVAL 1 DAY)'
    );
    $insert->execute(['user' => $userId, 'hash' => $tokenHash]);
    $tokens[$userId] = $rawToken;

    if (!$registeredCleanup) {
        register_shutdown_function(static function () use (&$tokens): void {
            if ($tokens === []) {
                return;
            }
            $hashes = array_map(static fn(string $token): string => hash('sha256', $token), array_values($tokens));
            $placeholders = implode(',', array_fill(0, count($hashes), '?'));
            getDatabaseConnection()->prepare("DELETE FROM auth_tokens WHERE token_hash IN ({$placeholders})")
                ->execute($hashes);
        });
        $registeredCleanup = true;
    }

    return $rawToken;
}

function testAuthHeaders(int $userId, bool $json = false): array
{
    $headers = ['Authorization: Bearer ' . testTokenForUser($userId)];
    if ($json) {
        $headers[] = 'Content-Type: application/json';
    }
    return $headers;
}
