<?php
declare(strict_types=1);

if (!defined('DIVAN')) define('DIVAN', true);
if (PHP_VERSION_ID < 70400) { http_response_code(500); exit('Divan Emtiaz needs PHP 7.4 or newer.'); }

$__root = dirname(__DIR__);
require_once __DIR__ . '/lib.php';
require_once __DIR__ . '/migrations.php';
require_once __DIR__ . '/catalog.php';
require_once __DIR__ . '/domain.php';
require_once __DIR__ . '/markets.php';
require_once __DIR__ . '/payments.php';
require_once __DIR__ . '/paypages.php';
require_once __DIR__ . '/site.php';
require_once __DIR__ . '/backup.php';
require_once __DIR__ . '/installer.php';
require_once __DIR__ . '/api.php';
require_once __DIR__ . '/telegram/load.php';

App::boot($__root);
ini_set('display_errors', '0');
ini_set('log_errors', '1');
if (is_dir($__root . '/storage/logs') && is_writable($__root . '/storage/logs')) ini_set('error_log', $__root . '/storage/logs/php-error.log');
