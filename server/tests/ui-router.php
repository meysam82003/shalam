<?php
declare(strict_types=1);
if(getenv('DIVAN_UI_TEST')!=='1' || !str_contains((string)getenv('DIVAN_TEST_DSN'),'host=127.0.0.1;')){http_response_code(403);exit;}
define('DIVAN_TEST',true);$src=dirname(__DIR__).'/public';require $src.'/app/bootstrap.php';App::$root=(string)getenv('DIVAN_UI_ROOT');App::$config=require App::$root.'/storage/config.php';
$pdo=new PDO('mysql:'.getenv('DIVAN_TEST_DSN').';charset=utf8mb4',getenv('DIVAN_TEST_USER'),(string)getenv('DIVAN_TEST_PASS'),[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false]);Db::use($pdo,'dvtui_');$_SERVER['HTTPS']='on';
$path=parse_url($_SERVER['REQUEST_URI'],PHP_URL_PATH);
if($path==='/divan/tg/api.php'){TgApi::dispatch();exit;}
if($path==='/divan/tg/webhook.php'){TgBot::webhook();exit;}
if($path==='/divan/tg/app/'||$path==='/divan/tg/app/index.php'){require $src.'/tg/app/index.php';exit;}
if(preg_match('#^/divan/(tg/app/(?:app.js|app.css)|assets/fonts/[^/]+|assets/img/logo.png)$#D',$path,$m)){$file=$src.'/'.$m[1];if(!is_file($file)){http_response_code(404);exit;}$ext=pathinfo($file,PATHINFO_EXTENSION);header('Content-Type: '.(['js'=>'application/javascript','css'=>'text/css','png'=>'image/png','woff2'=>'font/woff2'][$ext]??'application/octet-stream'));readfile($file);exit;}
http_response_code(404);
