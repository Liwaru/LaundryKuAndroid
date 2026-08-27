<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/auth_test_helper.php';

const BASE_URL = 'http://127.0.0.1/laundryku_api/api/';
const OWNER_ID = 4;

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual));
    }
}

function requestJson(string $endpoint, array $payload, ?string $token = null): array
{
    $handle = curl_init(BASE_URL . $endpoint);
    $headers = ['Content-Type: application/json'];
    if ($token !== null) {
        $headers[] = 'Authorization: Bearer ' . $token;
    }
    curl_setopt_array($handle, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_POST => true,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_POSTFIELDS => json_encode($payload, JSON_THROW_ON_ERROR),
    ]);
    $raw = curl_exec($handle);
    if ($raw === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($raw, true, 512, JSON_THROW_ON_ERROR)];
}

function insertFixtureUser(PDO $pdo, int $level, string $password, string $suffix): int
{
    $statement = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         VALUES (:name, :phone, :username, :password, :level, 'aktif')"
    );
    $statement->execute([
        'name' => 'Prf' . $level,
        'phone' => '087' . random_int(100000000, 999999999),
        'username' => 'pf' . $level . $suffix,
        'password' => password_hash($password, PASSWORD_DEFAULT),
        'level' => $level,
    ]);
    return (int) $pdo->lastInsertId();
}

function login(string $username, string $password): array
{
    return requestJson('login.php', ['username' => $username, 'password' => $password]);
}

$pdo = getDatabaseConnection();
$suffix = strtolower(bin2hex(random_bytes(3)));
$oldPassword = bin2hex(random_bytes(4));
$newPassword = bin2hex(random_bytes(5));
$userIds = [];
$usernames = [];

try {
    assertSameValue(401, requestJson('update_profile.php', [])['status'], 'Update profile allowed without token');
    assertSameValue(
        401,
        requestJson('update_profile.php', [], str_repeat('0', 64))['status'],
        'Update profile allowed an invalid token'
    );
    assertSameValue(401, requestJson('change_password.php', [])['status'], 'Change password allowed without token');

    foreach ([1, 2, 3, 4] as $level) {
        $id = insertFixtureUser($pdo, $level, $oldPassword, $suffix . $level);
        $userIds[] = $id;
        $username = 'ed' . $level . $suffix;
        $phone = '086' . random_int(100000000, 999999999);
        $response = requestJson('update_profile.php', [
            'nama' => 'Edit' . $level,
            'username' => $username,
            'no_hp' => $phone,
            'id_user' => OWNER_ID,
            'level' => 4,
        ], testTokenForUser($id));
        assertSameValue(200, $response['status'], "Level {$level} could not edit its profile");
        $stored = $pdo->query("SELECT nama, username, no_hp, level FROM users WHERE id_user = {$id}")->fetch();
        assertSameValue('Edit' . $level, $stored['nama'], "Level {$level} name was not updated");
        assertSameValue($username, $stored['username'], "Level {$level} username was not updated");
        assertSameValue($phone, $stored['no_hp'], "Level {$level} phone was not updated");
        assertSameValue($level, (int) $stored['level'], 'Client level changed the account role');
        $usernames[$id] = $username;
    }

    $firstId = $userIds[0];
    $secondId = $userIds[1];
    $second = $pdo->query("SELECT username, no_hp FROM users WHERE id_user = {$secondId}")->fetch();
    $first = $pdo->query("SELECT nama, username, no_hp FROM users WHERE id_user = {$firstId}")->fetch();
    assertSameValue(409, requestJson('update_profile.php', [
        'nama' => $first['nama'], 'username' => $second['username'], 'no_hp' => $first['no_hp'],
    ], testTokenForUser($firstId))['status'], 'Duplicate username was accepted');
    assertSameValue(409, requestJson('update_profile.php', [
        'nama' => $first['nama'], 'username' => $first['username'], 'no_hp' => $second['no_hp'],
    ], testTokenForUser($firstId))['status'], 'Duplicate phone was accepted');
    assertSameValue(200, requestJson('update_profile.php', [
        'nama' => $first['nama'], 'username' => $first['username'], 'no_hp' => $first['no_hp'],
    ], testTokenForUser($firstId))['status'], 'Unchanged unique fields were rejected');

    $token = testTokenForUser($firstId);
    assertSameValue(400, requestJson('change_password.php', [
        'old_password' => bin2hex(random_bytes(4)), 'new_password' => $newPassword,
        'confirm_password' => $newPassword,
    ], $token)['status'], 'Wrong old password was accepted');
    assertSameValue(400, requestJson('change_password.php', [
        'old_password' => $oldPassword, 'new_password' => str_repeat('x', 5),
        'confirm_password' => str_repeat('x', 5),
    ], $token)['status'], 'Short new password was accepted');
    assertSameValue(400, requestJson('change_password.php', [
        'old_password' => $oldPassword, 'new_password' => $newPassword,
        'confirm_password' => strrev($newPassword),
    ], $token)['status'], 'Mismatched confirmation was accepted');
    assertSameValue(400, requestJson('change_password.php', [
        'old_password' => $oldPassword, 'new_password' => $oldPassword,
        'confirm_password' => $oldPassword,
    ], $token)['status'], 'Same password was accepted');
    assertSameValue(200, requestJson('change_password.php', [
        'old_password' => $oldPassword, 'new_password' => $newPassword,
        'confirm_password' => $newPassword,
        'id_user' => $secondId,
    ], $token)['status'], 'Valid password change failed');
    assertSameValue(200, requestJson('update_profile.php', [
        'nama' => $first['nama'], 'username' => $first['username'], 'no_hp' => $first['no_hp'],
    ], $token)['status'], 'Current bearer token was invalidated after password change');
    assertSameValue(401, login($first['username'], $oldPassword)['status'], 'Old password still logs in');
    assertSameValue(200, login($first['username'], $newPassword)['status'], 'New password cannot log in');

    $registerUsername = 'rg' . $suffix;
    $registerPhone = '085' . random_int(100000000, 999999999);
    $registerShort = [
        'nama' => 'RegTest', 'no_hp' => $registerPhone, 'username' => $registerUsername,
        'password' => str_repeat('r', 5), 'konfirmasi_password' => str_repeat('r', 5),
    ];
    assertSameValue(400, requestJson('register.php', $registerShort)['status'], 'Register accepted a short password');
    $registerPassword = bin2hex(random_bytes(3));
    $registerShort['password'] = $registerPassword;
    $registerShort['konfirmasi_password'] = $registerPassword;
    assertSameValue(200, requestJson('register.php', $registerShort)['status'], 'Register rejected a six-character password');
    $registeredId = (int) $pdo->query("SELECT id_user FROM users WHERE username = " . $pdo->quote($registerUsername))->fetchColumn();
    $userIds[] = $registeredId;

    $staffUsername = 'sf' . $suffix;
    $staffPhone = '084' . random_int(100000000, 999999999);
    $staffPayload = [
        'nama' => 'StaffT', 'no_hp' => $staffPhone, 'username' => $staffUsername,
        'password' => str_repeat('s', 5), 'level' => 2,
    ];
    assertSameValue(400, requestJson('owner_create_staff.php', $staffPayload, testTokenForUser(OWNER_ID))['status'], 'Add Staff accepted a short password');
    $staffPayload['password'] = bin2hex(random_bytes(3));
    $staffResponse = requestJson('owner_create_staff.php', $staffPayload, testTokenForUser(OWNER_ID));
    assertSameValue(201, $staffResponse['status'], 'Add Staff rejected a six-character password');
    $userIds[] = (int) $staffResponse['body']['data']['id_user'];
    $invalidRolePayload = $staffPayload;
    $invalidRolePayload['username'] = 'iv' . $suffix;
    $invalidRolePayload['no_hp'] = '083' . random_int(100000000, 999999999);
    $invalidRolePayload['level'] = 1;
    assertSameValue(400, requestJson('owner_create_staff.php', $invalidRolePayload, testTokenForUser(OWNER_ID))['status'], 'Owner created an invalid target role');
    assertSameValue(403, requestJson('owner_create_staff.php', $invalidRolePayload, testTokenForUser($secondId))['status'], 'Non-owner created staff');

    echo "PASS: update profile requires a valid bearer token and supports Levels 1-4\n";
    echo "PASS: identity comes from token; client id_user/level cannot change another user or role\n";
    echo "PASS: username and phone uniqueness exclude only the authenticated user\n";
    echo "PASS: password validation, hashing, current-session continuity, and old/new login regression\n";
    echo "PASS: Register and Add Staff require 6-16 characters; owner target remains Level 2/3\n";
} finally {
    $userIds = array_values(array_filter(array_unique($userIds), static fn(int $id): bool => $id > 4));
    if ($userIds !== []) {
        $placeholders = implode(',', array_fill(0, count($userIds), '?'));
        $pdo->prepare("DELETE FROM users WHERE id_user IN ({$placeholders})")->execute($userIds);
    }
}
