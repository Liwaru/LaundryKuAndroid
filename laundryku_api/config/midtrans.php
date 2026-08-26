<?php
declare(strict_types=1);

final class MidtransConfigurationException extends RuntimeException
{
}
final class MidtransRequestException extends RuntimeException
{
    public function __construct(string $message, public readonly int $httpStatus = 0)
    {
        parent::__construct($message);
    }
}

function midtransServerKey(): string
{
    $key = trim((string) getenv('LAUNDRYKU_MIDTRANS_SERVER_KEY'));
    if ($key === '') {
        throw new MidtransConfigurationException('Midtrans Server Key is not configured');
    }
    return $key;
}

function midtransBaseUrl(): string
{
    $environment = strtolower(trim((string) (getenv('LAUNDRYKU_MIDTRANS_ENV') ?: 'sandbox')));
    if ($environment !== 'sandbox') {
        throw new MidtransConfigurationException('Only Midtrans Sandbox is enabled');
    }
    return 'https://api.sandbox.midtrans.com';
}

function midtransRequest(string $method, string $path, ?array $payload = null): array
{
    $override = $GLOBALS['laundryku_midtrans_transport'] ?? null;
    if (is_callable($override)) {
        $response = $override($method, $path, $payload);
        if (!is_array($response)) {
            throw new MidtransRequestException('Invalid Midtrans test transport response');
        }
        return $response;
    }

    $url = midtransBaseUrl() . $path;
    $handle = curl_init($url);
    if ($handle === false) {
        throw new MidtransRequestException('Unable to initialize Midtrans request');
    }

    $headers = [
        'Accept: application/json',
        'Content-Type: application/json',
        'Authorization: Basic ' . base64_encode(midtransServerKey() . ':'),
    ];
    $options = [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_CUSTOMREQUEST => strtoupper($method),
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_CONNECTTIMEOUT => 8,
        CURLOPT_TIMEOUT => 20,
        CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
    ];
    if ($payload !== null) {
        $options[CURLOPT_POSTFIELDS] = json_encode($payload, JSON_THROW_ON_ERROR);
    }
    curl_setopt_array($handle, $options);

    $body = curl_exec($handle);
    if ($body === false) {
        $message = curl_error($handle);
        curl_close($handle);
        throw new MidtransRequestException('Midtrans network request failed: ' . $message);
    }
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);

    try {
        $decoded = json_decode($body, true, 512, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        throw new MidtransRequestException('Midtrans returned an invalid response', $status);
    }
    if (!is_array($decoded)) {
        throw new MidtransRequestException('Midtrans returned an invalid response', $status);
    }
    if ($status < 200 || $status >= 300) {
        throw new MidtransRequestException('Midtrans rejected the request', $status);
    }
    return $decoded;
}

function midtransCreateQris(array $payload): array
{
    return midtransRequest('POST', '/v2/charge', $payload);
}

function midtransGetStatus(string $orderId): array
{
    if ($orderId === '') {
        throw new InvalidArgumentException('Midtrans order ID is required');
    }
    return midtransRequest('GET', '/v2/' . rawurlencode($orderId) . '/status');
}

function midtransNotificationSignatureIsValid(array $notification): bool
{
    foreach (['order_id', 'status_code', 'gross_amount', 'signature_key'] as $field) {
        if (!isset($notification[$field]) || !is_string($notification[$field])) {
            return false;
        }
    }
    $expected = hash(
        'sha512',
        $notification['order_id'] .
        $notification['status_code'] .
        $notification['gross_amount'] .
        midtransServerKey()
    );
    return hash_equals($expected, strtolower($notification['signature_key']));
}

function midtransQrisUrl(array $actions): ?string
{
    foreach ($actions as $action) {
        if (!is_array($action) || !in_array($action['name'] ?? null, ['generate-qr-code', 'generate-qr-code-v2'], true)) {
            continue;
        }
        $url = (string) ($action['url'] ?? '');
        $parts = parse_url($url);
        if (($parts['scheme'] ?? '') !== 'https' || ($parts['host'] ?? '') !== 'api.sandbox.midtrans.com') {
            continue;
        }
        return $url;
    }
    return null;
}
