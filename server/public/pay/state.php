<?php
/** Live state of one order for the return page (asks the gateway again, within the same limits as the app). */
declare(strict_types=1);

require __DIR__ . '/../app/bootstrap.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
if (!App::installed()) { http_response_code(503); echo '{}'; exit; }
Db::pdo();
Migrations::ensure();
$order = RateLimit::hit('paystate:' . Http::ip(), 600, 3600) ? PayOrders::byCode((string)($_GET['o'] ?? '')) : null;
if ($order === null) { http_response_code(404); echo '{"status":null}'; exit; }
if (in_array($order['status'], ['pending', 'failed'], true) && RateLimit::hit('paystatus:' . $order['id'], 30, 3600)) {
    try { $order = PayOrders::settle($order); } catch (Throwable $e) { /* asked again on the next poll */ }
}
echo json_encode(['status' => $order['status'], 'ref_id' => $order['ref_id']]);
