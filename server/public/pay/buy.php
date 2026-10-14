<?php
/**
 * Buying Pro on the site (for an account by username, or for an activation code typed in the app). The order then
 * follows the same path as the app's: Divan's page before payment, the gateway, and the result page.
 */
declare(strict_types=1);

require __DIR__ . '/../app/bootstrap.php';
if (!App::installed()) { http_response_code(503); exit; }
Db::pdo();
Migrations::ensure();
PayPages::headers();
if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST') {
    $old = ['plan' => (string)($_POST['plan'] ?? ''), 'method' => (string)($_POST['method'] ?? ''), 'username' => trim((string)($_POST['username'] ?? '')), 'agree' => !empty($_POST['agree']), 'promo' => trim((string)($_POST['promo'] ?? ''))];
    try {
        if (!PayPages::validFormToken((string)($_POST['t'] ?? ''))) throw new ApiError('form', 'مهلت این صفحه تمام شده بود؛ دوباره «ادامه» را بزنید.');
        if (!$old['agree']) throw new ApiError('agree', 'برای ادامه، شرایط خرید را بپذیرید.');
        $r = PayOrders::createSite($old['method'], $old['plan'], $old['username'], $old['promo']);
        header('Location: start.php?o=' . $r['order'], true, 303);
        exit;
    } catch (ApiError $e) {
        http_response_code(400);
        echo PayPages::renderBuy($e->getMessage(), $old);
        exit;
    }
}
// A link from the site's discount banner fills the code in.
echo PayPages::renderBuy(null, ['promo' => mb_substr(Promo::normalize((string)($_GET['promo'] ?? '')), 0, 40)]);
