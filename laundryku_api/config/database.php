<?php
declare(strict_types=1);

function getDatabaseConnection(): PDO
{
    $host = getenv('LAUNDRYKU_DB_HOST') ?: '127.0.0.1';
    $port = getenv('LAUNDRYKU_DB_PORT') ?: '3306';
    $database = getenv('LAUNDRYKU_DB_NAME') ?: 'laundryku';
    $username = getenv('LAUNDRYKU_DB_USER') ?: 'root';
    $password = getenv('LAUNDRYKU_DB_PASSWORD');

    if ($password === false) {
        $password = '';
    }

    $dsn = "mysql:host={$host};port={$port};dbname={$database};charset=utf8mb4";

    return new PDO($dsn, $username, $password, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
}
