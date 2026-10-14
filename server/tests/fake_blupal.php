<?php
/**
 * Fake Blupal for the automated tests (php -S router), following blupal.top/documentation:
 * POST /api/v1/invoices/create, GET /api/v1/invoices/{id}, POST /api/v1/sandbox/invoices/{id}/simulate.
 * Keys: blu_test_* are sandbox, blu_live_* live; only keys listed in the state file are valid.
 * Control files next to the state file: .down (500), .wrongmode (reports the other mode), .wrongamount (amount + 1000).
 */
$state = getenv('FAKE_BLUPAL_STATE');
$db = is_file($state) ? json_decode(file_get_contents($state), true) : ['keys' => [], 'invoices' => [], 'next' => 1000];
$save = function () use (&$db, $state) { file_put_contents($state, json_encode($db)); };
$out = function (int $code, array $body) { http_response_code($code); header('Content-Type: application/json'); echo json_encode($body); return true; };
if (is_file("$state.down")) return $out(500, ['success' => false, 'error' => 'server']);
$key = $_SERVER['HTTP_X_API_KEY'] ?? ($_GET['api_key'] ?? '');
if (!in_array($key, $db['keys'], true)) return $out(401, ['success' => false, 'error' => 'unauthorized']);
$mode = strpos($key, 'blu_test_') === 0 ? 'sandbox' : 'live';
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$view = function (array $inv) use ($state) {
    $m = $inv['mode'];
    if (is_file("$state.wrongmode")) $m = $m === 'live' ? 'sandbox' : 'live';
    $amount = $inv['amount'] + (is_file("$state.wrongamount") ? 1000 : 0);
    $paid = $inv['status'] === 'PAID';
    return ['success' => true, 'invoice_id' => $inv['id'], 'status' => $inv['status'], 'transaction_id' => $paid ? $inv['id'] * 7 : null,
        'amount' => $amount, 'final_amount' => $amount + $inv['rand'], 'mode' => $m, 'expires_at' => $m === 'sandbox' ? '2026-10-14T12:30:00+03:30' : null,
        'payer_name' => $paid ? 'خریدار آزمایشی' : null, 'payer_card' => $paid ? '6037991234567890' : null, 'payer_bank_name' => $paid ? 'بانک ملی' : null];
};
if ($_SERVER['REQUEST_METHOD'] === 'POST' && $path === '/api/v1/invoices/create') {
    $in = json_decode(file_get_contents('php://input'), true) ?: [];
    if (!isset($in['amount'])) return $out(400, ['success' => false, 'error' => 'amount_required']);
    $a = (int)$in['amount'];
    if ($a < 100000) return $out(400, ['success' => false, 'error' => 'amount_too_low', 'message' => 'مبلغ باید حداقل 100,000 ریال باشد']);
    if ($a > 500000000) return $out(400, ['success' => false, 'error' => 'amount_too_high']);
    $id = $db['next']++;
    $tok = bin2hex(random_bytes(10));
    $db['invoices'][$id] = ['id' => $id, 'amount' => $a, 'rand' => random_int(0, 999), 'status' => 'PENDING', 'mode' => $mode, 'token' => $tok];
    $save();
    $inv = $db['invoices'][$id];
    return $out(200, ['success' => true, 'invoice_id' => $id, 'amount' => $a, 'final_amount' => $a + $inv['rand'], 'status' => 'PENDING',
        'payment_link' => 'https://blupal.top/' . ($mode === 'sandbox' ? 'sandbox/' : '') . 'payment/' . $tok, 'card_number' => '6219861012345678',
        'mode' => $mode, 'expires_at' => $mode === 'sandbox' ? '2026-10-14T12:30:00+03:30' : null]);
}
if ($_SERVER['REQUEST_METHOD'] === 'GET' && preg_match('#^/api/v1/invoices/([^/]+)$#', $path, $m)) {
    if (!ctype_digit($m[1])) return $out(400, ['success' => false, 'error' => 'invalid_invoice_id']);
    $inv = $db['invoices'][$m[1]] ?? null;
    if ($inv === null) return $out(404, ['success' => false, 'error' => 'not_found']);
    if ($inv['mode'] !== $mode) return $out(403, ['success' => false, 'error' => 'mode_mismatch']);
    return $out(200, $view($inv));
}
if ($_SERVER['REQUEST_METHOD'] === 'POST' && preg_match('#^/api/v1/sandbox/invoices/(\d+)/simulate$#', $path, $m)) {
    if ($mode !== 'sandbox') return $out(403, ['success' => false, 'error' => 'mode_mismatch']);
    $inv = $db['invoices'][$m[1]] ?? null;
    if ($inv === null) return $out(404, ['success' => false, 'error' => 'not_found']);
    if ($inv['status'] !== 'PENDING') return $out(400, ['success' => false, 'error' => 'invalid_state']);
    $in = json_decode(file_get_contents('php://input'), true) ?: [];
    $sc = $in['scenario'] ?? 'success';
    $map = ['success' => 'PAID', 'wrong_amount' => 'PENDING', 'expire' => 'EXPIRED', 'cancel' => 'CANCELED'];
    if (!isset($map[$sc])) return $out(400, ['success' => false, 'error' => 'invalid_scenario']);
    $db['invoices'][$m[1]]['status'] = $map[$sc];
    $save();
    return $out(200, ['success' => true, 'status' => $map[$sc], 'invoice_id' => (int)$m[1], 'transaction_id' => $map[$sc] === 'PAID' ? (int)$m[1] * 7 : null]);
}
return $out(404, ['success' => false, 'error' => 'not_found']);
