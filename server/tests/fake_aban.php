<?php
/**
 * Fake Aban Gateway for the automated tests (php -S router), following github.com/AbanGateway/docs:
 * POST …/invoices, GET …/invoices/{id}, POST …/invoices/{id}/verify|cancel|simulate-payment, under any address prefix.
 * Tokens: ag_test_* sandbox, ag_live_* live; only tokens listed in the state file are valid.
 * Control files next to the state file: .down (503), .wrongamount, .wrongorder, .wrongtest, .payhost (payment page domain).
 */
$state = getenv('FAKE_ABAN_STATE');
$db = is_file($state) ? json_decode(file_get_contents($state), true) : ['tokens' => [], 'invoices' => [], 'requests' => []];
$save = function () use (&$db, $state) { file_put_contents($state, json_encode($db)); };
$out = function (int $code, array $body) { http_response_code($code); header('Content-Type: application/json'); echo json_encode($body); return true; };
$err = fn(int $code, string $c) => $out($code, ['error' => ['code' => $c, 'message' => $c]]);
if (is_file("$state.down")) return $err(503, 'capacity_full');
$auth = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
$token = strpos($auth, 'Bearer ') === 0 ? substr($auth, 7) : '';
if (!in_array($token, $db['tokens'], true)) return $out(401, ['detail' => 'Invalid token']);
$test = strpos($token, 'ag_test_') === 0;
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$db['requests'][] = [$_SERVER['REQUEST_METHOD'], $path];
$save();
if (!preg_match('#^/api/v1/invoices(?:/([^/]+)(?:/(verify|cancel|simulate-payment))?)?$#', $path, $m)) return $err(404, 'not_found');
$id = $m[1] ?? null; $act = $m[2] ?? null;
$view = function (array $inv) use ($state) {
    $host = is_file("$state.payhost") ? trim(file_get_contents("$state.payhost")) : 'abangateway.ir';
    return ['invoice_id' => $inv['id'], 'status' => $inv['status'], 'amount_rial' => $inv['amount'] + (is_file("$state.wrongamount") ? 1000 : 0),
        'payable_rial' => $inv['amount'] + $inv['rand'], 'payable_toman' => intdiv($inv['amount'] + $inv['rand'], 10), 'fee_rial' => 40000,
        'order_id' => is_file("$state.wrongorder") ? 'someoneelse000000000' : $inv['order'], 'card_number' => '6037991234567890', 'card_holder' => 'فروشنده آزمایشی', 'bank' => 'ملی',
        'iban' => null, 'account_number' => null, 'payment_url' => "https://$host/pay/" . $inv['id'], 'expires_at' => '2026-10-14T12:00:00+00:00',
        'paid_at' => $inv['status'] === 'paid' ? '2026-10-14T11:00:00+00:00' : null, 'is_test' => is_file("$state.wrongtest") ? !$inv['test'] : $inv['test']];
};
$method = $_SERVER['REQUEST_METHOD'];
if ($method === 'POST' && $id === null) {
    $in = json_decode(file_get_contents('php://input'), true);
    if (!is_array($in) || !is_int($in['amount_rial'] ?? null) || $in['amount_rial'] <= 0) return $out(422, ['error' => ['code' => 'invalid_request', 'message' => 'amount_rial', 'details' => ['fields' => ['amount_rial' => 'missing']]]]);
    foreach (['callback_url', 'return_url'] as $k) if (isset($in[$k]) && stripos($in[$k], 'https://') !== 0) return $err(422, 'unsafe_callback_url');
    $iid = 'inv_' . bin2hex(random_bytes(10));
    $db['invoices'][$iid] = ['id' => $iid, 'amount' => $in['amount_rial'], 'rand' => random_int(1, 9999), 'order' => (string)($in['order_id'] ?? ''), 'status' => 'pending', 'test' => $test, 'verified' => false,
        'callback' => $in['callback_url'] ?? null, 'return' => $in['return_url'] ?? null];
    $save();
    return $out(201, $view($db['invoices'][$iid]));
}
$inv = $id !== null ? ($db['invoices'][$id] ?? null) : null;
if ($inv === null || $inv['test'] !== $test) return $err(404, 'invoice_not_found');
if ($method === 'GET' && $act === null) return $out(200, $view($inv));
if ($method === 'POST' && $act === 'verify') {
    if ($inv['status'] === 'expired') return $err(410, 'invoice_expired');
    if ($inv['status'] !== 'paid') return $err(402, 'not_yet_paid');
    if ($inv['verified']) return $err(409, 'already_verified');
    $db['invoices'][$id]['verified'] = true;
    $save();
    return $out(200, ['verified' => true, 'invoice_id' => $id, 'order_id' => $inv['order'], 'amount_rial' => $inv['amount'] + (is_file("$state.wrongamount") ? 1000 : 0), 'paid_at' => '2026-10-14T11:00:00+00:00']);
}
if ($method === 'POST' && $act === 'cancel') {
    if ($inv['status'] !== 'pending') return $err(409, 'invoice_not_payable');
    $db['invoices'][$id]['status'] = 'cancelled'; $save();
    return $out(200, $view($db['invoices'][$id]));
}
if ($method === 'POST' && $act === 'simulate-payment') {
    if (!$test) return $err(403, 'sandbox_only');
    if ($inv['status'] !== 'pending') return $err(409, 'invoice_not_payable');
    $db['invoices'][$id]['status'] = 'paid'; $save();
    return $out(200, $view($db['invoices'][$id]));
}
if ($method === 'POST' && $act === null) return $err(404, 'not_found');
return $err(404, 'not_found');
