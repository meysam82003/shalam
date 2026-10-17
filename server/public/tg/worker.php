<?php
declare(strict_types=1);
// cPanel Cron supports PHP CLI directly. No URL containing a cron credential is needed.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require dirname(__DIR__).'/app/bootstrap.php';
if(!App::installed()){fwrite(STDERR,"not installed\n");exit(1);}
try{Migrations::ensure();TgQueue::schedule();$r=TgQueue::run(20);echo json_encode($r)."\n";}catch(Throwable $e){error_log('[divan] telegram worker failed');fwrite(STDERR,"worker failed; inspect server configuration\n");exit(1);}
