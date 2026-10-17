<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }

/** Read-only deployment diagnostics. Passing these checks is not a production acceptance certificate. */
final class TgReadiness
{
    public static function probe(): array
    {
        TgLimit::require('tg-installation-check',3,300);
        $me=TgClient::call('getMe');
        $hook=TgClient::call('getWebhookInfo');
        $menu=TgClient::call('getChatMenuButton');
        $info=(array)($hook['result'] ?? []);$button=(array)($menu['result'] ?? []);
        // Store booleans/numbers only, never Telegram bodies, error descriptions or credentials.
        $r=['at'=>Clock::now(),'identity_ok'=>!empty($me['ok']) && !empty($me['result']['is_bot']),
            'webhook_ok'=>!empty($hook['ok']) && ($info['url'] ?? '')===App::baseUrl().'/tg/webhook.php',
            'webhook_last_error'=>(int)($info['last_error_date'] ?? 0),
            'pending_updates'=>(int)($info['pending_update_count'] ?? 0),
            'menu_ok'=>!empty($menu['ok']) && ($button['type'] ?? '')==='web_app' && ($button['web_app']['url'] ?? '')===TgConfig::get()['app_url']];
        Settings::set('tg_installation_probe',$r);return self::report();
    }
    public static function report(): array
    {
        $rows=[];$add=function(string $key,string $title,bool $ok,string $detail)use(&$rows){$rows[]=['key'=>$key,'title'=>$title,'ok'=>$ok,'detail'=>$detail];};
        $cfg=TgConfig::get();$now=Clock::now();
        $add('php','نسخه PHP',PHP_VERSION_ID>=70400,PHP_VERSION);
        $required=['pdo_mysql','openssl','mbstring','curl','json','fileinfo','zlib','gd','dom'];
        $missing=array_values(array_filter($required,fn($e)=>!extension_loaded($e)));
        $add('extensions','افزونه‌های PHP',!$missing,$missing?implode('، ',$missing):'همه افزونه‌های مورد نیاز نصب‌اند.');
        $add('https','HTTPS و مسیر مینی‌اپ',stripos(App::baseUrl(),'https://')===0 && $cfg['app_url']===App::baseUrl().'/tg/app/','آدرس‌ها باید روی دامنه و مسیر همین نصب باشند.');
        $version=(int)Db::value('SELECT MAX(version) FROM {migrations}');
        $add('schema','نسخه ساختار دیتابیس',$version===12,'نسخه فعلی: '.$version.'؛ نسخه مورد انتظار این بسته: ۱۲');
        $tables=['users','purchases','entitlements','user_grants','audit_log','tg_members','tg_links','tg_sessions','tg_replays','tg_requests','tg_events','tg_outbox','tg_updates','tg_limits','tg_ticket_meta'];
        $names=array_map(fn($t)=>Db::$prefix.$t,$tables);
        $engines=Db::all('SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('.implode(',',array_fill(0,count($names),'?')).')',$names);
        $transactional=count($engines)===count($names);foreach($engines as $e)$transactional=$transactional && strtoupper((string)$e['ENGINE'])==='INNODB';
        $add('transactions','جدول‌های تراکنشی',$transactional,'همه جدول‌های مورد نیاز باید موجود و InnoDB باشند.');
        $add('secrets','توکن و راز Webhook',TgConfig::token()!=='' && Settings::secret('tg_webhook_secret')!=='','فقط وجود تنظیمات بررسی می‌شود؛ کلیدها نمایش داده نمی‌شوند.');
        $add('enabled','ربات، مینی‌اپ و ارسال صف',$cfg['bot_enabled'] && $cfg['app_enabled'] && !$cfg['paused'],'هر سه باید برای بهره‌برداری فعال باشند.');
        $linked=(int)Db::value("SELECT COUNT(*) FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id WHERE m.active=1 AND a.active=1 AND m.role='owner' AND a.role='owner'");
        $add('owner','اتصال مالک',$linked>0,'حساب مالک وب و تلگرام باید فعال و متصل باشند.');
        $beat=(int)Settings::get('tg_worker_heartbeat',0);
        $add('cron','اجرای Cron',$beat>0 && $beat<=$now && $now-$beat<=300,$beat?'آخرین اجرا: '.jdate(Clock::sql($beat)):'هنوز Worker اجرا نشده است.');
        $r=(array)Settings::get('tg_installation_probe',[]);$at=(int)($r['at'] ?? 0);$fresh=$at>0 && $at<=$now && $now-$at<=900;
        $add('connection','آزمایش واقعی Telegram API',$fresh && !empty($r['identity_ok']),'نتیجه آزمون حداکثر ۱۵ دقیقه معتبر است؛ دکمه بررسی اتصال را بزنید.');
        $add('webhook','ثبت آدرس Webhook',$fresh && !empty($r['webhook_ok']),'آدرس ثبت‌شده باید با همین نصب برابر باشد؛ دریافت واقعی پیام جداگانه آزمایش شود.');
        $last=(int)($r['webhook_last_error'] ?? 0);
        $add('webhook_error','خطای اخیر تحویل Webhook',$fresh && ($last===0 || $now-$last>900),'نباید خطای تحویل طی ۱۵ دقیقه اخیر ثبت شده باشد.');
        $add('menu','دکمه Mini App',$fresh && !empty($r['menu_ok']),'منوی پیش‌فرض ربات باید آدرس همین مینی‌اپ را باز کند.');
        $failed=(int)Db::value("SELECT COUNT(*) FROM {tg_outbox} WHERE state IN ('dead','uncertain') AND created_at>=?",[Clock::sql($now-86400)]);
        $add('queue','خطاهای ارسال ۲۴ ساعت اخیر',$failed===0,$failed.' پیام ناموفق یا با نتیجه نامشخص؛ بخش صف را بررسی کنید.');
        return ['checked_at'=>Clock::sql(),'checks'=>$rows,'checks_passed'=>count(array_filter($rows,fn($r)=>!$r['ok']))===0,'production_approved'=>false,
            'acceptance_remaining'=>['آزمایش ورود و لغو دسترسی در Telegram واقعی','دریافت یک‌باره اعلان ثبت‌نام و خرید آزمایشی در staging','آزمایش Android، iOS، وب و موبایل اقتصادی','تطبیق سورس هاست و نسخه اپ با این بسته و آزمون پرداخت در محیط آزمایشی رسمی']];
    }
}
