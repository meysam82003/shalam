<?php
/**
 * Test-only server for the Divan server's end-to-end checks:  php -S 127.0.0.1:PORT http_router.php
 * Runs the real gateway with fake stores (the purchase token decides the answer). Never deploy this file:
 * it lives in the private folder, outside the public folder.
 */
declare(strict_types=1);
if (PHP_SAPI !== 'cli-server') { http_response_code(404); exit; }
require __DIR__ . '/../src/Bridge.php';

$cfgFile = (string)getenv('DIVAN_BRIDGE_TEST_CFG');
$cfg = json_decode((string)file_get_contents($cfgFile), true);
\DivanBridge\Bridge::$transport = function (string $m, string $url, array $h, ?string $b, int $t) use ($cfgFile) {
    file_put_contents($cfgFile . '.calls', json_encode([$m, $url]) . "\n", FILE_APPEND | LOCK_EX);
    $tok = (string)(json_decode((string)$b, true)['tokenId'] ?? basename(rtrim(explode('?', $url)[0], '/')));
    $ms = 1800000000000;
    if (strpos($url, '/products?') !== false) return ['status' => 200, 'body' => '{"skus":[{"sku":"divan_pro_1m","price":190000}]}', 'error' => ''];
    if (strpos($tok, 'probe') === 0) return ['status' => 404, 'body' => '{}', 'error' => ''];
    if (strpos($tok, 'slowvalid') === 0) { usleep(800000); return ['status' => 200, 'body' => json_encode(['purchaseState' => 0, 'consumptionState' => 1, 'purchaseTime' => $ms]), 'error' => '']; }
    if (strpos($tok, 'sub') === 0) return ['status' => 200, 'body' => json_encode(['initiationTimestampMsec' => $ms, 'validUntilTimestampMsec' => $ms + 30 * 86400000, 'autoRenewing' => true]), 'error' => ''];
    if (strpos($tok, 'valid') === 0) return ['status' => 200, 'body' => json_encode(['purchaseState' => 0, 'consumptionState' => 1, 'purchaseTime' => $ms]), 'error' => ''];
    if (strpos($tok, 'refund') === 0) return ['status' => 200, 'body' => '{"purchaseState":1}', 'error' => ''];
    if (strpos($tok, 'cancel') === 0) return ['status' => 200, 'body' => '{"purchaseState":2}', 'error' => ''];
    if (strpos($tok, 'unknown') === 0) return ['status' => 404, 'body' => '{}', 'error' => ''];
    if (strpos($tok, 'denied') === 0) return ['status' => 401, 'body' => '{}', 'error' => ''];
    if (strpos($tok, 'forbid') === 0) return ['status' => 403, 'body' => '{}', 'error' => ''];
    if (strpos($tok, 'slow') === 0) return ['status' => 0, 'body' => '', 'error' => 'Operation timed out after 20000 milliseconds'];
    if (strpos($tok, 'down') === 0) return ['status' => 503, 'body' => '', 'error' => ''];
    return ['status' => 500, 'body' => '', 'error' => ''];
};
if (is_file($cfgFile . '.tamper')) {
    // A man in the middle changes the answer after it was signed.
    ob_start();
    \DivanBridge\run($cfg, (string)$cfg['__data']);
    echo str_replace('"purchaseState":1', '"purchaseState":0', (string)ob_get_clean());
    exit;
}
\DivanBridge\run($cfg, (string)$cfg['__data']);
