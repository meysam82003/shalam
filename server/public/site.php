<?php
/** Settings of the introduction site from the panel («مدیریت سایت»), read once per visit by site.js. */
declare(strict_types=1);

require __DIR__ . '/app/bootstrap.php';
header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');
if (!App::installed()) { http_response_code(503); echo '{}'; exit; }
Db::pdo();
Migrations::ensure();
// A minute of browser cache; ad impressions are counted only within a fair rate per visitor.
header('Cache-Control: public, max-age=60');
echo json_encode(SiteSettings::publicView(RateLimit::hit('site-imp:' . Http::ip(), 60, 3600)), JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
