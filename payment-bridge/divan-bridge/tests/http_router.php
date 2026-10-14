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
    // Aban Gateway: the real bridge's request is passed on, over local HTTP, to the fake Aban of the server tests.
    $aban = (string)getenv('FAKE_ABAN_URL');
    if ($aban !== '' && preg_grep('/^Authorization: Bearer ag_/', $h)) {
        $local = preg_replace('#^https://[^/]+#', $aban, $url);
        $ch = curl_init($local);
        curl_setopt_array($ch, [CURLOPT_CUSTOMREQUEST => $m, CURLOPT_RETURNTRANSFER => true, CURLOPT_HTTPHEADER => $h, CURLOPT_TIMEOUT => 10]);
        if ($b !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, $b);
        $resp = curl_exec($ch);
        $st = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        curl_close($ch);
        return ['status' => $resp === false ? 0 : $st, 'body' => (string)$resp, 'error' => $resp === false ? 'Operation timed out' : ''];
    }
    if (strpos($url, 'zarinpal.com/pg/v4/payment/') !== false) {
        $j = json_decode((string)$b, true);
        if (substr($url, -12) === 'request.json') {
            if (($j['currency'] ?? '') !== 'IRR') return ['status' => 200, 'body' => '{"data":[],"errors":{"code":-9}}', 'error' => ''];
            $auth = 'A' . str_pad(substr(preg_replace('/\D/', '', md5($j['metadata']['order_id'])), 0, 20), 35, '0', STR_PAD_LEFT);
            file_put_contents($cfgFile . '.zp', json_encode([$auth => $j['amount']]) . "\n", FILE_APPEND | LOCK_EX);
            return ['status' => 200, 'body' => json_encode(['data' => ['code' => 100, 'authority' => $auth, 'fee' => 0], 'errors' => []]), 'error' => ''];
        }
        // verify: paid only if this authority was created with exactly this amount and is not marked unpaid.
        $known = [];
        foreach (is_file($cfgFile . '.zp') ? file($cfgFile . '.zp') : [] as $l) $known += (array)json_decode($l, true);
        if (is_file($cfgFile . '.zpdown')) return ['status' => 0, 'body' => '', 'error' => 'Operation timed out'];
        if (is_file($cfgFile . '.zpunpaid')) return ['status' => 200, 'body' => '{"data":[],"errors":{"code":-51}}', 'error' => ''];
        if (!isset($known[$j['authority']])) return ['status' => 200, 'body' => '{"data":[],"errors":{"code":-54}}', 'error' => ''];
        if ((int)$known[$j['authority']] !== (int)$j['amount']) return ['status' => 200, 'body' => '{"data":[],"errors":{"code":-50}}', 'error' => ''];
        return ['status' => 200, 'body' => json_encode(['data' => ['code' => 100, 'ref_id' => 777000 + strlen($j['authority']), 'card_pan' => '6037****1234'], 'errors' => []]), 'error' => ''];
    }
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
