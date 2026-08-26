<?php
declare(strict_types=1);

/**
 * Read a small, conventional dotenv file without exporting its values globally.
 * Existing process/Apache environment variables always take precedence.
 *
 * @return array<string, string>
 */
function laundrykuReadEnvFile(string $path): array
{
    if (!is_file($path) || !is_readable($path)) {
        return [];
    }

    $lines = file($path, FILE_IGNORE_NEW_LINES);
    if ($lines === false) {
        return [];
    }

    $values = [];
    foreach ($lines as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) {
            continue;
        }
        if (str_starts_with($line, 'export ')) {
            $line = ltrim(substr($line, 7));
        }
        if (preg_match('/^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/', $line, $matches) !== 1) {
            continue;
        }

        $value = trim($matches[2]);
        $length = strlen($value);
        if ($length >= 2 && (($value[0] === '"' && $value[$length - 1] === '"') || ($value[0] === "'" && $value[$length - 1] === "'"))) {
            $value = substr($value, 1, -1);
        } else {
            $value = (string) preg_replace('/\s+#.*$/', '', $value);
        }
        $values[$matches[1]] = $value;
    }

    return $values;
}

function laundrykuEnvironment(string $name, ?string $default = null): ?string
{
    $processValue = getenv($name);
    if ($processValue !== false) {
        return (string) $processValue;
    }
    if (array_key_exists($name, $_ENV)) {
        return (string) $_ENV[$name];
    }
    if (array_key_exists($name, $_SERVER)) {
        return (string) $_SERVER[$name];
    }

    static $fileValues = null;
    if ($fileValues === null) {
        $fileValues = laundrykuReadEnvFile(dirname(__DIR__) . DIRECTORY_SEPARATOR . '.env');
    }

    return array_key_exists($name, $fileValues) ? $fileValues[$name] : $default;
}
