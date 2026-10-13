<?php
/** Test helper: one process that verifies a receipt, run several at once by run.php. Args: root receipt.json userId */
declare(strict_types=1);
define('DIVAN', true);
define('DIVAN_TEST', true);
$src = dirname(__DIR__) . '/public';
foreach (['lib', 'migrations', 'catalog', 'domain', 'markets', 'payments', 'backup', 'installer', 'api'] as $f) require "$src/app/$f.php";
App::$root = $argv[1];
App::$config = require $argv[1] . '/storage/config.php';
$dsn = 'mysql:' . (getenv('DIVAN_TEST_DSN') ?: 'host=127.0.0.1;port=3306;dbname=divan_test2') . ';charset=utf8mb4';
$pdo = new PDO($dsn, getenv('DIVAN_TEST_USER') ?: 'divan', (string)getenv('DIVAN_TEST_PASS'), [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]);
$pdo->exec("SET time_zone = '+00:00'");
Db::use($pdo, 'dvt_');
Clock::$fixed = (int)$argv[4];
Http::$allowLocalHttpForTests = true;
$_SERVER['REMOTE_ADDR'] = '10.9.9.' . getmypid() % 250;
if (($argv[5] ?? '') === 'settle') {
    $o = PayOrders::settle(PayOrders::byCode($argv[2]));
    echo json_encode(['ok' => true, 'status' => $o['status'], 'purchase' => (int)$o['purchase_id']]);
    exit;
}
$u = Db::one('SELECT * FROM {users} WHERE id = ?', [(int)$argv[3]]);
try {
    $r = Purchases::verify($u, json_decode((string)file_get_contents($argv[2]), true));
    echo json_encode(['ok' => true, 'id' => (int)$r['purchase']['id'], 'status' => $r['purchase']['status']]);
} catch (ApiError $e) {
    echo json_encode(['ok' => false, 'code' => $e->errorCode]);
}
