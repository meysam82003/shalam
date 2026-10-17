<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
/** Shared domain actions. Caller supplies authenticated actor; no browser-supplied SQL/table/role. */
final class Management
{
    private const ACTIONS = [
        'user.enable'=>'users.write','user.disable'=>'users.write','user.unlock'=>'users.write','user.revoke_sessions'=>'users.write','user.release_device'=>'users.write','user.reset_moves'=>'users.write','user.note'=>'users.write',
        'user.gift_pro'=>'pro.write','user.revoke_grant'=>'pro.write',
        'plan.save'=>'plans.write','promo.save'=>'plans.write','promo.toggle'=>'plans.write','campaign.save'=>'plans.write','gateway.toggle'=>'gateways.write','gateway.test'=>'sales.read',
        'ad.save'=>'content.write','ad.delete'=>'content.write','placement.save'=>'content.write','media.upload'=>'content.write','media.delete'=>'content.write','ads.settings'=>'content.write',
        'notice.save'=>'content.write','notice.cancel'=>'content.write','notice.toggle'=>'content.write','notices.settings'=>'content.write','site.save'=>'content.write',
        'seo.scan'=>'content.read','seo.save'=>'content.write','ticket.save'=>'support.write','alerts.preferences'=>'alerts','alerts.rule'=>'telegram.manage','member.revoke'=>'telegram.manage','member.permissions'=>'telegram.manage',
    ];
    public static function permission(string $action): string
    {
        if(!isset(self::ACTIONS[$action])) throw new ApiError('action','عملیات پشتیبانی نمی‌شود.',404);
        return self::ACTIONS[$action];
    }
    public static function reason(array $d): string
    {
        $r=trim((string)($d['reason'] ?? ''));
        if(mb_strlen($r)<3) throw new ApiError('reason','دلیل اقدام را با حداقل ۳ نویسه بنویسید.');
        return mb_substr($r,0,300);
    }
    public static function date($v): ?string
    {
        if($v===null || $v==='') return null;
        if(!is_string($v) || !preg_match('/^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$/D',$v)) throw new ApiError('date','تاریخ معتبر نیست.');
        $d=DateTimeImmutable::createFromFormat(strlen($v)===16?'!Y-m-d H:i':'!Y-m-d H:i:s',str_replace('T',' ',$v),new DateTimeZone('UTC'));
        $errors=DateTimeImmutable::getLastErrors();
        if(!$d || ($errors && ($errors['warning_count'] || $errors['error_count']))) throw new ApiError('date','تاریخ معتبر نیست.');
        return $d->format('Y-m-d H:i:s');
    }
    public static function range(?string $from,?string $to): void { if($from!==null && $to!==null && $to<=$from) throw new ApiError('date','پایان باید بعد از شروع باشد.'); }
    public static function integer($v,int $min,int $max): int
    {
        if(filter_var($v,FILTER_VALIDATE_INT)===false || (int)$v<$min || (int)$v>$max) throw new ApiError('number','عدد خارج از محدوده مجاز است.');
        return (int)$v;
    }
    public static function nullableInt($v,int $max=2147483647): ?int { return $v===null || $v==='' ? null : self::integer($v,1,$max); }
    public static function text(array $d,string $key,int $max,bool $required=false): string
    {
        $v=trim((string)($d[$key] ?? '')); if($required && $v==='') throw new ApiError('text','فیلد '.$key.' لازم است.');
        if(mb_strlen($v)>$max) throw new ApiError('text','متن '.$key.' طولانی است.'); return $v;
    }
    public static function row(string $table,int $id): array
    {
        $r=Db::one("SELECT * FROM {{$table}} WHERE id=?",[$id]); if(!$r) throw new ApiError('not_found','رکورد پیدا نشد.',404); return $r;
    }
    public static function userAction(int $adminId,string $action,array $d): array
    {
        $id=(int)($d['id'] ?? 0); $u=self::row('users',$id); $reason=self::reason($d);
        switch($action) {
            case 'enable': Db::update('users',['status'=>'active','updated_at'=>Clock::sql()],'id=?',[$id]);break;
            case 'disable': Db::update('users',['status'=>'disabled','updated_at'=>Clock::sql()],'id=?',[$id]);
            case 'revoke_sessions': Db::update('sessions',['revoked_at'=>Clock::sql()],'user_id=? AND revoked_at IS NULL',[$id]); break;
            case 'unlock': Db::update('users',['failed_logins'=>0,'locked_until'=>null],'id=?',[$id]);RateLimit::clear('login-user:'.Users::normalize($u['username']));break;
            case 'release_device': Devices::release($id);break;
            case 'reset_moves': Db::update('users',['device_moves'=>0,'device_moves_since'=>null],'id=?',[$id]);break;
            case 'note': Db::update('users',['note'=>trim(($u['note'] ?? '')."\n".Clock::sql().' #'.$adminId.': '.$reason)],'id=?',[$id]);break;
            case 'gift_pro':
                $days=self::nullableInt($d['days'] ?? null,3650);
                // Same user_grants service as the web panel. Does not forge a purchase or rewrite paid periods.
                self::grant($adminId,$id,'pro',$days,$reason);break;
            case 'revoke_grant':
                $g=Db::one('SELECT id FROM {user_grants} WHERE id=? AND user_id=?',[(int)($d['grant'] ?? 0),$id]);
                if(!$g) throw new ApiError('not_found','هدیه پیدا نشد.',404);
                Db::update('user_grants',['revoked_at'=>Clock::sql()],'id=?',[$g['id']]);break;
            default:throw new ApiError('action','عملیات حساب پشتیبانی نمی‌شود.');
        }
        Support::action(null,$id,$adminId,$action,$reason);
        return ['id'=>$id];
    }
    public static function grant(int $adminId,int $userId,string $feature,?int $days,string $reason): int
    {
        self::row('users',$userId);
        if($feature!=='pro' && Db::value('SELECT k FROM {features} WHERE k=?',[$feature])===null) throw new ApiError('feature','قابلیت نامعتبر است.');
        return Db::tx(function()use($adminId,$userId,$feature,$days,$reason){
            $id=Db::insert('user_grants',['user_id'=>$userId,'feature_k'=>$feature,'expires_at'=>$days===null?null:Clock::sql(Clock::now()+$days*86400),'reason'=>mb_substr($reason,0,300),'admin_id'=>$adminId,'created_at'=>Clock::sql()]);
            if($feature==='pro') TgEvents::emit('pro_activated','user',$userId,'grant:'.$id);
            return $id;
        });
    }
    public static function ad(array $d): array
    {
        $format=(string)($d['format'] ?? '');if(!isset(Ads::FORMATS[$format])) throw new ApiError('format','قالب نامعتبر است.');
        $mediaId=self::nullableInt($d['media_id'] ?? null);$media=$mediaId?self::row('media',$mediaId):null;
        if(in_array($format,['image','video','corner','rewarded'],true) && !$media) throw new ApiError('media','این قالب رسانه لازم دارد.');
        if(in_array($format,['video','rewarded'],true) && ($media['kind'] ?? '')!=='video') throw new ApiError('media','این قالب فقط MP4 می‌پذیرد.');
        if($media && $media['kind']==='video' && !in_array($format,['video','rewarded','interstitial'],true)) throw new ApiError('media','ویدئو با این قالب سازگار نیست.');
        $url=self::text($d,'target_url',500);if(!safe_target_url($url)) throw new ApiError('url','نشانی مقصد مجاز نیست.');
        $valid=array_column(Ads::placements(),'formats','k');$places=array_values(array_unique((array)($d['placements'] ?? [])));
        if(!$places) throw new ApiError('placements','جایگاه را انتخاب کنید.');
        foreach($places as $p) {
            if(!is_string($p) || !isset($valid[$p]) || !in_array($format,explode(',',$valid[$p]),true)) throw new ApiError('placements','جایگاه با قالب سازگار نیست.');
            if((str_starts_with($p,'site_') || str_starts_with($p,'pay_')) && $media && $media['kind']!=='image') throw new ApiError('media','سایت و صفحات پرداخت فقط تصویر ثابت را نمایش می‌دهند.');
        }
        $stores=array_values(array_unique(array_intersect((array)($d['stores'] ?? []),Plans::STORES)));
        if(!$stores) throw new ApiError('stores','بازار یا مایکت را انتخاب کنید.');
        $style=[];foreach(['bg','fg','accent'] as $k) { $v=(string)($d['style_'.$k] ?? ''); if($v!=='') {if(!preg_match('/^#[a-fA-F0-9]{6}$/D',$v)) throw new ApiError('color','رنگ معتبر نیست.');$style[$k]=$v;} }
        $start=self::date($d['starts_at'] ?? null);$end=self::date($d['ends_at'] ?? null);self::range($start,$end);
        return ['name'=>self::text($d,'name',80,true),'title'=>self::text($d,'title',80),'body'=>self::text($d,'body',300),'format'=>$format,'media_id'=>$mediaId,'target_url'=>$url?:null,'cta_text'=>self::text($d,'cta_text',30),'style_json'=>$style?json_encode($style):null,
            'closable'=>!empty($d['closable']) || $format==='interstitial'?1:0,'starts_at'=>$start,'ends_at'=>$end,'total_cap'=>self::nullableInt($d['total_cap'] ?? null),'daily_cap'=>self::nullableInt($d['daily_cap'] ?? null),'min_interval_sec'=>self::integer($d['min_interval_sec'] ?? 0,0,86400),'priority'=>self::integer($d['priority'] ?? 0,-10000,10000),'placements'=>implode(',',$places),'stores'=>implode(',',$stores),'active'=>!empty($d['active'])?1:0,'updated_at'=>Clock::sql()];
    }
    public static function saveAd(array $d): int
    {
        $id=(int)($d['id'] ?? 0);$row=self::ad($d);
        if($id) {self::row('ads',$id);Db::update('ads',$row,'id=?',[$id]);}else $id=Db::insert('ads',$row+['created_at'=>Clock::sql()]);return $id;
    }
    public static function planRow(array $d): array
    {
        $id=(int)($d['id'] ?? 0);$p=self::row('plans',$id);
        $row=['title'=>self::text($d,'title',80,true),'description'=>self::text($d,'description',400),'active'=>!empty($d['active'])?1:0,'sort'=>self::integer($d['sort'] ?? $p['sort'],0,10000),'badge_text'=>self::text($d,'badge_text',40),'badge_color'=>self::text($d,'badge_color',16)?:null,'renewal_note'=>self::text($d,'renewal_note',300),'purchase_limit'=>self::nullableInt($d['purchase_limit'] ?? null),'updated_at'=>Clock::sql()];
        if($row['badge_color'] && !preg_match('/^#[a-fA-F0-9]{6}$/D',$row['badge_color'])) throw new ApiError('color','رنگ معتبر نیست.');
        foreach(Plans::STORES as $s) {
            $price=self::integer($d['store_price_'.$s] ?? $p['store_price_'.$s],1000,200000000);
            $list=self::nullableInt($d['list_price_'.$s] ?? null,200000000);
            $row['store_price_'.$s]=$price;$row['list_price_'.$s]=$list!==null && $list>$price?$list:null;$row['on_'.$s]=!empty($d['on_'.$s])?1:0;
        }
        // Preserve the existing panel's direct price rule.
        $row['base_price_toman']=$row['store_price_bazaar'];
        return $row;
    }
    public static function savePlan(array $d): int
    {
        $row=self::planRow($d);$id=(int)$d['id'];Db::update('plans',$row,'id=?',[$id]);return $id;
    }
    public static function savePromo(int $adminId,array $d): int
    {
        $kind=(string)($d['kind'] ?? '');if(!isset(Promo::KINDS[$kind])) throw new ApiError('kind','نوع کد معتبر نیست.');
        $code=Promo::normalize((string)($d['code'] ?? '')) ?: Promo::generate();
        if(strlen($code)<4 || strlen($code)>32) throw new ApiError('code','کد باید بین ۴ و ۳۲ نویسه باشد.');
        $plan=(string)($d['plan_code'] ?? '');if($plan!=='' && !Plans::byCode($plan)) throw new ApiError('plan','پلن پیدا نشد.');
        $feature=(string)($d['feature_k'] ?? '');if($kind==='gift_feature' && Db::value('SELECT k FROM {features} WHERE k=?',[$feature])===null) throw new ApiError('feature','قابلیت پیدا نشد.');
        $users=array_values(array_unique(array_map('intval',(array)($d['users'] ?? []))));foreach($users as $uid) self::row('users',$uid);
        $start=self::date($d['starts_at'] ?? null);$end=self::date($d['ends_at'] ?? null);self::range($start,$end);
        if(Db::value('SELECT id FROM {promo_codes} WHERE code=?',[$code])!==null) throw new ApiError('taken','این کد قبلاً ثبت شده است.',409);
        return Db::insert('promo_codes',['code'=>$code,'kind'=>$kind,'days'=>$kind==='discount'?null:self::nullableInt($d['days'] ?? null,3650),'feature_k'=>$kind==='gift_feature'?$feature:null,'plan_code'=>$kind==='discount' && $plan!==''?$plan:null,'percent'=>$kind==='discount'?self::integer($d['percent'] ?? 0,1,90):null,'max_uses'=>self::nullableInt($d['max_uses'] ?? null),'users'=>$users?implode(',',$users):null,'starts_at'=>$start,'ends_at'=>$end,'active'=>!empty($d['active'])?1:0,'note'=>self::reason($d),'admin_id'=>$adminId,'created_at'=>Clock::sql()]);
    }
    public static function perform(int $adminId,string $action,array $d,?array $member=null): array
    {
        self::permission($action); $reason=self::reason($d);$id=(int)($d['id'] ?? 0);$result=[];
        if(str_starts_with($action,'user.')) $result=self::userAction($adminId,substr($action,5),$d);
        else switch($action) {
            case 'plan.save': $result=['id'=>self::savePlan($d)];break;
            case 'promo.save': $id=self::savePromo($adminId,$d);$result=['id'=>$id,'code'=>Db::value('SELECT code FROM {promo_codes} WHERE id=?',[$id])];break;
            case 'promo.toggle': self::row('promo_codes',$id);Db::update('promo_codes',['active'=>!empty($d['active'])?1:0],'id=?',[$id]);break;
            case 'campaign.save':
                $pid=self::integer($d['plan_id'] ?? 0,1,2147483647);self::row('plans',$pid);$store=(string)($d['store'] ?? 'all');if(!in_array($store,['all','bazaar','myket'],true)) throw new ApiError('store','فروشگاه نامعتبر است.');
                $start=self::date($d['starts_at'] ?? null);$end=self::date($d['ends_at'] ?? null);self::range($start,$end);$color=self::text($d,'label_color',16);if($color && !preg_match('/^#[a-fA-F0-9]{6}$/D',$color)) throw new ApiError('color','رنگ معتبر نیست.');
                $r=['name'=>self::text($d,'name',80,true),'plan_id'=>$pid,'store'=>$store,'price_toman'=>self::integer($d['price_toman'] ?? 0,1000,200000000),'label'=>self::text($d,'label',40),'label_color'=>$color?:null,'starts_at'=>$start,'ends_at'=>$end,'active'=>!empty($d['active'])?1:0,'updated_at'=>Clock::sql()];
                if($id){self::row('campaigns',$id);Db::update('campaigns',$r,'id=?',[$id]);}else $id=Db::insert('campaigns',$r+['created_at'=>Clock::sql()]);$result=['id'=>$id];break;
            case 'gateway.toggle':
                $key=(string)($d['method'] ?? '');if(!PayRegistry::get($key)) throw new ApiError('gateway','درگاه موجود نیست.');
                $c=PayRegistry::cfg($key);$c['enabled']=!empty($d['enabled']);Settings::set('pay_'.$key,$c);break;
            case 'gateway.test':
                $key=(string)($d['method'] ?? '');$a=PayRegistry::get($key);if(!$a) throw new ApiError('gateway','درگاه موجود نیست.');
                TgLimit::require('gateway-test:'.$key,3,300);[$state,$msg]=$a->test();Settings::set('tg_health_'.$key,['state'=>$state,'at'=>Clock::sql()]);$result=['state'=>$state,'message'=>TgEvents::redact($msg)];break;
            case 'ad.save': $result=['id'=>self::saveAd($d)];break;
            case 'ad.delete': self::row('ads',$id);Db::run('DELETE FROM {ads} WHERE id=?',[$id]);break;
            case 'media.upload': $result=['id'=>Media::store($_FILES['file'] ?? [])];break;
            case 'media.delete':
                if(Db::value('SELECT id FROM {notices} WHERE media_id=? LIMIT 1',[$id])!==null) throw new ApiError('in_use','این رسانه در اعلان استفاده می‌شود.');Media::delete($id);break;
            case 'placement.save':
                $key=(string)($d['key'] ?? '');$p=Db::one('SELECT * FROM {placements} WHERE k=?',[$key]);if(!$p) throw new ApiError('placement','جایگاه موجود نیست.');
                $defaults=[];foreach(Ads::defaultPlacements() as $v)$defaults[$v[0]]=explode(',',$v[4]);$allowed=$defaults[$key] ?? explode(',',$p['formats']);$formats=array_values(array_intersect((array)($d['formats'] ?? []),$allowed));if(!$formats) throw new ApiError('format','یک قالب مجاز انتخاب کنید.');
                $r=['active'=>!empty($d['active'])?1:0,'formats'=>implode(',',$formats)];if($p['position']==='sticky')$r['screens']=implode(',',array_intersect((array)($d['screens'] ?? []),array_keys(Ads::ZONES)));Db::update('placements',$r,'k=?',[$key]);break;
            case 'ads.settings':
                $priority=(string)($d['priority'] ?? 'direct_first');if(!in_array($priority,['direct_first','network_first','direct_only','network_only'],true)) throw new ApiError('priority','ترتیب منبع نامعتبر است.');
                Settings::set('ads_enabled',!empty($d['enabled']));Settings::set('ads_priority',$priority);break;
            case 'notices.settings': Settings::set('notices_enabled',!empty($d['enabled']));Settings::set('notices_daily_cap',self::integer($d['daily_cap'] ?? 1,1,5));break;
            case 'notice.cancel':case 'notice.toggle':
                self::row('notices',$id);$r=$action==='notice.cancel'?['canceled_at'=>Clock::sql(),'active'=>0]:['active'=>!empty($d['active'])?1:0];Db::update('notices',$r+['updated_at'=>Clock::sql()],'id=?',[$id]);break;
            case 'notice.save':
                $target=self::text($d,'target',300);if(!Notices::validTarget($target))throw new ApiError('target','مقصد اعلان نامعتبر است.');
                $stores=array_values(array_intersect((array)($d['stores'] ?? []),array_keys(Notices::STORES)));if(!$stores)throw new ApiError('stores','نسخه را انتخاب کنید.');$aud=(string)($d['audience'] ?? 'free');if(!isset(Notices::AUDIENCES[$aud]))throw new ApiError('audience','مخاطب نامعتبر است.');
                $mid=self::nullableInt($d['media_id'] ?? null);if($mid && self::row('media',$mid)['kind']!=='image')throw new ApiError('media','اعلان فقط تصویر ثابت می‌پذیرد.');
                $start=self::date($d['send_at'] ?? null);$end=self::date($d['ends_at'] ?? null);self::range($start,$end);
                $id=Db::insert('notices',['title'=>self::text($d,'title',80,true),'body'=>self::text($d,'body',300,true),'target'=>$target?:null,'media_id'=>$mid,'audience'=>$aud,'stores'=>implode(',',$stores),'send_at'=>$start,'ends_at'=>$end,'active'=>!empty($d['active'])?1:0,'admin_id'=>$adminId,'created_at'=>Clock::sql(),'updated_at'=>Clock::sql()]);$result=['id'=>$id];break;
            case 'seo.scan': $result=TgSeo::scan();break;
            case 'seo.save': TgSeo::save($d);break;
            case 'site.save': SiteSettings::save((string)($d['part'] ?? ''),(array)($d['values'] ?? []));break;
            case 'ticket.save':
                $t=self::row('support_requests',$id);$status=(string)($d['status'] ?? 'in_review');$priority=(string)($d['priority'] ?? 'normal');if(!in_array($status,['open','in_review','resolved'],true)||!in_array($priority,['low','normal','high','urgent'],true))throw new ApiError('status','وضعیت یا اولویت نامعتبر است.');
                Db::update('support_requests',['status'=>$status,'updated_at'=>Clock::sql()],'id=?',[$id]);Db::run('INSERT INTO {tg_ticket_meta} (request_id,priority,read_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE priority=VALUES(priority),read_at=VALUES(read_at)',[$id,$priority,Clock::sql()]);Support::action($id,$t['user_id']===null?null:(int)$t['user_id'],$adminId,'internal_note',$reason);$result=['delivery'=>'internal_only'];break;
            case 'alerts.preferences':
                if(!$member)throw new ApiError('member','عضویت لازم است.');$digest=(string)($d['digest'] ?? 'off');if(!in_array($digest,['off','daily','weekly'],true))throw new ApiError('digest','بازه خلاصه نامعتبر است.');
                $q1=($d['quiet_from'] ?? '')===''?null:self::integer($d['quiet_from'],0,23);$q2=($d['quiet_to'] ?? '')===''?null:self::integer($d['quiet_to'],0,23);if(($q1===null)!==($q2===null))throw new ApiError('quiet','آغاز و پایان سکوت را باهم تنظیم کنید.');
                Db::update('tg_members',['quiet_from'=>$q1,'quiet_to'=>$q2,'digest'=>$digest],'id=?',[$member['id']]);break;
            case 'alerts.rule': TgEvents::saveRule($d);break;
            case 'member.revoke':
                if($member && $id===(int)$member['id'])throw new ApiError('self','دسترسی خود را از پنل وب لغو کنید.');self::row('tg_members',$id);Db::run('UPDATE {tg_members} SET active=0,epoch=epoch+1 WHERE id=?',[$id]);break;
            case 'member.permissions':
                $m=self::row('tg_members',$id);$role=(string)($d['role'] ?? '');if(!isset(TgPolicy::ROLES[$role]) || $role==='owner')throw new ApiError('role','نقش نامعتبر است؛ مالک از وب مدیریت می‌شود.');if($m['role']==='owner')throw new ApiError('role','تغییر مالک فقط از وب.');
                $perms=array_values(array_intersect((array)($d['permissions'] ?? []),TgPolicy::PERMISSIONS));Db::run('UPDATE {tg_members} SET role=?,permissions=?,epoch=epoch+1 WHERE id=?',[$role,json_encode($perms),$id]);break;
        }
        // Never log raw request data, credentials, uploads or secret settings.
        Audit::log('admin',$adminId,'tg_'.str_replace('.','_',$action),explode('.',$action)[0],$result['id'] ?? ($id?:null),$reason,null,['channel'=>'telegram','action'=>$action]);
        return $result+['ok'=>true];
    }
}
