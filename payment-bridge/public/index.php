<?php
/**
 * Divan Emtiaz payment gateway — the only public file. Everything else (code, keys, logs) lives in the
 * private folder «divan-bridge» next to (not inside) this public folder.
 */
declare(strict_types=1);

$candidates = [];
if (is_file(__DIR__ . '/bridge-path.php')) $candidates[] = (string)require __DIR__ . '/bridge-path.php';
$candidates[] = dirname(__DIR__) . '/divan-bridge';
$candidates[] = dirname(__DIR__, 2) . '/divan-bridge';

foreach ($candidates as $dir) {
    if ($dir !== '' && is_file($dir . '/src/Bridge.php') && is_file($dir . '/config.php')) {
        require $dir . '/src/Bridge.php';
        $cfg = require $dir . '/config.php';
        \DivanBridge\run(is_array($cfg) ? $cfg : [], $dir . '/data');
        exit;
    }
}
http_response_code(503);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
echo '{"ok":false,"error":"not_installed"}';
