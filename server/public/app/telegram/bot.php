<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class TgBot
{
    public const COMMANDS=['start'=>'شروع','panel'=>'پنل مدیریت','dashboard'=>'داشبورد','users'=>'کاربران','sales'=>'فروش','orders'=>'تراکنش‌ها','support'=>'پشتیبانی','alerts'=>'هشدارها','status'=>'سلامت سامانه','help'=>'راهنما','cancel'=>'لغو'];
    public static function summary(array $m,string $section): array
    {
        // Regenerate at delivery, so a web-role downgrade cannot disclose cached financial data.
        TgPolicy::require($m,$section==='sales'?'sales.read':'dashboard');
        return ['_summary'=>$section,'_permission'=>$section==='sales'?'sales.read':'dashboard'];
    }
    public static function summaryPayload(array $m,string $section): array
    {
        $d=TgRead::dashboard($m);$text="دیوان امتیاز | خلاصه مدیریتی\n".jdate(Clock::sql());
        if($section!=='sales' && isset($d['users']))$text.="\nکل کاربران: ".fa_digits($d['users']['total'])."\nکاربران جدید امروز: ".fa_digits($d['users']['today'])."\nPro: ".fa_digits($d['users']['pro']);
        if(isset($d['sales']))$text.="\nفروش تأییدشده امروز: ".money($d['sales']['today'])."\n۷ روز اخیر: ".money($d['sales']['week'])."\n۳۰ روز اخیر: ".money($d['sales']['month'])."\nدرآمد ثبت‌شده: ".money($d['sales']['total']);
        return ['text'=>$text,'reply_markup'=>['inline_keyboard'=>[[['text'=>'بازکردن مرکز مدیریت','web_app'=>['url'=>TgConfig::appUrl('dashboard')]]]]]];
    }
    public static function handle(array $update): void
    {
        if(!TgConfig::get()['bot_enabled'])return;
        $updateId=$update['update_id'] ?? null;if(!is_int($updateId) || $updateId<0)return;
        $callback=$update['callback_query'] ?? null;$message=$callback['message'] ?? ($update['message'] ?? null);$from=$callback['from'] ?? ($message['from'] ?? null);
        if(!is_array($message)||!is_array($from)||($message['chat']['type']??'')!=='private'||!empty($from['is_bot'])||(string)($message['chat']['id']??'')!==(string)($from['id']??''))return;
        // Old updates cannot revive commands after queue retention; callback comes from a currently sent message.
        if(!$callback && (int)($message['date']??0)<Clock::now()-86400)return;
        $tid=(string)($from['id']??'');TgLimit::require('bot:'.$tid,25,60);
        Db::tx(function() use($updateId,$callback,$message,$tid) {
            $n=Db::run('INSERT IGNORE INTO {tg_updates} (update_id,created_at) VALUES (?,?)',[$updateId,Clock::sql()])->rowCount();if($n!==1)return;
            $m=TgPolicy::member($tid);$text=trim((string)($message['text']??''));
            if(!$callback && preg_match('#^/start(?:@[A-Za-z0-9_]+)? link_([A-Za-z0-9_-]{32})$#D',$text,$match)){
                try { $m=TgAuth::link($match[1],$tid); } catch (ApiError $e) { Audit::security('tg_link_failed',null); return; }TgQueue::message($m,['text'=>'اتصال امن انجام شد. از این پس ورود معمولی بدون نام کاربری، رمز یا کد است.','reply_markup'=>['inline_keyboard'=>[[['text'=>'ورود به مینی‌اپ','web_app'=>['url'=>TgConfig::appUrl()]]]]]]);return;
            }
            if(!$m){Audit::security('tg_unauthorized',null);return;}
            if($callback){$text=(string)($callback['data']??'');if(!preg_match('/^users:([1-9][0-9]{0,4})$/D',$text,$match))return;$command='users';$arg='';$page=(int)$match[1];}
            else{if(!preg_match('#^/([a-z]+)(?:@[A-Za-z0-9_]+)?(?:\s+(.*))?$#uD',$text,$match))return;$command=$match[1];$arg=$match[2]??'';$page=1;}
            $payload=['text'=>'','_permission'=>'dashboard'];
            try { switch($command){
                case 'start':case 'panel':$payload=['text'=>'مرکز فرماندهی دیوان امتیاز','reply_markup'=>['inline_keyboard'=>[[['text'=>'ورود به پنل','web_app'=>['url'=>TgConfig::appUrl()]]]]]];break;
                case 'dashboard':case 'sales':$payload=self::summary($m,$command);break;
                case 'users':
                    TgPolicy::require($m,'users.read');$r=TgRead::list($m,'users',['q'=>$arg,'page'=>$page],5);$payload=['text'=>'کاربران — صفحه '.fa_digits($page),'_permission'=>'users.read'];$buttons=[];
                    foreach($r['rows'] as $u){$payload['text'].="\n#".$u['id'].' '.$u['username'].' • '.($u['pro']?'Pro':'رایگان');$buttons[]=[['text'=>$u['username'],'web_app'=>['url'=>TgConfig::appUrl('user',(string)$u['id'])]]];}
                    if($arg==='' && $r['total']>$page*5)$buttons[]=[['text'=>'صفحه بعد','callback_data'=>'users:'.($page+1)]];$payload['reply_markup']=['inline_keyboard'=>$buttons];break;
                case 'orders':
                    TgPolicy::require($m,'sales.read');$r=TgRead::list($m,'orders',[],5);$payload=['text'=>'سفارش‌های اخیر نسخه مستقیم','_permission'=>'sales.read'];$buttons=[];foreach($r['rows'] as $o){$payload['text'].="\n#".$o['id'].' '.money($o['amount_toman']).' • '.$o['status'];$buttons[]=[['text'=>'جزئیات #'.$o['id'],'web_app'=>['url'=>TgConfig::appUrl('order',(string)$o['id'])]]];}$buttons[]=[['text'=>'خریدهای بازار و مایکت','web_app'=>['url'=>TgConfig::appUrl('transactions')]]];$payload['reply_markup']=['inline_keyboard'=>$buttons];break;
                case 'support':TgPolicy::require($m,'support.read');$count=Db::value("SELECT COUNT(*) FROM {support_requests} WHERE status<>'resolved'");$payload=['text'=>'درخواست‌های باز: '.fa_digits($count),'_permission'=>'support.read','reply_markup'=>['inline_keyboard'=>[[['text'=>'پشتیبانی','web_app'=>['url'=>TgConfig::appUrl('support')]]]]]];break;
                case 'alerts':$payload=['text'=>'تنظیم گیرندگان، سکوت و خلاصه‌ها از این بخش:','reply_markup'=>['inline_keyboard'=>[[['text'=>'تنظیم هشدارها','web_app'=>['url'=>TgConfig::appUrl('alerts')]]]]]];break;
                case 'status':$s=TgRead::status($m);$payload=['text'=>'وضعیت Worker: '.($s['worker_stale']?'نیازمند بررسی':'Heartbeat تازه')."\nاتصال واقعی: ".(($s['connection']['ok']??false)?'آخرین تماس موفق بوده':'تأیید نشده یا ناموفق'),'_permission'=>'health'];break;
                case 'cancel':$payload=['text'=>'عملیات نوشتنی در چت آغاز نمی‌شود. برای لغو فرم مینی‌اپ، دکمه بستن را بزنید.'];break;
                default:$payload=['text'=>"دستورات مدیریت:\n".implode("\n",array_map(fn($k,$v)=>'/'.$k.' — '.$v,array_keys(self::COMMANDS),self::COMMANDS))."\nجست‌وجو: /users username\nعملیات حساس فقط داخل مینی‌اپ با مجوز نقش مدیر انجام می‌شود."];break;
            }
            } catch (ApiError $e) { $payload=['text'=>'این فرمان برای نقش شما مجاز نیست یا ورودی معتبر ندارد.']; }
            TgQueue::message($m,$payload);Audit::log('admin',(int)$m['admin_id'],'tg_command','bot',null,'/'.$command);
        });
    }
    public static function webhook(): void
    {
        try{TgConfig::requireHttps();if(!App::installed())throw new ApiError('unavailable','',503);Migrations::ensure();
            $secret=Settings::secret('tg_webhook_secret');if($_SERVER['REQUEST_METHOD']!=='POST'||$secret===''||!hash_equals($secret,Http::header('X-Telegram-Bot-Api-Secret-Token')))throw new ApiError('forbidden','',403);
            $raw=file_get_contents('php://input',false,null,0,65537);if(strlen((string)$raw)>65536)throw new ApiError('size','',413);$u=json_decode((string)$raw,true);if(!is_array($u))throw new ApiError('body','',400);self::handle($u);Http::json(['ok'=>true]);
        }catch(ApiError $e){$status=in_array($e->status,[401,403],true)?403:$e->status;Http::json(['ok'=>false],$status);}
        catch(Throwable $e){error_log('[divan] tg webhook failed');Http::json(['ok'=>false],503);}
    }
}
