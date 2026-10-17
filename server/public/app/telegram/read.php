<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class TgRead
{
    // Explicit public projections. Never SELECT * from users, sessions, purchases, settings or admins for a response.
    public const RESOURCES = [
        'users'=>['users','u.id,u.username,u.status,u.created_at,u.last_login_at','created_at','users.read'],
        'transactions'=>['purchases','u.id,u.user_id,u.store,u.plan_id,u.sku,u.order_id,u.amount_toman,u.status,u.verify_method,u.created_at,u.last_verified_at','created_at','sales.read'],
        'orders'=>['pay_orders','u.id,u.code,u.user_id,u.plan_id,u.method,u.channel,u.amount_toman,u.status,u.paid_at,u.created_at','created_at','sales.read'],
        'subscriptions'=>['entitlements','u.id,u.user_id,u.source,u.plan_code,u.starts_at,u.expires_at,u.status,u.created_at','created_at','sales.read'],
        'plans'=>['plans','u.id,u.code,u.title,u.description,u.duration_days,u.base_price_toman,u.sort,u.active,u.badge_text,u.badge_color,u.renewal_note,u.purchase_limit,u.sku_bazaar,u.sku_myket,u.type_bazaar,u.type_myket,u.store_price_bazaar,u.store_price_myket,u.list_price_bazaar,u.list_price_myket,u.on_bazaar,u.on_myket,u.updated_at','updated_at','sales.read'],
        'promos'=>['promo_codes','u.id,u.code,u.kind,u.days,u.feature_k,u.plan_code,u.percent,u.max_uses,u.uses,u.starts_at,u.ends_at,u.active,u.created_at','created_at','sales.read'],
        'campaigns'=>['campaigns','u.id,u.name,u.plan_id,u.store,u.price_toman,u.label,u.label_color,u.starts_at,u.ends_at,u.active,u.created_at','created_at','sales.read'],
        'ads'=>['ads','u.id,u.name,u.title,u.body,u.format,u.media_id,u.target_url,u.cta_text,u.style_json,u.closable,u.starts_at,u.ends_at,u.total_cap,u.daily_cap,u.min_interval_sec,u.priority,u.placements,u.stores,u.active,u.impressions_total,u.created_at,u.updated_at','created_at','content.read'],
        'media'=>['media','u.id,u.kind,u.path,u.mime,u.size,u.width,u.height,u.original_name,u.created_at','created_at','content.read'],
        'notices'=>['notices','u.id,u.title,u.body,u.media_id,u.target,u.audience,u.stores,u.send_at,u.ends_at,u.active,u.canceled_at,u.shown,u.opened,u.created_at','created_at','content.read'],
        'support'=>['support_requests','u.id,u.user_id,u.username,u.kind,u.store,u.status,u.created_at,u.updated_at','created_at','support.read'],
        'audit'=>['audit_log','u.id,u.actor_type,u.actor_id,u.action,u.entity,u.entity_id,u.created_at','created_at','audit.read'],
        'security'=>['security_events','u.id,u.kind,u.user_id,u.created_at','created_at','audit.read'],
        'ad_stats'=>['ad_stats','u.day,u.ad_id,u.source,u.placement,u.requests,u.fills,u.impressions,u.clicks,u.closes,u.rewards,u.errors','day','content.read'],
        'backups'=>['backups','u.id,u.kind,u.status,u.size,u.created_at','created_at','health'],
    ];
    public static function proSql(string $alias='u'): string
    {
        // UTC_TIMESTAMP avoided so test clock and current business logic share a clock.
        $now=Db::pdo()->quote(Clock::sql());
        return "(EXISTS(SELECT 1 FROM {entitlements} e WHERE e.user_id=$alias.id AND e.status='active' AND e.starts_at<=$now AND (e.expires_at IS NULL OR e.expires_at>$now)) OR EXISTS(SELECT 1 FROM {user_grants} g WHERE g.user_id=$alias.id AND g.feature_k='pro' AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at>$now)))";
    }
    public static function list(array $m,string $resource,array $in=[],int $size=25): array
    {
        if(!isset(self::RESOURCES[$resource]))throw new ApiError('resource','بخش موجود نیست.',404);
        [$table,$cols,$date,$permission]=self::RESOURCES[$resource];TgPolicy::require($m,$permission);
        $where=['1=1'];$args=[];$q=mb_substr(trim((string)($in['q'] ?? '')),0,80);
        if($q!=='') {
            $field=['users'=>'username','transactions'=>'order_id','orders'=>'code','promos'=>'code','ads'=>'name','support'=>'username','audit'=>'action','security'=>'kind','media'=>'original_name','plans'=>'title','campaigns'=>'name','notices'=>'title'][$resource] ?? null;
            if($field){$where[]="(u.$field LIKE ? OR u.id=?)";$args[]='%'.str_replace(['\\','%','_'],['\\\\','\\%','\\_'],$q).'%';$args[]=ctype_digit($q)?(int)$q:0;}
            elseif($resource==='subscriptions'){$where[]='u.user_id=?';$args[]=(int)$q;}
        }
        if(!empty($in['id'])){$where[]='u.id=?';$args[]=(int)$in['id'];}
        if(!empty($in['user_id']) && in_array($resource,['transactions','orders','subscriptions','support'],true)){$where[]='u.user_id=?';$args[]=(int)$in['user_id'];}
        if(!empty($in['status']) && in_array($resource,['users','transactions','orders','subscriptions','support'],true)){$where[]='u.status=?';$args[]=mb_substr((string)$in['status'],0,24);}
        if(!empty($in['store']) && in_array($resource,['transactions','support','campaigns'],true)){$where[]='u.store=?';$args[]=(string)$in['store'];}
        if($resource==='users' && in_array($in['pro'] ?? '',['pro','free'],true))$where[]=($in['pro']==='free'?'NOT ':'').self::proSql();
        if($resource==='users')$cols.=', '.self::proSql().' pro';
        if($resource==='support'){$cols.=", (SELECT priority FROM {tg_ticket_meta} tm WHERE tm.request_id=u.id) priority, (SELECT read_at FROM {tg_ticket_meta} tm WHERE tm.request_id=u.id) read_at";if(($in['unread'] ?? '')==='1')$where[]='NOT EXISTS(SELECT 1 FROM {tg_ticket_meta} tm WHERE tm.request_id=u.id AND tm.read_at IS NOT NULL)';}
        foreach(['from'=>'>=','to'=>'<'] as $key=>$op)if(!empty($in[$key])){$dt=Management::date($in[$key]);$where[]="u.$date $op ?";$args[]=$dt;}
        $where=implode(' AND ',$where);$page=max(1,min(100000,(int)($in['page'] ?? 1)));$size=max(1,min(1000,$size));$offset=($page-1)*$size;
        $sort=$resource==='ad_stats'?'day':($resource==='plans'?'sort':'id');$dir=($in['sort'] ?? '')==='oldest'?'ASC':'DESC';
        $rows=Db::all("SELECT $cols FROM {{$table}} u WHERE $where ORDER BY u.$sort $dir LIMIT $size OFFSET $offset",$args);
        if($resource==='media')foreach($rows as &$r)$r['url']=App::baseUrl().'/media/'.$r['path'];
        return ['rows'=>$rows,'total'=>(int)Db::value("SELECT COUNT(*) FROM {{$table}} u WHERE $where",$args),'page'=>$page,'per_page'=>$size];
    }
    public static function detail(array $m,string $kind,int $id): array
    {
        if($kind==='user'){
            TgPolicy::require($m,'users.read');$u=Db::one('SELECT id,username,status,created_at,last_login_at,note,device_moves FROM {users} WHERE id=?',[$id]);if(!$u)throw new ApiError('not_found','کاربر پیدا نشد.',404);
            $out=['user'=>$u,'subscription'=>Entitlements::summary($id),'devices'=>Devices::list($id),'sessions'=>Sessions::list($id),'device_limit'=>Devices::limit(),
                'entitlements'=>Db::all('SELECT id,source,plan_code,starts_at,expires_at,status FROM {entitlements} WHERE user_id=? ORDER BY id DESC LIMIT 100',[$id]),'grants'=>Db::all('SELECT id,feature_k,expires_at,revoked_at,created_at FROM {user_grants} WHERE user_id=? ORDER BY id DESC LIMIT 100',[$id]),
                'actions'=>Db::all('SELECT id,admin_id,action,reason,created_at FROM {support_actions} WHERE user_id=? ORDER BY id DESC LIMIT 100',[$id])];
            // Device secrets/identifiers and historic arbitrary details do not leave this endpoint.
            $out['devices']=array_map(fn($d)=>array_intersect_key($d,array_flip(['id','name','bound_at','seen_at'])),$out['devices']);
            if(TgPolicy::can($m,'sales.read')){$out['purchases']=self::list($m,'transactions',['user_id'=>$id])['rows'];$out['orders']=self::list($m,'orders',['user_id'=>$id])['rows'];}
            if(TgPolicy::can($m,'support.read'))$out['tickets']=self::list($m,'support',['user_id'=>$id])['rows'];return $out;
        }
        if($kind==='ticket'){
            TgPolicy::require($m,'support.read');$t=Db::one('SELECT id,user_id,username,contact,kind,store,order_id,message,status,created_at,updated_at FROM {support_requests} WHERE id=?',[$id]);if(!$t)throw new ApiError('not_found','درخواست پیدا نشد.',404);
            return ['ticket'=>$t,'meta'=>Db::one('SELECT priority,read_at FROM {tg_ticket_meta} WHERE request_id=?',[$id]),'actions'=>Db::all('SELECT id,admin_id,action,reason,created_at FROM {support_actions} WHERE request_id=? ORDER BY id DESC LIMIT 100',[$id]),'reply_supported'=>false];
        }
        $resource=['transaction'=>'transactions','order'=>'orders','ad'=>'ads','plan'=>'plans','promo'=>'promos','campaign'=>'campaigns','notice'=>'notices'][$kind] ?? '';
        $rows=self::list($m,$resource,['id'=>$id])['rows'];if(!$rows)throw new ApiError('not_found','رکورد پیدا نشد.',404);$result=['item'=>$rows[0]];
        if($kind==='promo')$result['redemptions']=Db::all('SELECT user_id,purchase_id,created_at FROM {promo_redemptions} WHERE code_id=? ORDER BY id DESC LIMIT 100',[$id]);return $result;
    }
    /** Union excludes direct purchase rows: activation-code orders count once, at payment, even before redemption. */
    public static function salesSql(): string
    {
        return "SELECT id,'purchase' kind,user_id,plan_id,store channel,amount_toman,COALESCE(purchase_time,created_at) at FROM {purchases} WHERE status='verified' AND store IN ('bazaar','myket') UNION ALL SELECT id,'order' kind,user_id,plan_id,method channel,amount_toman,paid_at at FROM {pay_orders} WHERE status='paid'";
    }
    public static function dashboard(array $m): array
    {
        TgPolicy::require($m,'dashboard');$now=Clock::now();$today=Clock::sql(strtotime(gmdate('Y-m-d',$now+12600).' 00:00:00 UTC')-12600);$week=Clock::sql($now-7*86400);$month=Clock::sql($now-30*86400);$out=['generated_at'=>Clock::sql(),'timezone'=>'Asia/Tehran','windows'=>['today'=>$today,'week'=>$week,'month'=>$month]];
        if(TgPolicy::can($m,'users.read') || TgPolicy::can($m,'reports')) {
            $pro=self::proSql();
            $out['users']=Db::one("SELECT COUNT(*) total,COALESCE(SUM(created_at>=?),0) today,COALESCE(SUM(created_at>=?),0) week,COALESCE(SUM(created_at>=?),0) month,COALESCE(SUM(status='active'),0) enabled,COALESCE(SUM($pro),0) pro FROM {users} u",[$today,$week,$month]);$out['users']['free']=(int)$out['users']['total']-(int)$out['users']['pro'];
            $out['users']['active_7d']=(int)Db::value('SELECT COUNT(DISTINCT user_id) FROM {sessions} WHERE last_seen_at>=?',[$week]);
            $out['users']['devices']=(int)Db::value('SELECT COUNT(*) FROM {pro_devices}');
            $out['users']['expired']=(int)Db::value("SELECT COUNT(*) FROM {users} u WHERE NOT $pro AND EXISTS(SELECT 1 FROM {entitlements} e WHERE e.user_id=u.id AND e.expires_at<=?)",[Clock::sql()]);
            $out['users']['expiring_periods']=(int)Db::value("SELECT COUNT(*) FROM {entitlements} WHERE status='active' AND expires_at>? AND expires_at<=?",[Clock::sql(),Clock::sql($now+3*86400)]);
            $out['registrations']=Db::all('SELECT DATE(DATE_ADD(created_at,INTERVAL 210 MINUTE)) day,COUNT(*) count FROM {users} WHERE created_at>=? GROUP BY day ORDER BY day',[$month]);
        }
        if(TgPolicy::can($m,'sales.read')) {
            $sql=self::salesSql();$out['sales']=Db::one("SELECT COUNT(*) count,COALESCE(SUM(amount_toman),0) total,COALESCE(SUM(CASE WHEN at>=? THEN amount_toman ELSE 0 END),0) today,COALESCE(SUM(CASE WHEN at>=? THEN amount_toman ELSE 0 END),0) week,COALESCE(SUM(CASE WHEN at>=? THEN amount_toman ELSE 0 END),0) month,COALESCE(SUM(amount_toman IS NULL),0) unknown_amounts FROM ($sql) s",[$today,$week,$month]);
            $out['daily_sales']=Db::all("SELECT DATE(DATE_ADD(at,INTERVAL 210 MINUTE)) day,COALESCE(SUM(amount_toman),0) amount,COUNT(*) count FROM ($sql) s WHERE at>=? GROUP BY day ORDER BY day",[$month]);
            $out['channels']=Db::all("SELECT channel,COUNT(*) count,COALESCE(SUM(amount_toman),0) amount FROM ($sql) s GROUP BY channel");
            $out['by_plan']=Db::all("SELECT s.plan_id,p.title,COUNT(*) count,COALESCE(SUM(s.amount_toman),0) amount FROM ($sql) s LEFT JOIN {plans} p ON p.id=s.plan_id GROUP BY s.plan_id,p.title");
            $out['purchase_status']=Db::all('SELECT status,COUNT(*) count FROM {purchases} GROUP BY status');$out['order_status']=Db::all('SELECT status,COUNT(*) count FROM {pay_orders} GROUP BY status');
            $out['entitlement_status']=Db::all("SELECT CASE WHEN status='active' AND expires_at IS NOT NULL AND expires_at<=? THEN 'expired' ELSE status END status,COUNT(*) count FROM {entitlements} GROUP BY CASE WHEN status='active' AND expires_at IS NOT NULL AND expires_at<=? THEN 'expired' ELSE status END",[Clock::sql(),Clock::sql()]);
            $days=Db::all("SELECT DATE(DATE_ADD(at,INTERVAL 210 MINUTE)) day,COALESCE(SUM(amount_toman),0) amount FROM ($sql) s WHERE at>=? GROUP BY day ORDER BY day",[Clock::sql($now-365*86400)]);$monthly=[];
            foreach($days as $d){$label=mb_substr(TgEvents::date($d['day'].' 08:00:00'),0,7);$monthly[$label]=($monthly[$label]??0)+(int)$d['amount'];}$out['monthly_sales']=[];foreach($monthly as $label=>$amount)$out['monthly_sales'][]=['month'=>$label,'amount'=>$amount];
        }
        if(TgPolicy::can($m,'support.read'))$out['support']=Db::all('SELECT status,COUNT(*) count FROM {support_requests} GROUP BY status');
        return $out;
    }
    public static function gateways(array $m): array
    {
        TgPolicy::require($m,'sales.read');$rows=[];
        foreach(PayRegistry::adapters() as $id=>$a){$c=PayRegistry::cfg($id);$s=(array)Settings::get('pay_status_'.$id,[]);$rows[]=['id'=>$id,'title'=>$a->title(),'kind'=>$a->kind(),'status'=>PayRegistry::status($id),'enabled'=>(bool)$c['enabled'],'sandbox'=>(bool)$c['sandbox'],'route'=>in_array($id,['bazaar','myket'],true)?PaymentBridge::route($id):$c['route'],'configured'=>$a->configured(),'refund_supported'=>$a->supportsRefund(),'last_ok_at'=>$s['last_ok_at'] ?? null,'last_error_at'=>$s['last_error_at'] ?? null,'last_error'=>isset($s['last_error'])?TgEvents::redact($s['last_error']):null,'last_test'=>Settings::get('tg_health_'.$id),
            'success_count'=>(int)(in_array($id,['bazaar','myket'],true)?Db::value("SELECT COUNT(*) FROM {purchases} WHERE store=? AND status='verified'",[$id]):Db::value("SELECT COUNT(*) FROM {pay_orders} WHERE method=? AND status='paid'",[$id]))];}
        return ['rows'=>$rows,'bridge'=>['configured'=>PaymentBridge::ready(),'last_ok_at'=>Settings::get('bridge_status',[])['last_ok_at']??null,'last_error_at'=>Settings::get('bridge_status',[])['last_error_at']??null]];
    }
    public static function status(array $m): array
    {
        TgPolicy::require($m,'health');$heartbeat=(int)Settings::get('tg_worker_heartbeat',0);$cfg=TgConfig::get();
        return ['version'=>'tg-1.0.0-rc1','schema'=>(int)Db::value('SELECT MAX(version) FROM {migrations}'),'bot_enabled'=>$cfg['bot_enabled'],'app_enabled'=>$cfg['app_enabled'],'paused'=>$cfg['paused'],'worker_last_at'=>$heartbeat?:null,'worker_stale'=>!$heartbeat || Clock::now()-$heartbeat>300,'last_backup_at'=>Settings::get('last_backup_at'),'backup_error'=>Settings::get('last_backup_error','')!==''?'بکاپ ناموفق؛ پنل وب را بررسی کنید.':null,'connection'=>Settings::get('tg_connection'),'queue'=>Db::all('SELECT state,COUNT(*) count FROM {tg_outbox} GROUP BY state'),'recent_errors'=>Db::all("SELECT id,state,attempts,last_error,created_at FROM {tg_outbox} WHERE state IN ('dead','uncertain') ORDER BY id DESC LIMIT 30"),'external_watchdog_required'=>true];
    }
    public static function catalog(array $m): array
    {
        $out=['permissions'=>TgPolicy::permissions($m),'unsupported'=>['push'=>'اعلان‌ها هنگام همگام‌سازی دریافت می‌شوند؛ FCM نصب نیست.','ticket_reply'=>'ارسال پاسخ به اپ در Backend فعلی وجود ندارد؛ یادداشت‌ها داخلی‌اند.','refund'=>'بازپرداخت و تغییر دستی وضعیت پرداخت از تلگرام مجاز نیست.','search_console'=>'OAuth متصل نیست؛ آمار کلیک و نمایش در دسترس نیست.']];
        if(TgPolicy::can($m,'content.read')){$out+=['formats'=>Ads::FORMATS,'placements'=>Ads::placements(),'placement_defaults'=>array_column(Ads::defaultPlacements(),4,0),'zones'=>Ads::ZONES,'targets'=>Ads::APP_TARGETS,'media_limits'=>['image'=>Media::IMAGE_MAX,'video'=>Media::VIDEO_MAX],'site_parts'=>SiteSettings::defaults(),'site_labels'=>['texts'=>SiteSettings::TEXTS,'business'=>SiteSettings::BUSINESS,'contact'=>SiteSettings::CONTACT,'sections'=>SiteSettings::SECTIONS],'notice_audiences'=>Notices::AUDIENCES,'notice_stores'=>Notices::STORES,'ads_enabled'=>(bool)Settings::get('ads_enabled',false),'ads_priority'=>Settings::get('ads_priority','direct_first'),'notices_enabled'=>Notices::enabled(),'notices_daily_cap'=>Notices::dailyCap()];}
        if(TgPolicy::can($m,'sales.read'))$out+=['plans'=>Plans::all(),'promo_kinds'=>Promo::KINDS,'features'=>array_map(fn($f)=>['k'=>$f['k'],'title'=>$f['title']],Features::all())];return $out;
    }
    public static function alerts(array $m): array
    {
        TgPolicy::require($m,'alerts');$out=['preferences'=>array_intersect_key($m,array_flip(['quiet_from','quiet_to','digest'])),'types'=>TgEvents::TYPES];
        if(TgPolicy::can($m,'telegram.manage'))$out+=['rules'=>TgEvents::rules(),'members'=>Db::all('SELECT m.id,m.admin_id,m.telegram_id,m.role,m.permissions,m.active,a.username FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id'),'permissions'=>TgPolicy::PERMISSIONS,'roles'=>TgPolicy::ROLES];return $out;
    }
}
