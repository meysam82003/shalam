<?php
/**
 * Payment gateway webhooks (Blupal, Aban Gateway). A webhook never grants anything by itself: it only names the
 * invoice, and the order is settled by asking the gateway again with the amount stored on the order (PayOrders::settle).
 * Repeated deliveries are harmless (one atomic status change grants the plan once).
 */
declare(strict_types=1);

require __DIR__ . '/../app/bootstrap.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
if (!App::installed()) { http_response_code(503); echo '{"received":false}'; exit; }
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') { http_response_code(405); echo '{"received":false}'; exit; }
Db::pdo();
Migrations::ensure();

$method = (string)($_GET['m'] ?? '');
$raw = (string)file_get_contents('php://input', false, null, 0, 65536);
$result = PayWebhooks::handle($method, $raw, ['signature' => Http::header('X-Signature')], Http::ip());
http_response_code($result[0]);
echo json_encode($result[1]);
