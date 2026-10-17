<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
foreach (['auth','management','events','read','bot','api','seo','panel'] as $module) require_once __DIR__.'/'.$module.'.php';
