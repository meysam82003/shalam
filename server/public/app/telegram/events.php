<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class TgEvents
{
    // [Persian title, minimum permission, priority]. Only these event types can enter the queue.
    public const TYPES = [
        'registered'=>['👤 کاربر جدید در دیوان امتیاز','users.read',1],
        'purchase_success'=>['💰 خرید تأییدشده دیوان Pro','sales.read',2],
        'purchase_failed'=>['خرید ناموفق','sales.read',2],
        'payment_pending'=>['پرداخت طولانی در انتظار','sales.read',2],
        'refund'=>['بازپرداخت ثبت‌شده توسط فروشگاه','sales.read',3],
        'pro_activated'=>['فعال‌شدن Pro','users.read',1],
        'subscription_extended'=>['تمدید مدیریتی اشتراک','users.read',1],
        'subscription_expired'=>['پایان دوره اشتراک','users.read',1],
        'subscription_expiring'=>['نزدیک‌شدن پایان دوره اشتراک','users.read',1],
        'promo_used'=>['استفاده از کد تخفیف یا هدیه','sales.read',1],
        'ticket_created'=>['درخواست جدید پشتیبانی','support.read',2],
        'ticket_updated'=>['اقدام داخلی در تیکت','support.read',1],
        'gateway_error'=>['خطای درگاه پرداخت','sales.read',3],
        'store_error'=>['اختلال تأیید فروشگاه','sales.read',3],
        'bridge_error'=>['اختلال واسط پرداخت ایران','health',3],
        'backend_error'=>['خطای مهم Backend','health',3],
        'admin_unauthorized'=>['تلاش ورود غیرمجاز مدیر','audit.read',3],
        'sensitive_changed'=>['تغییر مدیریتی حساس','audit.read',2],
        'notice_changed'=>['ایجاد یا لغو اعلان تبلیغاتی','content.read',1],
        'backup_ok'=>['بکاپ سرور ساخته شد','health',1],
        'backup_failed'=>['بکاپ سرور ناموفق','health',3],
        'cron_gap'=>['وقفه در اجرای Cron','health',3],
        'suspicious'=>['رویداد امنیتی مشکوک','audit.read',3],
        'device_changed'=>['تغییر دستگاه یا نشست حساب','users.read',2],
        'digest'=>['خلاصه مدیریتی دیوان امتیاز','dashboard',1],
    ];
    public static function rules(): array
    {
        $saved=(array)Settings::get('tg_rules',[]);$out=[];
        foreach(self::TYPES as $type=>$spec) $out[$type]=($saved[$type] ?? [])+['enabled'=>true,'recipients'=>[],'priority'=>$spec[2],'hourly_cap'=>20];
        return $out;
    }
    public static function saveRule(array $d): void
    {
        $type=(string)($d['type'] ?? '');if(!isset(self::TYPES[$type]))throw new ApiError('event','نوع رویداد نامعتبر است.');
        $ids=array_values(array_unique(array_map('intval',(array)($d['recipients'] ?? []))));foreach($ids as $id) Management::row('tg_members',$id);
        $rules=self::rules();$rules[$type]=['enabled'=>!empty($d['enabled']),'recipients'=>$ids,'priority'=>Management::integer($d['priority'] ?? 1,1,3),'hourly_cap'=>Management::integer($d['hourly_cap'] ?? 20,1,200)];Settings::set('tg_rules',$rules);
    }
    public static function emit(string $type,string $entity,$id,string $dedup): void
    {
        if(!isset(self::TYPES[$type]) || !TgConfig::get()['bot_enabled'])return;
        $rule=self::rules()[$type];if(!$rule['enabled'])return;
        Db::tx(function() use($type,$entity,$id,$dedup,$rule) {
            $n=Db::run('INSERT IGNORE INTO {tg_events} (event_key,type,entity,entity_id,created_at) VALUES (?,?,?,?,?)',[hash('sha256',$type.'|'.$dedup),$type,$entity,(string)$id,Clock::sql()])->rowCount();
            if($n!==1)return;
            $eid=(int)Db::pdo()->lastInsertId();
            foreach(Db::all('SELECT m.*,a.role admin_role,a.username FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id WHERE m.active=1 AND a.active=1') as $m) {
                if(!TgPolicy::can($m,self::TYPES[$type][1]) || ($rule['recipients'] && !in_array((int)$m['id'],$rule['recipients'],true)) || ($type==='digest' && (string)$id!==(string)$m['id']))continue;
                Db::insert('tg_outbox',['event_id'=>$eid,'member_id'=>$m['id'],'epoch'=>$m['epoch'],'priority'=>$rule['priority'],'available_at'=>Clock::sql(),'created_at'=>Clock::sql()]);
            }
        });
    }
    /** Transient/log events must never break the app's original error handling. No secrets enter the outbox. */
    public static function signal(string $type,string $entity,$id,string $key): void
    {
        try { self::emit($type,$entity,$id,$key); } catch(Throwable $e) { error_log('[divan] telegram event persistence failed'); }
    }
    public static function audit(int $auditId,string $action,string $entity,$id): void
    {
        $map=['pay_order_paid'=>'purchase_success','purchase_refunded'=>'refund','activation_redeemed'=>'promo_used','device_bound'=>'device_changed','signed_out_elsewhere'=>'device_changed','user_release_device'=>'device_changed','user_transfer'=>'device_changed','notice_create'=>'notice_changed','notice_cancel'=>'notice_changed','notice_toggle'=>'notice_changed','tg_notice_save'=>'notice_changed','tg_notice_cancel'=>'notice_changed'];
        if(isset($map[$action]))self::emit($map[$action],$entity,(string)$id,$action.':'.$id.($map[$action]==='device_changed'?':'.$auditId:''));
        elseif(preg_match('/^(plan_|store|paymethod|bridge_|admin_|tg_member|tg_plan|tg_gateway|tg_promo|telegram_(save|member|revoke|sessions|webhook))/',$action))self::signal('sensitive_changed','audit',$auditId,'audit:'.$auditId);
        if(str_starts_with($action,'ticket_') || $action==='tg_ticket_save')self::signal('ticket_updated','support',$id,'audit:'.$auditId);
    }
    public static function security(int $eventId,string $kind,?int $userId): void
    {
        if($kind==='register')self::emit('registered','user',$userId,'user:'.$userId);
        elseif(in_array($kind,['admin_login_failed','admin_totp_failed','tg_unauthorized'],true))self::signal('admin_unauthorized','security',$eventId,$kind.':'.intdiv(Clock::now(),300));
        elseif(strpos($kind,'device_')===0)self::signal('device_changed','user',$userId,'security:'.$eventId);
        elseif(preg_match('/reuse|mismatch|bad_signature|throttled|unofficial/',$kind))self::signal('suspicious','security',$eventId,$kind.':'.($userId??0).':'.intdiv(Clock::now(),300));
    }
    public static function redact(string $msg): string
    {
        // Remote error bodies may echo credentials; remove URLs, long tokens and known encrypted-setting names.
        $msg=preg_replace('#https?://\S+#i','[نشانی حذف شد]',$msg);
        $msg=preg_replace('/[A-Za-z0-9_\-\/=+:.]{24,}/','[محرمانه]',$msg);
        return mb_substr($msg,0,250);
    }
    public static function date(?string $sql=null): string
    {
        return jdate($sql ?? Clock::sql());
    }

    public static function render(array $event,array $member): array
    {
        $type=$event['type'];$entity=$event['entity'];$id=$event['entity_id'];$route='dashboard';$target='';
        $text=self::TYPES[$type][0]."\n".self::date($event['created_at']);
        if($entity==='user') {
            $u=Db::one('SELECT id,username,created_at FROM {users} WHERE id=?',[$id]);
            if($u){$s=Entitlements::summary((int)$id);$store=Db::value('SELECT store FROM {sessions} WHERE user_id=? ORDER BY id LIMIT 1',[$id]);$text.="\nشناسه: ".$id."\nنام کاربری: ".$u['username']."\nکانال نخستین نشست: ".(['bazaar'=>'کافه‌بازار','myket'=>'مایکت','direct'=>'مستقیم','web'=>'وب'][$store??'']??'ثبت نشده')."\nوضعیت فعلی: ".($s['pro']?'Pro':'رایگان');}$route='user';$target=$id;
        }elseif($entity==='purchase' || $entity==='pay_order') {
            $p=$entity==='purchase'?Db::one('SELECT id,user_id,plan_id,amount_toman,store channel,status,order_id FROM {purchases} WHERE id=?',[$id]):Db::one('SELECT id,user_id,plan_id,amount_toman,method channel,status,code order_id FROM {pay_orders} WHERE id=?',[$id]);
            if($p) {$title=Db::value('SELECT title FROM {plans} WHERE id=?',[$p['plan_id']]);$text.="\nکاربر: ".$p['user_id']."\nپلن: ".$title."\nمبلغ: ".($p['amount_toman']===null?'ثبت نشده':fa_digits(number_format((int)$p['amount_toman'])).' تومان')."\nکانال: ".$p['channel']."\nسفارش: ".$p['order_id']."\nوضعیت فعلی: ".$p['status'];}$route=$entity==='purchase'?'transaction':'order';$target=$id;
        }elseif($entity==='support'){$route='ticket';$target=$id;$text.="\nشناسه درخواست: ".$id;}
        elseif($entity==='entitlement' || $entity==='grant'){$uid=Db::value('SELECT user_id FROM '.($entity==='grant'?'{user_grants}':'{entitlements}').' WHERE id=?',[$id]);$route='user';$target=(string)$uid;$text.="\nکاربر: ".$uid."\nشناسه دوره: ".$id;}
        elseif($entity==='audit' || $entity==='security'){$route='audit';$text.="\nشناسه رویداد: ".$id;}
        elseif($entity==='notice'){$route='notices';$target=$id;}
        elseif($type==='digest')return TgBot::summaryPayload($member,'dashboard');
        else {$route='status';$text.="\nمرجع: ".$entity.' #'.$id;}
        $keyboard=[[['text'=>'بازکردن جزئیات در مینی‌اپ','web_app'=>['url'=>TgConfig::appUrl($route,(string)$target)]]]];
        if($entity==='user')$keyboard=[
            [['text'=>'مشاهده پروفایل','web_app'=>['url'=>TgConfig::appUrl('user',$id)]]],
            [['text'=>'مشاهده خریدها','web_app'=>['url'=>TgConfig::appUrl('user',$id).'?tab=purchases']],['text'=>'مدیریت اشتراک','web_app'=>['url'=>TgConfig::appUrl('user',$id).'?tab=pro']]],
            [['text'=>'مدیریت دستگاه','web_app'=>['url'=>TgConfig::appUrl('user',$id).'?tab=devices']]]];
        return ['text'=>mb_substr($text,0,3800),'reply_markup'=>['inline_keyboard'=>$keyboard]];
    }
}

final class TgClient
{
    public static $testTransport=null;
    public static function call(string $method,array $params=[]): array
    {
        if(defined('DIVAN_TEST') && self::$testTransport) return (self::$testTransport)($method,$params);
        $allowed=['sendMessage','getMe','setWebhook','getWebhookInfo','getChatMenuButton','deleteWebhook','setChatMenuButton','setMyCommands','answerCallbackQuery'];
        if(!in_array($method,$allowed,true) || TgConfig::token()==='')return ['ok'=>false,'class'=>'permanent','error'=>'not_configured'];
        if(!function_exists('curl_init'))return ['ok'=>false,'class'=>'retry','error'=>'curl_missing'];
        $ch=curl_init('https://api.telegram.org/bot'.TgConfig::token().'/'.$method);
        curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>json_encode($params,JSON_UNESCAPED_UNICODE),CURLOPT_HTTPHEADER=>['Content-Type: application/json'],CURLOPT_RETURNTRANSFER=>true,CURLOPT_FOLLOWLOCATION=>false,CURLOPT_PROTOCOLS=>CURLPROTO_HTTPS,CURLOPT_SSL_VERIFYPEER=>true,CURLOPT_SSL_VERIFYHOST=>2,CURLOPT_CONNECTTIMEOUT=>4,CURLOPT_TIMEOUT=>10]);
        $raw=curl_exec($ch);$status=(int)curl_getinfo($ch,CURLINFO_HTTP_CODE);$errno=curl_errno($ch);curl_close($ch);
        $body=json_decode(is_string($raw)?$raw:'',true);
        if($status===200 && !empty($body['ok'])) {Settings::set('tg_connection',['ok'=>true,'at'=>Clock::sql()]);return ['ok'=>true,'result'=>$body['result']];}
        // Never store curl_error/remote description: the Bot API URL contains the secret.
        $retry=$status===429 || in_array($errno,[6,7],true);$uncertain=$status===0 && !$retry || $status>=500 || $status===200;
        $r=['ok'=>false,'class'=>$retry?'retry':($uncertain?'uncertain':'permanent'),'error'=>'telegram_http_'.$status.'_curl_'.$errno,'retry_after'=>max(1,min(86400,(int)($body['parameters']['retry_after'] ?? 30)))];
        Settings::set('tg_connection',['ok'=>false,'at'=>Clock::sql(),'error'=>$r['error']]);return $r;
    }
}

final class TgQueue
{
    public static function message(array $m,array $payload): void
    {
        Db::insert('tg_outbox',['member_id'=>$m['id'],'epoch'=>$m['epoch'],'payload_enc'=>Crypto::encrypt(json_encode($payload,JSON_UNESCAPED_UNICODE)),'available_at'=>Clock::sql(),'created_at'=>Clock::sql()]);
    }
    public static function quiet(array $m): bool
    {
        if($m['quiet_from']===null || $m['quiet_to']===null || $m['quiet_from']===$m['quiet_to'])return false;
        $hour=(int)gmdate('G',Clock::now()+12600);$from=(int)$m['quiet_from'];$to=(int)$m['quiet_to'];return $from<$to?($hour>=$from && $hour<$to):($hour>=$from || $hour<$to);
    }
    public static function run(int $limit=20): array
    {
        $counts=['sent'=>0,'retry'=>0,'uncertain'=>0,'dead'=>0,'skipped'=>0];$cfg=TgConfig::get();
        Settings::set('tg_worker_heartbeat',Clock::now());
        if(!$cfg['bot_enabled'] || $cfg['paused'])return $counts;
        // GET_LOCK serializes workers on cPanel without Redis; no HTTP call inside a business transaction.
        $lock='dv_tg_'.substr(hash('sha256',Db::$prefix.(string)App::cfg('db_name')),0,40);
        if((int)Db::value('SELECT GET_LOCK(?,0)',[$lock])!==1)return $counts;
        try {
            // A dead worker may have sent a message before dying. Do not blindly resend it.
            Db::run("UPDATE {tg_outbox} SET state='uncertain',last_error='worker_interrupted' WHERE state='sending' AND locked_at<?",[Clock::sql(Clock::now()-120)]);
            $rows=Db::all("SELECT * FROM {tg_outbox} WHERE state='pending' AND available_at<=? ORDER BY priority DESC,id LIMIT ".max(1,min(50,$limit)),[Clock::sql()]);
            foreach($rows as $q){
                Settings::reset();$live=TgConfig::get();if(!$live['bot_enabled'] || $live['paused'])break;
                $m=Db::one('SELECT m.*,a.role admin_role,a.username FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id WHERE m.id=? AND m.active=1 AND a.active=1',[$q['member_id']]);
                $event=$q['event_id']?Db::one('SELECT * FROM {tg_events} WHERE id=?',[$q['event_id']]):null;
                $allowed=$m && (int)$m['epoch']===(int)$q['epoch'];$rule=null;
                if($event){$rule=TgEvents::rules()[$event['type']] ?? null;$allowed=$allowed && $rule && $rule['enabled'] && TgPolicy::can($m,TgEvents::TYPES[$event['type']][1]) && (!$rule['recipients'] || in_array((int)$m['id'],$rule['recipients'],true));}
                if($event && $event['type']==='digest')$allowed=$allowed && $m['digest']!=='off' && TgPolicy::can($m,'dashboard');
                if(!$allowed){Db::update('tg_outbox',['state'=>'skipped','payload_enc'=>null],'id=?',[$q['id']]);$counts['skipped']++;continue;}
                if($event && self::quiet($m)){Db::update('tg_outbox',['available_at'=>Clock::sql(Clock::now()+300)],'id=?',[$q['id']]);continue;}
                if(!TgLimit::hit('send:'.$m['id'],1,2) || ($rule && !TgLimit::hit('event:'.$m['id'].':'.$event['type'],(int)$rule['hourly_cap'],3600))){Db::update('tg_outbox',['available_at'=>Clock::sql(Clock::now()+60)],'id=?',[$q['id']]);continue;}
                $payload=$event?TgEvents::render($event,$m):json_decode((string)Crypto::decrypt($q['payload_enc']),true);
                if(!is_array($payload)){Db::update('tg_outbox',['state'=>'dead','last_error'=>'invalid_payload'],'id=?',[$q['id']]);continue;}
                if(!$event && isset($payload['_permission']) && !TgPolicy::can($m,$payload['_permission'])){Db::update('tg_outbox',['state'=>'skipped','payload_enc'=>null],'id=?',[$q['id']]);continue;}
                if(isset($payload['_summary']))$payload=TgBot::summaryPayload($m,$payload['_summary']);
                unset($payload['_permission']);
                Db::update('tg_outbox',['state'=>'sending','locked_at'=>Clock::sql(),'attempts'=>(int)$q['attempts']+1],'id=?',[$q['id']]);
                $r=TgClient::call('sendMessage',$payload+['chat_id'=>(string)$m['telegram_id'],'protect_content'=>true,'link_preview_options'=>['is_disabled'=>true]]);
                if($r['ok']){$upd=['state'=>'sent','sent_at'=>Clock::sql(),'message_id'=>$r['result']['message_id']??null,'payload_enc'=>null,'last_error'=>null];$counts['sent']++;}
                else{$state=$r['class']==='uncertain'?'uncertain':($r['class']==='retry' && (int)$q['attempts']<5?'pending':'dead');$delay=max($r['retry_after']??30,min(3600,30*(2**(int)$q['attempts'])));$upd=['state'=>$state,'last_error'=>$r['error'],'available_at'=>Clock::sql(Clock::now()+$delay)];$counts[$state==='pending'?'retry':$state]++;}
                Db::update('tg_outbox',$upd,'id=?',[$q['id']]);
            }
        }finally{Db::value('SELECT RELEASE_LOCK(?)',[$lock]);}
        return $counts;
    }
    public static function schedule(): void
    {
        $cfg=TgConfig::get();if(!$cfg['bot_enabled'])return;
        $last=(int)Settings::get('tg_scheduler_heartbeat',0);
        if($last && $last<Clock::now()-900)TgEvents::emit('cron_gap','cron',0,'gap:'.$last);
        Settings::set('tg_scheduler_heartbeat',Clock::now());
        // Skip already emitted keys in SQL so the first batch cannot starve later records.
        foreach(['entitlements'=>['entitlement','ent',"status='active'"],'user_grants'=>['grant','grant',"feature_k='pro' AND revoked_at IS NULL"]] as $table=>$spec){
            foreach(['subscription_expiring'=>[Clock::sql(),Clock::sql(Clock::now()+3*86400)],'subscription_expired'=>[Clock::sql(Clock::now()-86400),Clock::sql()]] as $type=>$range){
                $rows=Db::all("SELECT s.id,s.expires_at FROM {".$table."} s WHERE ".$spec[2]." AND s.expires_at>? AND s.expires_at<=? AND NOT EXISTS(SELECT 1 FROM {tg_events} e WHERE e.event_key=SHA2(CONCAT(?,s.id,':',s.expires_at),256)) ORDER BY s.expires_at,s.id LIMIT 1000",[$range[0],$range[1],$type.'|'.$spec[1].':']);
                foreach($rows as $e)TgEvents::emit($type,$spec[0],$e['id'],$spec[1].':'.$e['id'].':'.$e['expires_at']);
            }
        }
        $cut=Clock::sql(Clock::now()-max(5,(int)$cfg['pending_minutes'])*60);
        foreach(['pay_orders'=>['pay_order','order',"status='pending'"],'purchases'=>['purchase','purchase',"status IN ('pending','signature_verified')"]] as $table=>$spec){
            $rows=Db::all("SELECT s.id FROM {".$table."} s WHERE ".$spec[2]." AND s.created_at<? AND NOT EXISTS(SELECT 1 FROM {tg_events} e WHERE e.event_key=SHA2(CONCAT(?,s.id),256)) ORDER BY s.id LIMIT 1000",[$cut,'payment_pending|'.$spec[1].':']);
            foreach($rows as $r)TgEvents::emit('payment_pending',$spec[0],$r['id'],$spec[1].':'.$r['id']);
        }
        $local=Clock::now()+12600;$hour=(int)gmdate('G',$local);
        if($hour===9)foreach(Db::all("SELECT m.*,a.role admin_role,a.username FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id WHERE m.active=1 AND a.active=1 AND m.digest<>'off'") as $m){
            if($m['digest']==='weekly' && gmdate('w',$local)!=='6')continue;
            $key='digest:'.$m['id'].':'.gmdate('Y-m-d',$local);
            TgEvents::emit('digest','digest',$m['id'],$key);
        }
        $cutoff=Clock::sql(Clock::now()-max(7,min(90,(int)$cfg['retention_days']))*86400);
        Db::run("DELETE FROM {tg_outbox} WHERE state IN ('sent','skipped','dead','uncertain') AND created_at<?",[$cutoff]);
        // Minimal event-key tombstones stay: pruning them would re-emit old business events.
        Db::run('DELETE FROM {tg_replays} WHERE expires_at<?',[Clock::sql()]);Db::run('DELETE FROM {tg_links} WHERE expires_at<?',[$cutoff]);Db::run('DELETE FROM {tg_sessions} WHERE expires_at<?',[$cutoff]);Db::run('DELETE FROM {tg_requests} WHERE created_at<?',[$cutoff]);Db::run('DELETE FROM {tg_updates} WHERE created_at<?',[$cutoff]);Db::run('DELETE FROM {tg_limits} WHERE window_start<?',[Clock::now()-172800]);
    }
}
