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

function requestCustomers(int $userId): array
{
    $handle = curl_init(BASE_URL . 'cashier_customers.php?id_user=' . $userId);
    curl_setopt_array($handle, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10]);
    $body = curl_exec($handle);
    if ($body === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($body, true, 512, JSON_THROW_ON_ERROR)];
}

function insertUser(
    PDO $pdo,
    string $name,
    string $username,
    string $phone,
    int $level,
    string $status
): int {
    $statement = $pdo->prepare(
        'INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         SELECT :name, :phone, :username, password, :level, :status
         FROM users WHERE id_user = :cashier'
    );
    $statement->execute([
        'name' => $name,
        'phone' => $phone,
        'username' => $username,
        'level' => $level,
        'status' => $status,
        'cashier' => CASHIER_ID,
    ]);
    return (int) $pdo->lastInsertId();
}

function insertTransaction(PDO $pdo, int $customerId, string $suffix, string $status, ?int $cashierId): int
{
    $statement = $pdo->prepare(
        'INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, id_kasir, tanggal_masuk, total_harga,
          status_laundry, status_pembayaran)
         VALUES (:code, :customer, :cashier, NOW(), 10000, :status, :payment)'
    );
    $statement->execute([
        'code' => 'CUSTTEST' . $suffix . bin2hex(random_bytes(2)),
        'customer' => $customerId,
        'cashier' => $cashierId,
        'status' => $status,
        'payment' => $status === 'selesai' ? 'sudah_dibayar' : 'belum_dibayar',
    ]);
    return (int) $pdo->lastInsertId();
}

function customerById(array $customers, int $userId): ?array
{
    foreach ($customers as $customer) {
        if ((int) $customer['id_user'] === $userId) {
            return $customer;
        }
    }
    return null;
}

function containsKeyRecursively(array $value, string $forbiddenKey): bool
{
    foreach ($value as $key => $item) {
        if ((string) $key === $forbiddenKey) return true;
        if (is_array($item) && containsKeyRecursively($item, $forbiddenKey)) return true;
    }
    return false;
}

$pdo = getDatabaseConnection();
$fixtureUserIds = [];
$fixtureTransactionIds = [];
$temporarilyChangedCustomerIds = [];

try {
    $originalCustomers = $pdo->query('SELECT id_user FROM users WHERE level = 1')->fetchAll(PDO::FETCH_COLUMN);
    $temporarilyChangedCustomerIds = array_map('intval', $originalCustomers);
    if ($temporarilyChangedCustomerIds !== []) {
        $placeholders = implode(',', array_fill(0, count($temporarilyChangedCustomerIds), '?'));
        $changeLevel = $pdo->prepare("UPDATE users SET level = 2 WHERE id_user IN ({$placeholders})");
        $changeLevel->execute($temporarilyChangedCustomerIds);
    }
    $emptyResponse = requestCustomers(CASHIER_ID);
    assertSameValue(200, $emptyResponse['status'], 'Empty customer request failed');
    assertSameValue(0, $emptyResponse['body']['data']['total_pelanggan'], 'Empty total is not zero');
    assertSameValue([], $emptyResponse['body']['data']['customers'], 'Empty list is not returned');
    if ($temporarilyChangedCustomerIds !== []) {
        $placeholders = implode(',', array_fill(0, count($temporarilyChangedCustomerIds), '?'));
        $restoreLevel = $pdo->prepare("UPDATE users SET level = 1 WHERE id_user IN ({$placeholders})");
        $restoreLevel->execute($temporarilyChangedCustomerIds);
        $temporarilyChangedCustomerIds = [];
    }

    $baselineResponse = requestCustomers(CASHIER_ID);
    assertSameValue(200, $baselineResponse['status'], 'Level 2 could not load customers');
    $baselineTotal = (int) $baselineResponse['body']['data']['total_pelanggan'];

    foreach ([CUSTOMER_ID, STAFF_ID, OWNER_ID] as $forbiddenUser) {
        assertSameValue(403, requestCustomers($forbiddenUser)['status'], "Role {$forbiddenUser} was allowed");
    }
    assertSameValue(403, requestCustomers(999999)['status'], 'Unknown user was allowed');

    $stamp = random_int(1000, 9999);
    $inactiveCashier = $fixtureUserIds[] = insertUser(
        $pdo,
        'No Kasir',
        'off' . $stamp,
        '087' . random_int(100000000, 999999999),
        2,
        'nonaktif'
    );
    assertSameValue(403, requestCustomers($inactiveCashier)['status'], 'Inactive cashier was allowed');

    $customerA = $fixtureUserIds[] = insertUser(
        $pdo,
        'A Test',
        'custa' . $stamp,
        '085' . random_int(100000000, 999999999),
        1,
        'aktif'
    );
    $customerB = $fixtureUserIds[] = insertUser(
        $pdo,
        'Z Test',
        'custb' . $stamp,
        '086' . random_int(100000000, 999999999),
        1,
        'nonaktif'
    );
    $fixtureTransactionIds[] = insertTransaction($pdo, $customerA, 'ACTIVE', 'menunggu', CASHIER_ID);
    $fixtureTransactionIds[] = insertTransaction($pdo, $customerA, 'DONE', 'selesai', CASHIER_ID);
    $fixtureTransactionIds[] = insertTransaction($pdo, $customerA, 'CANCEL', 'dibatalkan', null);

    $response = requestCustomers(CASHIER_ID);
    assertSameValue(200, $response['status'], 'Fixture request failed');
    $data = $response['body']['data'];
    assertSameValue($baselineTotal + 2, $data['total_pelanggan'], 'Total customers is wrong');
    $rowA = customerById($data['customers'], $customerA);
    $rowB = customerById($data['customers'], $customerB);
    if ($rowA === null || $rowB === null) {
        throw new RuntimeException('Fixture customers are missing');
    }
    assertSameValue(3, (int) $rowA['total_transaksi'], 'All transaction statuses were not counted');
    assertSameValue(0, (int) $rowB['total_transaksi'], 'Customer without transaction is not zero');
    assertSameValue('nonaktif', $rowB['status_akun'], 'Inactive Level 1 customer is missing');
    foreach ([CASHIER_ID, STAFF_ID, OWNER_ID, $inactiveCashier] as $nonCustomerId) {
        assertSameValue(null, customerById($data['customers'], $nonCustomerId), "Non-customer {$nonCustomerId} leaked");
    }
    $names = array_column($data['customers'], 'nama');
    $sortedNames = $names;
    natcasesort($sortedNames);
    assertSameValue(array_values($sortedNames), $names, 'Customers are not ordered by name');
    assertSameValue(false, containsKeyRecursively($response['body'], 'password'), 'Password field leaked');

    echo "PASS: only Level 1 accounts are returned and total includes active/nonactive customers\n";
    echo "PASS: active, completed and cancelled transactions count by id_pelanggan\n";
    echo "PASS: customer without transactions returns zero and list is ordered by name\n";
    echo "PASS: Level 1/3/4, inactive cashier and unknown user receive HTTP 403\n";
    echo "PASS: empty database state returns zero/list and password is never exposed\n";
} finally {
    if ($fixtureTransactionIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureTransactionIds), '?'));
        $deleteTransactions = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
        $deleteTransactions->execute($fixtureTransactionIds);
    }
    if ($fixtureUserIds !== []) {
        $placeholders = implode(',', array_fill(0, count($fixtureUserIds), '?'));
        $deleteUsers = $pdo->prepare("DELETE FROM users WHERE id_user IN ({$placeholders})");
        $deleteUsers->execute($fixtureUserIds);
    }
    if ($temporarilyChangedCustomerIds !== []) {
        $placeholders = implode(',', array_fill(0, count($temporarilyChangedCustomerIds), '?'));
        $restoreLevel = $pdo->prepare("UPDATE users SET level = 1 WHERE id_user IN ({$placeholders})");
        $restoreLevel->execute($temporarilyChangedCustomerIds);
    }
}
