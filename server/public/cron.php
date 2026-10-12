<?php
/**
 * Daily housekeeping: store rechecks (refunds, pending purchases), rate-limit cleanup and the encrypted backup.
 * cPanel cron:  php /home/USER/public_html/divan/cron.php     (or)   curl -s "https://SITE/divan/cron.php?key=CRON_KEY"
 */

declare(strict_types=1);

require __DIR__ . '/app/bootstrap.php';
if (!App::installed()) { http_response_code(503); exit("not installed\n"); }
if (PHP_SAPI !== 'cli' && !hash_equals((string)App::cfg('cron_key', ''), (string)($_GET['key'] ?? ''))) { http_response_code(403); exit("forbidden\n"); }
Db::pdo();
Migrations::run();
@set_time_limit(300);
Settings::set('last_daily_at', Clock::now());
$out = Backup::daily();
header('Content-Type: text/plain; charset=utf-8');
echo 'ok rechecked=' . count($out['rechecked']) . (isset($out['backup']) ? ' backup=' . $out['backup']['file'] : '') . (isset($out['backup_error']) ? ' backup_error=' . $out['backup_error'] : '') . "\n";
