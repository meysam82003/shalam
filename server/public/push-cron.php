<?php
/** cPanel: run EVERY MINUTE, separate from daily backups. */
declare(strict_types=1);
require __DIR__.'/app/bootstrap.php';
if(!App::installed()){http_response_code(503);exit('not installed');}
if(PHP_SAPI!=='cli'&&(!App::cfg('cron_key','')||!hash_equals((string)App::cfg('cron_key',''),(string)($_GET['key']??'')))){http_response_code(403);exit('forbidden');}
Db::pdo();Migrations::ensure();
$queued=PushDelivery::enqueue();$result=PushDelivery::flush();
header('Content-Type: application/json');echo json_encode(['queued'=>$queued]+$result);
