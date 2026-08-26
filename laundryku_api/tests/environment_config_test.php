<?php
declare(strict_types=1);

require_once __DIR__ . '/../config/environment.php';
require_once __DIR__ . '/../config/midtrans.php';

function assertEnvironmentValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message);
    }
}

$temporaryFile = tempnam(sys_get_temp_dir(), 'laundryku-env-');
if ($temporaryFile === false) {
    throw new RuntimeException('Unable to create temporary dotenv fixture');
}

try {
    file_put_contents(
        $temporaryFile,
        "# local configuration\n" .
        "LAUNDRYKU_MIDTRANS_SERVER_KEY=local-test-value\n" .
        "LAUNDRYKU_MIDTRANS_ENV=\"sandbox\"\n" .
        "IGNORED_INVALID_LINE\n"
    );
    $values = laundrykuReadEnvFile($temporaryFile);
    assertEnvironmentValue('local-test-value', $values['LAUNDRYKU_MIDTRANS_SERVER_KEY'] ?? null, 'Dotenv key was not parsed');
    assertEnvironmentValue('sandbox', $values['LAUNDRYKU_MIDTRANS_ENV'] ?? null, 'Quoted dotenv value was not parsed');

    putenv('LAUNDRYKU_MIDTRANS_SERVER_KEY=process-test-value');
    assertEnvironmentValue('process-test-value', laundrykuEnvironment('LAUNDRYKU_MIDTRANS_SERVER_KEY'), 'Process environment must take precedence');

    putenv('LAUNDRYKU_MIDTRANS_ENV=sandbox');
    assertEnvironmentValue('https://api.sandbox.midtrans.com', midtransBaseUrl(), 'Sandbox URL is incorrect');

    putenv('LAUNDRYKU_MIDTRANS_ENV=production');
    try {
        midtransBaseUrl();
        throw new RuntimeException('Production environment was not rejected');
    } catch (MidtransConfigurationException $exception) {
        assertEnvironmentValue('Only Midtrans Sandbox is enabled', $exception->getMessage(), 'Unexpected production rejection');
    }
} finally {
    putenv('LAUNDRYKU_MIDTRANS_SERVER_KEY');
    putenv('LAUNDRYKU_MIDTRANS_ENV');
    if (is_file($temporaryFile)) {
        unlink($temporaryFile);
    }
}

echo "Environment config tests passed\n";
