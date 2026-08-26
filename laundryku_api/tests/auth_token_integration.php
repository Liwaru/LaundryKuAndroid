<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/database.php';

const BASE_URL = 'http://127.0.0.1/laundryku_api/api/';

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException(
            $message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual)
        );
    }
}

function requestApi(string $method, string $path, ?array $body = null, ?string $token = null): array
{
    $handle = curl_init(BASE_URL . $path);
    $headers = ['Content-Type: application/json'];
    if ($token !== null) {
        $headers[] = 'Authorization: Bearer ' . $token;
    }
    curl_setopt_array($handle, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_TIMEOUT => 10,
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_HTTPHEADER => $headers,
    ]);
    if ($body !== null) {
        curl_setopt($handle, CURLOPT_POSTFIELDS, json_encode($body, JSON_THROW_ON_ERROR));
    }
    $response = curl_exec($handle);
    if ($response === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return [
        'status' => $status,
        'body' => json_decode($response, true, 512, JSON_THROW_ON_ERROR),
    ];
}

function login(string $username, string $password): array
{
    $response = requestApi('POST', 'login.php', ['username' => $username, 'password' => $password]);
    assertSameValue(200, $response['status'], "Login {$username} failed");
    $token = $response['body']['data']['token'] ?? null;
    if (!is_string($token) || preg_match('/^[a-f0-9]{64}$/', $token) !== 1) {
        throw new RuntimeException("Login {$username} did not return a secure token");
    }
    return $response['body']['data'];
}

function insertToken(PDO $pdo, int $userId, string $rawToken, string $expiry, ?string $revoked = null): void
{
    $statement = $pdo->prepare(
        'INSERT INTO auth_tokens (id_user, token_hash, expires_at, revoked_at)
         VALUES (:user, :hash, :expiry, :revoked)'
    );
    $statement->execute([
        'user' => $userId,
        'hash' => hash('sha256', $rawToken),
        'expiry' => $expiry,
        'revoked' => $revoked,
    ]);
}

function createTransaction(PDO $pdo, int $customerId, string $suffix): int
{
    $statement = $pdo->prepare(
        "INSERT INTO transaksi
         (kode_transaksi, id_pelanggan, estimasi_selesai, total_harga, status_laundry, status_pembayaran)
         VALUES (:code, :customer, NOW() + INTERVAL 2 DAY, 50000, 'menunggu', 'belum_dibayar')"
    );
    $statement->execute(['code' => 'AUTH' . $suffix . bin2hex(random_bytes(2)), 'customer' => $customerId]);
    $transactionId = (int) $pdo->lastInsertId();
    $detail = $pdo->prepare(
        'INSERT INTO detail_transaksi (id_transaksi, id_layanan, qty, harga_satuan, subtotal)
         VALUES (:transaction, 2, 5, 10000, 50000)'
    );
    $detail->execute(['transaction' => $transactionId]);
    return $transactionId;
}

function deleteTransactions(PDO $pdo, array $ids): void
{
    if ($ids === []) {
        return;
    }
    $placeholders = implode(',', array_fill(0, count($ids), '?'));
    foreach (['riwayat_status', 'pembayaran', 'detail_transaksi'] as $table) {
        $statement = $pdo->prepare("DELETE FROM {$table} WHERE id_transaksi IN ({$placeholders})");
        $statement->execute($ids);
    }
    $statement = $pdo->prepare("DELETE FROM transaksi WHERE id_transaksi IN ({$placeholders})");
    $statement->execute($ids);
}

$pdo = getDatabaseConnection();
$fixtureTransactionIds = [];
$fixtureUserIds = [];

try {
    $pdo->exec('DELETE FROM auth_tokens');

    $customer = login('pelanggan', 'pelanggan');
    $cashier = login('kasir', 'kasir');
    $staff = login('staff', 'staff');
    $owner = login('owner', 'owner');

    assertSameValue(1, (int) $customer['level'], 'Customer login role changed');
    assertSameValue(2, (int) $cashier['level'], 'Cashier login role changed');
    assertSameValue(3, (int) $staff['level'], 'Staff login role changed');
    assertSameValue(4, (int) $owner['level'], 'Owner login role changed');

    $stored = $pdo->prepare('SELECT token_hash FROM auth_tokens WHERE id_user = :user AND revoked_at IS NULL');
    $stored->execute(['user' => 1]);
    $storedHash = (string) $stored->fetchColumn();
    assertSameValue(hash('sha256', $customer['token']), $storedHash, 'Database token hash is wrong');
    assertSameValue(false, $storedHash === $customer['token'], 'Raw token was stored in database');

    assertSameValue(401, requestApi('GET', 'customer_orders.php')['status'], 'Missing token was accepted');
    assertSameValue(401, requestApi('GET', 'customer_orders.php', null, str_repeat('a', 64))['status'], 'Invalid token was accepted');

    $expiredToken = bin2hex(random_bytes(32));
    insertToken($pdo, 1, $expiredToken, '2000-01-01 00:00:00');
    assertSameValue(401, requestApi('GET', 'customer_orders.php', null, $expiredToken)['status'], 'Expired token was accepted');

    $revokedToken = bin2hex(random_bytes(32));
    insertToken($pdo, 1, $revokedToken, '2099-01-01 00:00:00', date('Y-m-d H:i:s'));
    assertSameValue(401, requestApi('GET', 'customer_orders.php', null, $revokedToken)['status'], 'Revoked token was accepted');

    assertSameValue(200, requestApi('GET', 'customer_orders.php', null, $customer['token'])['status'], 'Customer endpoint rejected Customer');
    assertSameValue(200, requestApi('GET', 'cashier_dashboard.php', null, $cashier['token'])['status'], 'Cashier endpoint rejected Cashier');
    assertSameValue(200, requestApi('GET', 'staff_jobs.php', null, $staff['token'])['status'], 'Staff endpoint rejected Staff');
    assertSameValue(200, requestApi('GET', 'owner_reports.php?period=today', null, $owner['token'])['status'], 'Owner endpoint rejected Owner');

    foreach (['cashier_dashboard.php', 'staff_jobs.php', 'owner_reports.php?period=today'] as $path) {
        assertSameValue(403, requestApi('GET', $path, null, $customer['token'])['status'], "Customer accessed {$path}");
    }
    assertSameValue(403, requestApi('GET', 'staff_jobs.php', null, $cashier['token'])['status'], 'Cashier accessed Staff endpoint');
    assertSameValue(403, requestApi('GET', 'owner_reports.php?period=today', null, $cashier['token'])['status'], 'Cashier accessed Owner endpoint');

    $customerB = $pdo->prepare(
        "INSERT INTO users (nama, no_hp, username, password, level, status_akun)
         VALUES ('AuthB', :phone, :username, :password, 1, 'aktif')"
    );
    $customerB->execute([
        'phone' => '087' . random_int(100000000, 999999999),
        'username' => 'authb' . random_int(1000, 9999),
        'password' => password_hash('temporary', PASSWORD_DEFAULT),
    ]);
    $customerBId = (int) $pdo->lastInsertId();
    $fixtureUserIds[] = $customerBId;
    $customerBTransaction = $fixtureTransactionIds[] = createTransaction($pdo, $customerBId, 'B');

    $spoofedList = requestApi('GET', "customer_orders.php?id_user={$customerBId}", null, $customer['token']);
    assertSameValue(200, $spoofedList['status'], 'Customer spoof list request failed unexpectedly');
    assertSameValue(false, in_array($customerBTransaction, array_column($spoofedList['body']['data'], 'id_transaksi'), true), 'Customer B order leaked to Customer A');
    assertSameValue(404, requestApi('GET', "customer_order_detail.php?id_transaksi={$customerBTransaction}&id_user={$customerBId}", null, $customer['token'])['status'], 'Customer A accessed Customer B detail');

    $created = requestApi('POST', 'create_order.php', [
        'id_user' => $customerBId,
        'id_layanan' => 2,
        'qty' => 5,
    ], $customer['token']);
    if (isset($created['body']['data']['id_transaksi'])) {
        $fixtureTransactionIds[] = (int) $created['body']['data']['id_transaksi'];
    }
    assertSameValue(200, $created['status'], 'Authenticated Customer order creation failed');
    $customerATransaction = (int) $created['body']['data']['id_transaksi'];
    $ownerCheck = $pdo->prepare('SELECT id_pelanggan FROM transaksi WHERE id_transaksi = :id');
    $ownerCheck->execute(['id' => $customerATransaction]);
    assertSameValue(1, (int) $ownerCheck->fetchColumn(), 'create_order trusted spoofed id_user');

    $cashSelection = requestApi('POST', 'select_cash_payment.php', [
        'id_user' => $customerBId,
        'id_transaksi' => $customerATransaction,
    ], $customer['token']);
    assertSameValue(200, $cashSelection['status'], 'Customer cash selection failed');

    $confirmation = requestApi('POST', 'confirm_cash_payment.php', [
        'id_user' => 999,
        'id_transaksi' => $customerATransaction,
    ], $cashier['token']);
    assertSameValue(200, $confirmation['status'], 'Cashier confirmation failed');
    $cashierCheck = $pdo->prepare('SELECT id_kasir FROM transaksi WHERE id_transaksi = :id');
    $cashierCheck->execute(['id' => $customerATransaction]);
    assertSameValue(2, (int) $cashierCheck->fetchColumn(), 'Cashier audit identity trusted request body');

    $staffTransaction = $fixtureTransactionIds[] = createTransaction($pdo, 1, 'S');
    $staffUpdate = requestApi('POST', 'update_laundry_status.php', [
        'id_user' => 999,
        'id_transaksi' => $staffTransaction,
        'current_status' => 'menunggu',
    ], $staff['token']);
    assertSameValue(200, $staffUpdate['status'], 'Staff update failed');
    $historyCheck = $pdo->prepare(
        'SELECT id_user FROM riwayat_status WHERE id_transaksi = :id ORDER BY id_riwayat DESC LIMIT 1'
    );
    $historyCheck->execute(['id' => $staffTransaction]);
    assertSameValue(3, (int) $historyCheck->fetchColumn(), 'Staff audit identity trusted request body');

    $createdStaff = requestApi('POST', 'owner_create_staff.php', [
        'id_user' => 1,
        'nama' => 'AuthKasir',
        'no_hp' => '086' . random_int(100000000, 999999999),
        'username' => 'authk' . random_int(1000, 9999),
        'password' => 'temporary',
        'level' => 2,
    ], $owner['token']);
    if (isset($createdStaff['body']['data']['id_user'])) {
        $fixtureUserIds[] = (int) $createdStaff['body']['data']['id_user'];
    }
    assertSameValue(201, $createdStaff['status'], 'Owner could not create Staff');
    assertSameValue(403, requestApi('POST', 'owner_create_staff.php', [
        'nama' => 'Denied', 'no_hp' => '085111111111', 'username' => 'deniedauth',
        'password' => 'temporary', 'level' => 2,
    ], $customer['token'])['status'], 'Customer created Staff');

    $oldCustomerToken = $customer['token'];
    $customer = login('pelanggan', 'pelanggan');
    assertSameValue(401, requestApi('GET', 'customer_orders.php', null, $oldCustomerToken)['status'], 'New login did not revoke old token');
    assertSameValue(200, requestApi('GET', 'customer_orders.php', null, $customer['token'])['status'], 'New single-session token failed');

    assertSameValue(200, requestApi('POST', 'logout.php', [], $owner['token'])['status'], 'Logout failed');
    assertSameValue(401, requestApi('GET', 'owner_dashboard.php', null, $owner['token'])['status'], 'Logged-out token remained valid');

    echo "PASS: login returns 64-hex raw token once and database stores only SHA-256 hash\n";
    echo "PASS: missing, invalid, expired, revoked and logged-out tokens return HTTP 401\n";
    echo "PASS: Customer, Cashier, Staff and Owner role authorization is enforced from token identity\n";
    echo "PASS: Customer list/detail/create/payment identity spoofing is blocked\n";
    echo "PASS: Cashier and Staff audit identities ignore manipulated id_user fields\n";
    echo "PASS: Owner create Staff authorization and single-session revocation work\n";
} finally {
    deleteTransactions($pdo, $fixtureTransactionIds);
    $pdo->exec('DELETE FROM auth_tokens');
    foreach (array_reverse($fixtureUserIds) as $userId) {
        $statement = $pdo->prepare('DELETE FROM users WHERE id_user = :id');
        $statement->execute(['id' => $userId]);
    }
}
