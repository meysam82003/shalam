<?php
/** Divan Emtiaz — admin panel entry point. */

declare(strict_types=1);

require dirname(__DIR__) . '/app/bootstrap.php';
if (!App::installed()) { header('Location: ../install/'); exit; }
require_once App::$root . '/app/admin/core.php';
require_once App::$root . '/app/admin/pages_sales.php';
require_once App::$root . '/app/admin/pages_config.php';

Db::pdo();
Migrations::run();
Admin::start();
if (Admin::$me === null) Admin::login();

// Housekeeping without a cron job: at most once an hour, on an admin visit.
if ((int)Settings::get('last_daily_at', 0) < Clock::now() - 3600) {
    Settings::set('last_daily_at', Clock::now());
    try { Backup::daily(); } catch (Throwable $e) { error_log('[divan] daily ' . $e->getMessage()); }
}

$page = (string)($_GET['p'] ?? 'dashboard');
if ($page === 'logout') {
    Admin::checkPost(true);
    Audit::log('admin', (int)Admin::$me['id'], 'admin_logout', 'admin', Admin::$me['id']);
    session_unset();
    session_destroy();
    header('Location: index.php');
    exit;
}

$pages = [
    'telegram' => [TgPanel::class, 'page'],
    'dashboard' => [SalesPages::class, 'dashboard'],
    'users' => [SalesPages::class, 'users'],
    'user' => [SalesPages::class, 'user'],
    'subscriptions' => [SalesPages::class, 'subscriptions'],
    'transactions' => [SalesPages::class, 'transactions'],
    'transaction' => [SalesPages::class, 'transaction'],
    'support' => [SalesPages::class, 'support'],
    'ticket' => [SalesPages::class, 'ticket'],
    'gifts' => [SalesPages::class, 'gifts'],
    'promos' => [SalesPages::class, 'promos'],
    'reports' => [SalesPages::class, 'reports'],
    'plans' => [ConfigPages::class, 'plans'],
    'campaigns' => [ConfigPages::class, 'campaigns'],
    'features' => [ConfigPages::class, 'features'],
    'ads' => [ConfigPages::class, 'ads'],
    'ad' => [ConfigPages::class, 'ad'],
    'placements' => [ConfigPages::class, 'placements'],
    'media' => [ConfigPages::class, 'media'],
    'adnetwork' => [ConfigPages::class, 'adnetwork'],
    'notices' => [ConfigPages::class, 'notices'],
    'site' => [ConfigPages::class, 'site'],
    'stores' => [ConfigPages::class, 'stores'],
    'bridge' => [ConfigPages::class, 'bridge'],
    'paymethods' => [ConfigPages::class, 'paymethods'],
    'backups' => [ConfigPages::class, 'backups'],
    'audit' => [ConfigPages::class, 'audit'],
    'admins' => [ConfigPages::class, 'admins'],
    'account' => [ConfigPages::class, 'account'],
];
if (!isset($pages[$page])) $page = 'dashboard';
Admin::require($page);
Admin::checkPost($page === 'account');
try {
    call_user_func($pages[$page]);
} catch (ApiError $e) {
    Admin::flash($e->getMessage(), 'bad');
    $back = $_SERVER['QUERY_STRING'] ?? ('p=' . $page);
    header('Location: index.php?' . $back);
}
