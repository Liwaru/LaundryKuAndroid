<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';

const BASE_URL = 'http://127.0.0.1/laundryku_api/api/';
const CUSTOMER_ID = 1;
const CASHIER_ID = 2;
const STAFF_ID = 3;
const OWNER_ID = 4;

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException(
            $message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual)
        );
    }
}

function request(string $path, string $method = 'GET', ?array $payload = null): array
{
    $handle = curl_init(BASE_URL . $path);
    $options = [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10];
    if ($method === 'POST') {
        $options[CURLOPT_POST] = true;
        $options[CURLOPT_HTTPHEADER] = ['Content-Type: application/json'];
        $options[CURLOPT_POSTFIELDS] = json_encode($payload, JSON_THROW_ON_ERROR);
    }
    curl_setopt_array($handle, $options);
    $body = curl_exec($handle);
    if ($body === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($body, true, 512, JSON_THROW_ON_ERROR)];
}

function createFixtureUser(PDO $pdo, int $sourceId, int $level, string $status, string $prefix): int
{
    $statement = $pdo->prepare(
        'INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT :name, :phone, :username, password, :level, :status
         FROM users WHERE id_user = :source'
    );
    $statement->execute([
        'name' => substr($prefix . ' Test', 0, 9),
        'phone' => '085' . random_int(100000000, 999999999),
        'username' => strtolower(substr($prefix, 0, 3)) . random_int(10000, 99999),
        'level' => $level,
        'status' => $status,
        'source' => $sourceId,
    ]);
    return (int) $pdo->lastInsertId();
}

function createPayload(int $requester, string $username, string $phone, int $level): array
{
    return [
        'id_user' => $requester,
        'nama' => $level === 2 ? 'Kasir Tes' : 'Staff Tes',
        'no_hp' => $phone,
        'username' => $username,
        'password' => 'Staff123',
        'level' => $level,
    ];
}

function containsKeyRecursively(mixed $value, string $key): bool
{
    if (!is_array($value)) {
        return false;
    }
    if (array_key_exists($key, $value)) {
        return true;
    }
    foreach ($value as $child) {
        if (containsKeyRecursively($child, $key)) {
            return true;
        }
    }
    return false;
}

$pdo = getDatabaseConnection();
$fixtureUserIds = [];

try {
    $initial = request('owner_staff.php?id_user=' . OWNER_ID);
    assertSameValue(200, $initial['status'], 'Active Owner could not load staff');
    $baseline = $initial['body']['data']['summary'];
    assertSameValue(false, containsKeyRecursively($initial['body'], 'password'), 'Password was exposed by list endpoint');

    foreach ([CUSTOMER_ID, CASHIER_ID, STAFF_ID] as $requester) {
        assertSameValue(403, request('owner_staff.php?id_user=' . $requester)['status'], "Level for {$requester} loaded staff");
    }
    assertSameValue(403, request('owner_staff.php?id_user=999999')['status'], 'Unknown user loaded staff');

    $inactiveOwnerId = $fixtureUserIds[] = createFixtureUser($pdo, OWNER_ID, 4, 'nonaktif', 'Own');
    assertSameValue(403, request('owner_staff.php?id_user=' . $inactiveOwnerId)['status'], 'Inactive Owner loaded staff');

    $inactiveCashierId = $fixtureUserIds[] = createFixtureUser($pdo, CASHIER_ID, 2, 'nonaktif', 'Kas');
    $listWithFixture = request('owner_staff.php?id_user=' . OWNER_ID);
    $summary = $listWithFixture['body']['data']['summary'];
    assertSameValue((int) $baseline['jumlah_kasir'] + 1, $summary['jumlah_kasir'], 'Cashier summary is wrong');
    assertSameValue((int) $baseline['jumlah_staff_laundry'], $summary['jumlah_staff_laundry'], 'Laundry summary changed');
    assertSameValue((int) $baseline['jumlah_staff_aktif'], $summary['jumlah_staff_aktif'], 'Inactive cashier counted as active');
    $staffRows = $listWithFixture['body']['data']['staff'];
    assertSameValue(true, in_array($inactiveCashierId, array_column($staffRows, 'id_user'), true), 'Inactive cashier missing');
    foreach ($staffRows as $row) {
        if (!in_array((int) $row['level'], [2, 3], true)) {
            throw new RuntimeException('Customer or Owner leaked into staff list');
        }
    }
    assertSameValue(false, containsKeyRecursively($listWithFixture['body'], 'password'), 'Password was exposed');

    $cashierUsername = 'newkas' . random_int(100, 999);
    $cashierPhone = '084' . random_int(100000000, 999999999);
    $cashierPayload = createPayload(OWNER_ID, $cashierUsername, $cashierPhone, 2);
    $createdCashier = request('owner_create_staff.php', 'POST', $cashierPayload);
    assertSameValue(201, $createdCashier['status'], 'Owner could not create Level 2');
    $cashierCreatedId = (int) $createdCashier['body']['data']['id_user'];
    $fixtureUserIds[] = $cashierCreatedId;

    $staffUsername = 'newstf' . random_int(100, 999);
    $staffPhone = '083' . random_int(100000000, 999999999);
    $createdStaff = request(
        'owner_create_staff.php',
        'POST',
        createPayload(OWNER_ID, $staffUsername, $staffPhone, 3)
    );
    assertSameValue(201, $createdStaff['status'], 'Owner could not create Level 3');
    $staffCreatedId = (int) $createdStaff['body']['data']['id_user'];
    $fixtureUserIds[] = $staffCreatedId;

    assertSameValue(409, request('owner_create_staff.php', 'POST', createPayload(
        OWNER_ID,
        $cashierUsername,
        '082' . random_int(100000000, 999999999),
        2
    ))['status'], 'Duplicate username was allowed');
    assertSameValue(409, request('owner_create_staff.php', 'POST', createPayload(
        OWNER_ID,
        'dup' . random_int(10000, 99999),
        $cashierPhone,
        3
    ))['status'], 'Duplicate phone was allowed');

    foreach ([1, 4, 99] as $invalidLevel) {
        $payload = createPayload(
            OWNER_ID,
            'inv' . $invalidLevel . random_int(100, 999),
            '081' . random_int(100000000, 999999999),
            $invalidLevel
        );
        assertSameValue(400, request('owner_create_staff.php', 'POST', $payload)['status'], "Target Level {$invalidLevel} was allowed");
    }

    foreach ([CUSTOMER_ID, CASHIER_ID, STAFF_ID, $inactiveOwnerId, 999999] as $requester) {
        $payload = createPayload(
            $requester,
            'sec' . random_int(10000, 99999),
            '080' . random_int(100000000, 999999999),
            2
        );
        assertSameValue(403, request('owner_create_staff.php', 'POST', $payload)['status'], "Requester {$requester} created staff");
    }

    $passwordStatement = $pdo->prepare('SELECT password, level, status_akun FROM users WHERE id_user = :id');
    foreach ([[$cashierCreatedId, 2], [$staffCreatedId, 3]] as [$createdId, $expectedLevel]) {
        $passwordStatement->execute(['id' => $createdId]);
        $created = $passwordStatement->fetch();
        assertSameValue($expectedLevel, (int) $created['level'], 'Created role is wrong');
        assertSameValue('aktif', $created['status_akun'], 'Created staff is not active');
        assertSameValue(false, hash_equals('Staff123', $created['password']), 'Password stored as plaintext');
        assertSameValue(true, password_verify('Staff123', $created['password']), 'Password hash verification failed');
    }

    $afterCreate = request('owner_staff.php?id_user=' . OWNER_ID)['body']['data'];
    assertSameValue((int) $baseline['jumlah_kasir'] + 2, $afterCreate['summary']['jumlah_kasir'], 'Created cashier not summarized');
    assertSameValue((int) $baseline['jumlah_staff_laundry'] + 1, $afterCreate['summary']['jumlah_staff_laundry'], 'Created laundry staff not summarized');
    assertSameValue((int) $baseline['jumlah_staff_aktif'] + 2, $afterCreate['summary']['jumlah_staff_aktif'], 'Created staff active summary is wrong');

    echo "PASS: list contains only Level 2/3, includes inactive staff, and never exposes password\n";
    echo "PASS: list summary follows cashier, laundry staff, and active definitions\n";
    echo "PASS: active Owner creates Level 2/3; duplicate username/phone and target Level 1/4/99 are rejected\n";
    echo "PASS: Level 1/2/3, inactive/unknown Owner cannot list or create staff\n";
    echo "PASS: created passwords are hashes and password_verify succeeds\n";
} finally {
    if ($fixtureUserIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureUserIds), '?'));
        $delete = $pdo->prepare("DELETE FROM users WHERE id_user IN ({$placeholders})");
        $delete->execute($fixtureUserIds);
    }
}
