<?php
/**
 * Divan's page before the gateway: the plan, the exact amount, notes and an optional offer, then «continue» to the
 * gateway's own page. It decides nothing about payment; it only remembers the order for the return page.
 */
declare(strict_types=1);

require __DIR__ . '/../app/bootstrap.php';
if (!App::installed()) { http_response_code(503); exit; }
Db::pdo();
Migrations::ensure();
PayPages::headers();
if (isset($_GET['demo'])) { echo PayPages::renderStart(null, true); exit; }
$order = RateLimit::hit('paystart:' . Http::ip(), 120, 3600) ? PayOrders::byCode((string)($_GET['o'] ?? '')) : null;
if ($order !== null && $order['status'] === 'paid') { header('Location: return.php?o=' . $order['code'], true, 303); exit; }
if ($order !== null) PayPages::remember((string)$order['code']);
else http_response_code(404);
echo PayPages::renderStart($order);
