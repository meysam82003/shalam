<?php
/**
 * Where the payment page sends the buyer back. This page never decides by itself: it asks the gateway
 * again (with the amount stored on the order) and shows the order's real state.
 */
declare(strict_types=1);

require __DIR__ . '/../app/bootstrap.php';
if (!App::installed()) { http_response_code(503); exit; }
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src 'self'");
Db::pdo();
Migrations::ensure();

$order = PayOrders::byCode((string)($_GET['o'] ?? ''));
$given = (string)($_GET['Authority'] ?? $_GET['authority'] ?? '');
if ($order !== null && ($given === '' || hash_equals((string)$order['gateway_ref'], $given)) && RateLimit::hit('payreturn:' . Http::ip(), 60, 3600)) {
    try { $order = PayOrders::settle($order); } catch (Throwable $e) { /* shown as pending; the app and the cron ask again */ }
}
$state = $order['status'] ?? 'unknown';
[$title, $text, $color] = [
    'paid' => ['پرداخت موفق بود', 'اشتراک Pro به حساب شما اضافه شد. به برنامه برگردید.', '#12A891'],
    'failed' => ['پرداخت انجام نشد', 'اگر مبلغی کم شده، بانک طبق قانون ظرف ۷۲ ساعت آن را برمی‌گرداند.', '#C23B48'],
    'expired' => ['مهلت پرداخت تمام شد', 'از برنامه دوباره خرید را شروع کنید.', '#C23B48'],
][$state] ?? ['در حال بررسی پرداخت', 'نتیجه چند لحظهٔ دیگر در برنامه نمایش داده می‌شود؛ به برنامه برگردید.', '#C9A24B'];
$deep = $order !== null ? 'divan://pay/' . $order['code'] : 'divan://pro';
?><!doctype html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>دیوان امتیاز — <?= e($title) ?></title>
<style>
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:linear-gradient(#0A2019,#061511);font-family:Tahoma,sans-serif;color:#F2F0E6}
.c{max-width:420px;margin:16px;padding:28px 22px;border-radius:24px;background:#143A31;border:1px solid #E3C27A;text-align:center}
h1{font-size:1.3rem;color:<?= $color ?>}p{line-height:1.9;color:#C9DCD4}
a.b{display:inline-block;margin-top:14px;padding:12px 26px;border-radius:999px;background:linear-gradient(90deg,#EBCC8A,#5BC3A0);color:#0A1F1A;font-weight:bold;text-decoration:none}
small{display:block;margin-top:16px;color:#6F8E83}
</style></head><body><div class="c">
<h1><?= e($title) ?></h1><p><?= e($text) ?></p>
<?php if ($order !== null && $order['ref_id']): ?><p>کد پیگیری: <b dir="ltr"><?= e($order['ref_id']) ?></b></p><?php endif; ?>
<a class="b" href="<?= e($deep) ?>">بازگشت به دیوان امتیاز</a>
<?php if ($order !== null): ?><small>شمارهٔ سفارش: <span dir="ltr"><?= e($order['code']) ?></span></small><?php endif; ?>
</div></body></html>
