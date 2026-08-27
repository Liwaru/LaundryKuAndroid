<?php
declare(strict_types=1);

$GLOBALS['laundryku_midtrans_transport'] = static function (
    string $method,
    string $path,
    ?array $payload
): array {
    $stateFile = (string) getenv('LAUNDRYKU_MIDTRANS_TEST_STATE_FILE');
    if ($stateFile === '') {
        throw new RuntimeException('Midtrans mock state file is not configured');
    }
    $state = is_file($stateFile)
        ? json_decode((string) file_get_contents($stateFile), true, 512, JSON_THROW_ON_ERROR)
        : [];

    if ($method === 'POST' && $path === '/v2/charge' && is_array($payload)) {
        $orderId = (string) ($payload['transaction_details']['order_id'] ?? '');
        $amount = number_format((float) ($payload['transaction_details']['gross_amount'] ?? 0), 2, '.', '');
        $transactionId = 'mock-' . substr(hash('sha256', $orderId), 0, 24);
        $state[$orderId] = [
            'status' => 'pending',
            'amount' => $amount,
            'transaction_id' => $transactionId,
        ];
        file_put_contents($stateFile, json_encode($state, JSON_THROW_ON_ERROR), LOCK_EX);
        return [
            'status_code' => '201',
            'transaction_id' => $transactionId,
            'order_id' => $orderId,
            'gross_amount' => $amount,
            'payment_type' => 'qris',
            'transaction_status' => 'pending',
            'expiry_time' => '2099-01-01 00:00:00',
            'actions' => [[
                'name' => 'generate-qr-code-v2',
                'method' => 'GET',
                'url' => 'https://api.sandbox.midtrans.com/v2/qris/' . $transactionId . '/qr-code',
            ]],
        ];
    }

    if ($method === 'GET' && preg_match('#^/v2/(.+)/status$#', $path, $matches) === 1) {
        $orderId = rawurldecode($matches[1]);
        $entry = $state[$orderId] ?? null;
        if (!is_array($entry)) {
            throw new RuntimeException('Mock Midtrans order was not found');
        }
        $status = (string) $entry['status'];
        $response = [
            'status_code' => '200',
            'transaction_id' => $entry['transaction_id'],
            'order_id' => $orderId,
            'gross_amount' => $entry['amount'],
            'payment_type' => 'qris',
            'transaction_status' => $status,
            'fraud_status' => 'accept',
        ];
        if ($status === 'settlement') {
            $response['settlement_time'] = date('Y-m-d') . ' 20:00:00';
        }
        return $response;
    }

    throw new RuntimeException("Unexpected Midtrans mock request {$method} {$path}");
};
