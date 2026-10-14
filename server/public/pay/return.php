<?php
/**
 * Where the gateway sends the buyer back. This page never decides by itself: it asks the gateway again (with the
 * amount stored on the order) and shows the order's real state, updating itself while the payment is being confirmed.
 * Gateways with a fixed return address (Blupal «Callback page»: return.php?m=blupal) are matched by their invoice id
 * or by the order the page before payment remembered.
 */
declare(strict_types=1);

require __DIR__ . '/../app/bootstrap.php';
if (!App::installed()) { http_response_code(503); exit; }
Db::pdo();
Migrations::ensure();
PayPages::headers();
if (isset($_GET['demo'])) { echo PayPages::renderReturn(null, true, (string)$_GET['demo']); exit; }

$order = PayPages::findOrder($_GET, $_COOKIE);
$given = (string)($_GET['Authority'] ?? $_GET['authority'] ?? '');
if ($order !== null && ($given === '' || hash_equals((string)$order['gateway_ref'], $given)) && RateLimit::hit('payreturn:' . Http::ip(), 60, 3600)) {
    try { $order = PayOrders::settle($order); } catch (Throwable $e) { /* shown as pending; the app and the cron ask again */ }
}
$buyer = $order !== null && hash_equals((string)$order['code'], (string)($_COOKIE[PayPages::COOKIE] ?? ''));
echo PayPages::renderReturn($order, false, 'paid', $buyer);
