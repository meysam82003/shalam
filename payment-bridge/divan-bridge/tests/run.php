<?php
/**
 * Automated checks of the payment gateway (no network, no store account): authentication, replay,
 * limits, the action allowlist, store answers and the log. Run:  php divan-bridge/tests/run.php
 */
declare(strict_types=1);

use DivanBridge\Bridge;

require __DIR__ . '/../src/Bridge.php';

$pass = 0; $fail = 0;
function check(bool $ok, string $what): void { global $pass, $fail; if ($ok) { $pass++; } else { $fail++; echo "FAIL: $what\n"; } }

$data = sys_get_temp_dir() . '/divan-bridge-test-' . bin2hex(random_bytes(4));
mkdir($data, 0700, true);
$KEY = str_repeat('a1', 32);
$OLD = str_repeat('b2', 32);
$TOKEN_MYKET = 'myket-secret-token-XYZ';
$TOKEN_BAZAAR = 'bazaar-secret-key-QWE';
$cfg = [
    'keys' => ['k1' => $KEY, 'k0' => $OLD, 'kx' => 'PASTE-64-HEX', 'ks' => 'short'],
    'allowed_ips' => ['190.2.143.208'],
    'packages' => ['com.meysam.divanemtiaz'],
    'skus' => ['divan_pro_1m', 'divan_pro_3m', 'divan_pro_6m', 'divan_pro_lifetime'],
    'myket_access_token' => $TOKEN_MYKET,
    'bazaar_api_secret' => $TOKEN_BAZAAR,
    'allow_set_price' => false,
    'rate_per_minute' => 1000,
];
Bridge::$now = 1800000000;
$calls = [];
// Fake stores: the purchase token decides the answer.
Bridge::$transport = function (string $m, string $url, array $h, ?string $b, int $t) use (&$calls) {
    $calls[] = [$m, $url, $h, $b];
    $tok = (string)(json_decode((string)$b, true)['tokenId'] ?? basename(rtrim(explode('?', $url)[0], '/')));
    if (strpos($url, '/products?') !== false) return ['status' => 200, 'body' => '{"skus":[{"sku":"divan_pro_1m","price":190000}]}', 'error' => ''];
    if (strpos($tok, 'probe') === 0) return ['status' => 404, 'body' => '{"error":"not found"}', 'error' => ''];
    if (strpos($tok, 'valid') === 0) return ['status' => 200, 'body' => '{"purchaseState":0,"consumptionState":1,"purchaseTime":1799999000000}', 'error' => ''];
    if (strpos($tok, 'refund') === 0) return ['status' => 200, 'body' => '{"purchaseState":1}', 'error' => ''];
    if (strpos($tok, 'unknown') === 0) return ['status' => 404, 'body' => '{}', 'error' => ''];
    if (strpos($tok, 'denied') === 0) return ['status' => 401, 'body' => '{"error":"bad token"}', 'error' => ''];
    if (strpos($tok, 'slow') === 0) return ['status' => 0, 'body' => '', 'error' => 'Operation timed out after 20001 milliseconds'];
    if (strpos($tok, 'busy') === 0) return ['status' => 429, 'body' => '', 'error' => ''];
    return ['status' => 500, 'body' => '', 'error' => ''];
};

$IP = '190.2.143.208';
function req(array $body, array $o = []): array
{
    global $cfg, $data, $KEY, $IP;
    $raw = $o['raw'] ?? json_encode($body);
    $ts = (string)($o['ts'] ?? Bridge::$now);
    $nonce = $o['nonce'] ?? bin2hex(random_bytes(12));
    $kid = $o['kid'] ?? 'k1';
    $key = $o['key'] ?? $KEY;
    $h = ['x-divan-key' => $kid, 'x-divan-time' => $ts, 'x-divan-nonce' => $nonce, 'x-divan-signature' => Bridge::sign($key, $ts, $nonce, $raw)];
    if (isset($o['headers'])) $h = $o['headers'];
    [$st, $hd, $out] = (new Bridge($o['cfg'] ?? $cfg, $data))->handle($o['method'] ?? 'POST', $h, $raw, $o['ip'] ?? $IP);
    return ['status' => $st, 'headers' => $hd, 'body' => $out, 'json' => json_decode($out, true), 'nonce' => $nonce, 'key' => $key];
}
function signedOk(array $r): bool { return isset($r['headers']['X-Divan-Signature']) && hash_equals(Bridge::signResponse($r['key'], $r['nonce'], $r['body']), $r['headers']['X-Divan-Signature']); }
$verify = fn(string $token, string $store = 'myket', string $sku = 'divan_pro_1m') => ['action' => $store . '.verify', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => $sku, 'token' => $token]];

// ---------------------------------------------------------------- authentication
$r = req(['action' => 'ping']);
check($r['status'] === 200 && $r['json']['kind'] === 'pong' && $r['json']['stores'] === ['myket' => true, 'bazaar' => true], 'signed ping works');
check(signedOk($r), 'response is signed and bound to the nonce');
check(strpos($r['body'], $TOKEN_MYKET) === false && strpos($r['body'], $TOKEN_BAZAAR) === false && strpos($r['body'], $KEY) === false, 'ping reveals no secret');
$r = req(['action' => 'ping'], ['headers' => []]);
check($r['status'] === 401 && !isset($r['headers']['X-Divan-Signature']) && $r['json'] === ['ok' => false, 'error' => 'unauthorized'], 'request without signature refused, nothing revealed');
$r = req(['action' => 'ping'], ['key' => str_repeat('c3', 32)]);
check($r['status'] === 401, 'wrong HMAC key refused');
$raw = json_encode(['action' => 'ping']);
$ts = (string)Bridge::$now; $n = bin2hex(random_bytes(12));
[$st] = (new Bridge($cfg, $data))->handle('POST', ['x-divan-key' => 'k1', 'x-divan-time' => $ts, 'x-divan-nonce' => $n, 'x-divan-signature' => Bridge::sign($KEY, $ts, $n, $raw)], json_encode(['action' => 'myket.products', 'params' => ['package' => 'com.meysam.divanemtiaz']]), $IP);
check($st === 401, 'body changed after signing is refused');
$r = req(['action' => 'ping'], ['nonce' => 'fixed-nonce-0000000001']);
$r2 = req(['action' => 'ping'], ['nonce' => 'fixed-nonce-0000000001']);
check($r['status'] === 200 && $r2['status'] === 401 && $r2['json']['error'] === 'replay', 'same nonce twice: second refused (replay)');
check(req(['action' => 'ping'], ['ts' => Bridge::$now - 301])['json']['error'] === 'expired', 'timestamp older than 5 minutes refused');
check(req(['action' => 'ping'], ['ts' => Bridge::$now + 301])['json']['error'] === 'expired', 'timestamp from the future refused');
check(req(['action' => 'ping'], ['kid' => 'k0', 'key' => $OLD])['status'] === 200, 'second key works during key rotation');
check(req(['action' => 'ping'], ['kid' => 'kx', 'key' => 'PASTE-64-HEX'])['status'] === 401 && req(['action' => 'ping'], ['kid' => 'ks', 'key' => 'short'])['status'] === 401, 'placeholder and short keys never accepted');
check(req(['action' => 'ping'], ['method' => 'GET'])['status'] === 405, 'GET refused (POST only)');
check(req(['action' => 'ping'], ['ip' => '5.6.7.8'])['status'] === 403, 'caller outside the allowed IP refused');
check(req(['action' => 'ping'], ['raw' => str_repeat('x', Bridge::MAX_BODY + 1)])['status'] === 413, 'oversized body refused');

// ---------------------------------------------------------------- actions allowlist, no proxy
$n0 = count($calls);
$r = req(['action' => 'fetch', 'params' => ['url' => 'https://example.com/']]);
check($r['status'] === 400 && $r['json']['kind'] === 'bad_request' && count($calls) === $n0, 'unknown action refused, nothing fetched (no open proxy)');
$r = req(['action' => 'myket.verify', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => '../../admin', 'token' => 'valid12345']]);
check($r['status'] === 400 && count($calls) === $n0, 'path in product id refused');
$r = req(['action' => 'myket.verify', 'params' => ['package' => 'com.other.app', 'sku' => 'divan_pro_1m', 'token' => 'valid12345']]);
check($r['status'] === 400 && count($calls) === $n0, 'other package refused');
$r = req(['action' => 'myket.verify', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => 'other_product', 'token' => 'valid12345']]);
check($r['status'] === 400 && count($calls) === $n0, 'product id outside the list refused');
$r = req(['action' => 'myket.verify', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => 'divan_pro_1m', 'token' => "valid\r\nX-Injected: 1"]]);
check($r['status'] === 400 && count($calls) === $n0, 'header injection in token refused');
$r = req(['action' => 'myket.verify', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => 'divan_pro_1m', 'token' => 'valid?url=http://evil']]);
check($r['status'] === 400, 'URL characters in token refused');
check(req(['action' => 'myket.set_price', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => 'divan_pro_1m', 'price_rial' => 190000]])['json']['kind'] === 'not_allowed', 'price change off unless enabled in config');

// ---------------------------------------------------------------- store answers
$r = req($verify('valid-token-0001'));
$c = end($calls);
check($r['json']['kind'] === 'store' && $r['json']['status'] === 200 && $r['json']['body']['purchaseState'] === 0, 'Myket valid purchase passes through');
check($c[0] === 'GET' && $c[1] === 'https://developer.myket.ir/api/applications/com.meysam.divanemtiaz/purchases/products/divan_pro_1m/tokens/valid-token-0001' && in_array('X-Access-Token: ' . $TOKEN_MYKET, $c[2], true) && $c[3] === null, 'Myket documented verify endpoint (GET …/tokens/{token}), key in header');
$legacy = $cfg; $legacy['myket_verify'] = 'partners';
req($verify('valid-token-0011'), ['cfg' => $legacy]);
$c = end($calls);
check($c[0] === 'POST' && $c[1] === 'https://developer.myket.ir/api/partners/applications/com.meysam.divanemtiaz/purchases/products/divan_pro_1m/verify' && json_decode($c[3], true) === ['tokenId' => 'valid-token-0011'], 'older «partners» verify form still selectable');
check(req($verify('refund-token-01'))['json']['body']['purchaseState'] === 1, 'Myket unsuccessful purchase (purchaseState=1) passed on');
check(req($verify('unknown-token-01'))['json']['status'] === 404, 'unknown receipt → 404 from the store');
$r = req($verify('denied-token-01'));
check($r['json']['kind'] === 'store_auth' && $r['json']['status'] === 401, 'store 401 reported as store_auth (not as invalid purchase)');
$r = req($verify('slow-token-0001'));
check($r['json']['kind'] === 'store_unreachable' && $r['json']['error'] === 'timeout', 'store timeout reported as store_unreachable/timeout');
check(req($verify('busy-token-0001'))['json']['status'] === 429, 'store rate limit passed on as 429');
$r = req($verify('valid-token-0002', 'bazaar'));
$c = end($calls);
check($r['json']['kind'] === 'store' && $c[0] === 'GET' && $c[1] === 'https://pardakht.cafebazaar.ir/devapi/v2/api/validate/com.meysam.divanemtiaz/inapp/divan_pro_1m/purchases/valid-token-0002/' && in_array('CAFEBAZAAR-PISHKHAN-API-SECRET: ' . $TOKEN_BAZAAR, $c[2], true), 'Bazaar official in-app endpoint with the API key header');
$r = req(['action' => 'bazaar.verify', 'params' => ['package' => 'com.meysam.divanemtiaz', 'sku' => 'divan_pro_1m', 'token' => 'valid-sub-001', 'type' => 'subscription']]);
check(end($calls)[1] === 'https://pardakht.cafebazaar.ir/devapi/v2/api/applications/com.meysam.divanemtiaz/subscriptions/divan_pro_1m/purchases/valid-sub-001', 'Bazaar subscription endpoint');
check(req(['action' => 'bazaar.test', 'params' => ['package' => 'com.meysam.divanemtiaz']])['json']['status'] === 404, 'Bazaar connectivity test: 404 = key accepted');
check(req(['action' => 'myket.test', 'params' => ['package' => 'com.meysam.divanemtiaz']])['json']['body'] === ['count' => 1], 'Myket connectivity test returns only a count');
$noTok = $cfg; $noTok['myket_access_token'] = '';
check(req($verify('valid-token-0003'), ['cfg' => $noTok])['json']['kind'] === 'not_configured', 'missing store token reported as not_configured');

// ---------------------------------------------------------------- ZarinPal (site version)
$M = '11111111-2222-3333-4444-555555555555';
$zr = fn(array $over = []) => ['action' => 'zarinpal.request', 'params' => $over + ['merchant_id' => $M, 'amount' => 190000, 'callback_url' => 'https://httpuser87890.ir/divan/pay/return.php?o=abcdefghij0123456789', 'description' => 'x', 'metadata' => ['order_id' => 'abcdefghij0123456789']]];
$n0 = count($calls);
$r = req($zr());
$c = end($calls);
check($r['json']['kind'] === 'store' && $c[1] === 'https://payment.zarinpal.com/pg/v4/payment/request.json' && json_decode($c[3], true)['currency'] === 'IRR' && json_decode($c[3], true)['amount'] === 190000, 'ZarinPal request: official endpoint, amount in rials with currency IRR');
$r = req($zr(['sandbox' => true]));
check(end($calls)[1] === 'https://sandbox.zarinpal.com/pg/v4/payment/request.json', 'sandbox address when asked');
$n1 = count($calls);
check(req($zr(['callback_url' => 'https://evil.example/steal']))['status'] === 400, 'buyer can only be returned to the Divan site');
check(req($zr(['callback_url' => 'http://httpuser87890.ir/x']))['status'] === 400, 'plain http return refused');
check(req($zr(['amount' => '190000']))['status'] === 400 && req($zr(['amount' => 5]))['status'] === 400, 'amount must be a whole number of rials in range');
check(req($zr(['merchant_id' => '../../x']))['status'] === 400, 'bad merchant id refused');
$only = $cfg; $only['zarinpal_merchants'] = ['99999999-2222-3333-4444-555555555555'];
check(req($zr(), ['cfg' => $only])['status'] === 400, 'merchant id outside the allowlist refused');
check(count($calls) === $n1, 'nothing sent for refused requests');
$r = req(['action' => 'zarinpal.verify', 'params' => ['merchant_id' => $M, 'amount' => 190000, 'authority' => 'A0000000000000000000000000000000012345']]);
check(end($calls)[1] === 'https://payment.zarinpal.com/pg/v4/payment/verify.json' && json_decode(end($calls)[3], true)['authority'] === 'A0000000000000000000000000000000012345', 'ZarinPal verify with the given authority and amount');
check(req(['action' => 'zarinpal.verify', 'params' => ['merchant_id' => $M, 'amount' => 190000, 'authority' => 'A00/../../x']])['status'] === 400, 'bad authority refused');

// ---------------------------------------------------------------- rate limit
$lim = $cfg; $lim['rate_per_minute'] = 10; $lim['allowed_ips'] = [];
$codes = [];
for ($i = 0; $i < 12; $i++) $codes[] = req(['action' => 'ping'], ['cfg' => $lim, 'ip' => '10.9.8.7'])['status'];
check(count(array_filter($codes, fn($c) => $c === 200)) === 10 && $codes[10] === 429 && $codes[11] === 429, 'rate limit: 10 per minute, then 429');
Bridge::$now += 120;

// ---------------------------------------------------------------- concurrency of the replay guard
$n = 'race-nonce-000000000001';
$ok = 0;
for ($i = 0; $i < 5; $i++) if (req(['action' => 'ping'], ['nonce' => $n])['status'] === 200) $ok++;
check($ok === 1, 'a nonce passes exactly once');

// ---------------------------------------------------------------- the log holds no secret
$log = '';
foreach (glob($data . '/logs/*.log') as $f) $log .= file_get_contents($f);
check($log !== '' && strpos($log, $TOKEN_MYKET) === false && strpos($log, $TOKEN_BAZAAR) === false && strpos($log, $KEY) === false && strpos($log, 'valid-token-0001') === false, 'log holds no key, store token or purchase token');
check(strpos($log, '"result":"auth_replay"') !== false && strpos($log, '"result":"auth_bad_signature"') !== false && strpos($log, '"result":"ip_denied"') !== false, 'security events are logged');

// ---------------------------------------------------------------- real HTTPS client stays strict
$src = file_get_contents(__DIR__ . '/../src/Bridge.php');
check(strpos($src, 'CURLOPT_SSL_VERIFYPEER => true') !== false && strpos($src, 'CURLOPT_SSL_VERIFYHOST => 2') !== false && strpos($src, 'CURLOPT_FOLLOWLOCATION => false') !== false && strpos($src, 'CURLOPT_PROTOCOLS => CURLPROTO_HTTPS') !== false, 'certificate checks on, no redirects, HTTPS only');
Bridge::$transport = null;
$quick = $cfg; $quick['timeout'] = 5;
$r = req($verify('valid-token-0009'), ['cfg' => $quick]);
check(in_array($r['json']['kind'], ['store_unreachable', 'store_auth', 'store'], true), 'real client answers with a kind (network here may be closed)');

exec('rm -rf ' . escapeshellarg($data));
echo ($fail === 0 ? "ALL $pass BRIDGE CHECKS PASSED\n" : "$fail FAILED, $pass passed\n");
exit($fail === 0 ? 0 : 1);
