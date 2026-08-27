<?php
declare(strict_types=1);

require_once __DIR__ . '/auth_test_helper.php';

const BASE_URL = 'http://127.0.0.1/laundryku_api/api/';
const CUSTOMER_ID = 1;
const CASHIER_ID = 2;
const STAFF_ID = 3;
const OWNER_ID = 4;

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . '; expected=' . json_encode($expected) . ', actual=' . json_encode($actual));
    }
}

function requestDetail(string $endpoint, string $parameter, int $targetId, ?int $requesterId): array
{
    $handle = curl_init(BASE_URL . $endpoint . '?' . http_build_query([$parameter => $targetId]));
    $options = [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10];
    if ($requesterId !== null) {
        $options[CURLOPT_HTTPHEADER] = testAuthHeaders($requesterId);
    }
    curl_setopt_array($handle, $options);
    $raw = curl_exec($handle);
    if ($raw === false) {
        throw new RuntimeException('HTTP request failed: ' . curl_error($handle));
    }
    $status = curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);
    return ['status' => $status, 'body' => json_decode($raw, true, 512, JSON_THROW_ON_ERROR)];
}

function containsSensitiveKey(mixed $value): bool
{
    if (!is_array($value)) {
        return false;
    }
    foreach ($value as $key => $child) {
        if (is_string($key) && in_array(strtolower($key), ['password', 'token', 'token_hash', 'auth_token'], true)) {
            return true;
        }
        if (containsSensitiveKey($child)) {
            return true;
        }
    }
    return false;
}

assertSameValue(401, requestDetail('cashier_customer_detail.php', 'id_pelanggan', CUSTOMER_ID, null)['status'], 'Customer detail allowed no token');
foreach ([CUSTOMER_ID, STAFF_ID, OWNER_ID] as $forbiddenRole) {
    assertSameValue(403, requestDetail('cashier_customer_detail.php', 'id_pelanggan', CUSTOMER_ID, $forbiddenRole)['status'], "Role {$forbiddenRole} accessed cashier customer detail");
}
$customerDetail = requestDetail('cashier_customer_detail.php', 'id_pelanggan', CUSTOMER_ID, CASHIER_ID);
assertSameValue(200, $customerDetail['status'], 'Cashier could not access a Level 1 customer');
assertSameValue(CUSTOMER_ID, (int) $customerDetail['body']['data']['customer']['id_user'], 'Wrong customer returned');
assertSameValue(true, array_key_exists('total_pengeluaran', $customerDetail['body']['data']['customer']), 'Customer spending summary missing');
assertSameValue(true, array_key_exists('created_at', $customerDetail['body']['data']['customer']), 'Customer join date missing');
assertSameValue(false, containsSensitiveKey($customerDetail['body']), 'Customer detail exposed sensitive data');
assertSameValue(true, count($customerDetail['body']['data']['recent_transactions']) <= 3, 'Recent transaction limit exceeded');
foreach ([CASHIER_ID, STAFF_ID, OWNER_ID] as $invalidTarget) {
    assertSameValue(404, requestDetail('cashier_customer_detail.php', 'id_pelanggan', $invalidTarget, CASHIER_ID)['status'], "Cashier accessed non-customer target {$invalidTarget}");
}

assertSameValue(401, requestDetail('owner_staff_detail.php', 'id_staff', CASHIER_ID, null)['status'], 'Staff detail allowed no token');
foreach ([CUSTOMER_ID, CASHIER_ID, STAFF_ID] as $forbiddenRole) {
    assertSameValue(403, requestDetail('owner_staff_detail.php', 'id_staff', CASHIER_ID, $forbiddenRole)['status'], "Role {$forbiddenRole} accessed owner staff detail");
}
foreach ([CASHIER_ID, STAFF_ID] as $validTarget) {
    $staffDetail = requestDetail('owner_staff_detail.php', 'id_staff', $validTarget, OWNER_ID);
    assertSameValue(200, $staffDetail['status'], "Owner could not access staff target {$validTarget}");
    assertSameValue($validTarget, (int) $staffDetail['body']['data']['staff']['id_user'], 'Wrong staff returned');
    assertSameValue(false, containsSensitiveKey($staffDetail['body']), 'Staff detail exposed sensitive data');
}
foreach ([CUSTOMER_ID, OWNER_ID] as $invalidTarget) {
    assertSameValue(404, requestDetail('owner_staff_detail.php', 'id_staff', $invalidTarget, OWNER_ID)['status'], "Owner accessed invalid staff target {$invalidTarget}");
}

echo "PASS: cashier customer detail is Level 2 only and targets only Level 1\n";
echo "PASS: owner staff detail is Level 4 only and targets only Levels 2/3\n";
echo "PASS: detail responses contain no password/token and recent customer transactions are limited to three\n";
