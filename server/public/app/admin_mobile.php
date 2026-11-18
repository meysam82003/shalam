<?php
/** Native administration API; independent bearer sessions, same accounts, TOTP and roles as the web panel. */
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
require_once __DIR__ . '/admin/core.php';
final class AdminMobile
{
    public static function login(array $in): array
    {
        if (!RateLimit::hit('mobile-admin-login:'.Http::ip(),20,900)) throw new ApiError('rate_limited','تلاش زیاد؛ کمی بعد دوباره وارد شوید.',429);
        $name=trim((string)($in['username']??''));$pass=(string)($in['password']??'');
        return Db::tx(function() use($name,$pass) {
            $a=Db::one('SELECT * FROM {admins} WHERE username = ? FOR UPDATE',[$name]);
            if ($a!==null && $a['locked_until']!==null && Clock::ts($a['locked_until'])>Clock::now()) throw new ApiError('locked','حساب مدیر موقتاً قفل است.',429);
            if ($a===null || !(int)$a['active'] || !password_verify($pass,$a['password_hash'])) {
                if ($a!==null) Db::update('admins',['failed'=>(int)$a['failed']+1,'locked_until'=>(int)$a['failed']>=5?Clock::sql(Clock::now()+900):null],'id = ?',[$a['id']]);
                Audit::security('mobile_admin_login_failed',null,$name);
                return ['invalid'=>true];
            }
            $token=bin2hex(random_bytes(32));
            Db::insert('admin_mobile_challenges',['token_hash'=>Crypto::hash($token),'admin_id'=>$a['id'],'expires_at'=>Clock::now()+300,'attempts'=>0]);
            return ['challenge'=>$token,'expires_at'=>Clock::now()+300];
        });
    }
    public static function verify(array $in): array
    {
        if (!RateLimit::hit('mobile-admin-totp:'.Http::ip(),30,900)) throw new ApiError('rate_limited','تلاش زیاد است.',429);
        $result=Db::tx(function() use($in) {
            $c=Db::one('SELECT * FROM {admin_mobile_challenges} WHERE token_hash = ? FOR UPDATE',[Crypto::hash((string)($in['challenge']??''))]);
            if ($c===null || (int)$c['expires_at']<Clock::now() || (int)$c['attempts']>=5) return ['invalid'=>true];
            Db::run('UPDATE {admin_mobile_challenges} SET attempts = attempts + 1 WHERE token_hash = ?',[$c['token_hash']]);
            $a=Db::one('SELECT * FROM {admins} WHERE id = ? AND active = 1 FOR UPDATE',[$c['admin_id']]);
            if ($a===null) return ['invalid'=>true];
            $secret=Crypto::decrypt($a['totp_enc']);
            $step=$secret===null?null:Totp::verify($secret,(string)($in['code']??''),(int)$a['totp_last_step']);
            if ($step===null) return ['invalid'=>true];
            Db::run('DELETE FROM {admin_mobile_challenges} WHERE token_hash = ?',[$c['token_hash']]);
            Db::update('admins',['failed'=>0,'locked_until'=>null,'last_login_at'=>Clock::sql(),'totp_last_step'=>$step],'id = ?',[$a['id']]);
            $token=bin2hex(random_bytes(32));
            Db::insert('admin_mobile_sessions',['token_hash'=>Crypto::hash($token),'admin_id'=>$a['id'],'password_stamp'=>Crypto::hash($a['password_hash']),'created_at'=>Clock::now(),'seen_at'=>Clock::now(),'expires_at'=>Clock::now()+2592000,'revoked_at'=>null]);
            Audit::log('admin',(int)$a['id'],'mobile_login','admin',$a['id']);
            return ['token'=>$token,'expires_at'=>Clock::now()+2592000,'username'=>$a['username'],'role'=>$a['role']];
        });
        if (isset($result['invalid'])) throw new ApiError('totp','کد دومرحله‌ای نامعتبر یا منقضی است.',401);
        return $result;
    }
    public static function auth(string $page, bool $write=false): array
    {
        $header=Http::header('Authorization');$token=strpos($header,'Bearer ')===0?substr($header,7):'';
        $s=Db::one('SELECT * FROM {admin_mobile_sessions} WHERE token_hash = ? AND revoked_at IS NULL',[Crypto::hash($token)]);
        if ($s===null || Clock::now()>(int)$s['expires_at'] || Clock::now()-(int)$s['seen_at']>2592000) throw new ApiError('admin_unauthorized','دوباره وارد برنامهٔ مدیریت شوید.',401);
        $a=Db::one('SELECT * FROM {admins} WHERE id = ? AND active = 1',[$s['admin_id']]);
        if($a!==null && !hash_equals($s['password_stamp'],Crypto::hash($a['password_hash'])))throw new ApiError('admin_unauthorized','رمز مدیر تغییر کرده؛ دوباره وارد شوید.',401);
        if($write && Clock::now()-(int)$s['created_at']>43200)throw new ApiError('admin_unauthorized','برای تغییر تنظیمات دوباره ورود دومرحله‌ای انجام دهید.',401);
        Admin::$me=$a;
        if ($a===null || !Admin::can($page) || ($write && (($page!=='account' && !Admin::writable()) || (in_array($page,['adnetwork','ads','notices','features','plans','site'],true) && !in_array($a['role'],['owner','manager'],true))))) throw new ApiError('admin_forbidden','دسترسی مجاز نیست.',403);
        if (!RateLimit::hit('mobile-admin:'.$a['id'],300,3600)) throw new ApiError('rate_limited','درخواست زیاد است.',429);
        Db::update('admin_mobile_sessions',['seen_at'=>Clock::now()],'token_hash = ?',[$s['token_hash']]);
        return $a;
    }
    private static function field(string $key,string $label,$value,string $type='text',array $options=[]): array { return compact('key','label','value','type','options'); }
    public static function adsFields(): array
    {
        $c=Monetization::settings();$f=[];
        foreach (['enabled'=>'تمام تبلیغات شبکه‌ای','vip_ads'=>'نمایش برای کاربران Pro'] as $k=>$l) $f[]=self::field($k,$l,$c[$k],'bool');
        $f[]=self::field('primary','شبکهٔ اصلی',$c['primary'],'select',Monetization::NETWORKS);
        $f[]=self::field('fallback','شبکهٔ جایگزین',$c['fallback'],'select',['none','tapsell','adivery']);
        foreach (['banner'=>'بنری','interstitial'=>'تمام‌صفحه','rewarded'=>'جایزه‌دار'] as $k=>$l) $f[]=self::field("$k.enabled","تبلیغات $l",$c[$k]['enabled'],'bool');
        foreach (['hands'=>'تعداد دست بین نمایش‌ها','min_seconds'=>'حداقل فاصلهٔ نمایش (ثانیه)','daily_cap'=>'سقف روزانهٔ تمام‌صفحه'] as $k=>$l) $f[]=self::field('interstitial.'.$k,$l,$c['interstitial'][$k],'number');
        foreach (['amount'=>'سکهٔ هر پاداش','daily_cap'=>'سقف پاداش روزانه','min_seconds'=>'حداقل زمان معتبر ویدئو (ثانیه)'] as $k=>$l) $f[]=self::field('rewarded.'.$k,$l,$c['rewarded'][$k],'number');
        $f[]=self::field('banner.pages','صفحات بنر (با کامای انگلیسی)',$c['banner']['pages'],'list');
        foreach ($c['banner']['placements'] as $k=>$on) $f[]=self::field('banner.placements.'.$k,'جایگاه '.$k,$on,'bool');
        foreach (Monetization::NETWORKS as $n) {
            $v=$c['networks'][$n];foreach (['enabled'=>'فعال','test'=>'آزمایشی'] as $k=>$l) $f[]=self::field("networks.$n.$k","$n — $l",$v[$k],'bool');
            $f[]=self::field("networks.$n.app_id","$n — شناسهٔ اپ",$v['app_id']);
            foreach (Monetization::FORMATS as $type) $f[]=self::field("networks.$n.zones.$type","$n — $type",$v['zones'][$type]);
        }
        return $f;
    }
    private static function panelInput(array $in): array {
        $payload=isset($in['native_payload'])?json_decode((string)$in['native_payload'],true):$in;
        if(!is_array($payload))throw new ApiError('panel','درخواست فرم نامعتبر است.');
        return NativePanel::handle($payload);
    }
    public static function handle(string $route,string $method,array $in): array
    {
        if ($route==='admin/mobile/login' && $method==='POST') { $r=self::login($in);if(isset($r['invalid'])) throw new ApiError('credentials','نام کاربری یا رمز درست نیست.',401);return $r; }
        if ($route==='admin/mobile/verify' && $method==='POST') return self::verify($in);
        if ($route==='admin/mobile/logout' && $method==='POST') { self::auth('account');$h=Crypto::hash(substr(Http::header('Authorization'),7));Db::update('admin_mobile_sessions',['revoked_at'=>Clock::now()],'token_hash = ?',[$h]);return ['logged_out'=>true]; }
        if ($route==='admin/ads/settings') {
            $a=self::auth('adnetwork',$method==='PUT');
            if ($method==='GET') return ['settings'=>Monetization::settings(),'fields'=>self::adsFields(),'message'=>'درآمد واقعی: ناموجود. تپسل کلید راه‌اندازی ثابت دارد؛ تغییر آن به ساخت APK جدید نیاز دارد. شناسهٔ جایگاه‌ها و تنظیمات دیگر پویا هستند.'];
            if ($method!=='PUT') throw new ApiError('method','روش نامعتبر است.',405);
            $before=Monetization::settings();$c=Monetization::save($in);Audit::log('admin',(int)$a['id'],'iran_ads_settings','settings','iran_ads','برنامهٔ مدیریت',$before,$c);return ['settings'=>$c,'fields'=>self::adsFields()];
        }
        if ($route==='admin/ads/stats') { self::auth('ads');if($method!=='GET')throw new ApiError('method','روش نامعتبر است.',405);return Monetization::stats(); }
        if ($route==='admin/mobile/push/register') { $a=self::auth('users');if(!Admin::can('transactions'))throw new ApiError('admin_forbidden','دسترسی مجاز نیست.',403);if($method!=='POST')throw new ApiError('method','روش نامعتبر است.',405);return PushDelivery::register($in,null,null,$a); }
        if ($route==='admin/mobile/menu' && $method==='GET') return NativePanel::menu();
        if ($route==='admin/mobile/panel' && $method==='POST') return self::panelInput($in);
        if ($route==='admin/mobile/inbox') { $a=self::auth('dashboard');if($method==='GET')return AdminInbox::list($a,(int)($in['after']??0));if($method==='POST')return AdminInbox::read($a,(int)($in['read_through']??0));throw new ApiError('method','روش نامعتبر است.',405); }
        if ($route==='admin/mobile/download' && $method==='GET') {
            self::auth('backups');if(!Admin::owner())throw new ApiError('admin_forbidden','فقط مدیر اصلی می‌تواند پشتیبان دریافت کند.',403);
            $b=Db::one('SELECT * FROM {backups} WHERE id=?',[(int)($in['id']??0)]);$file=$b?Backup::dir().'/'.basename($b['file']):'';
            if($b===null||!is_file($file))throw new ApiError('missing','پشتیبان پیدا نشد.',404);
            header('Content-Type: application/octet-stream');header('Content-Disposition: attachment; filename="'.basename($b['file']).'"');header('Content-Length: '.filesize($file));readfile($file);exit;
        }
        $kind=substr($route,strlen('admin/mobile/'));
        $pages=['dashboard'=>'dashboard','users'=>'users','notices'=>'notices','gifts'=>'gifts','purchases'=>'transactions','subscriptions'=>'subscriptions','plans'=>'plans','features'=>'features','contacts'=>'site','media'=>'media','audit'=>'audit','direct_ads'=>'ads'];
        if (!isset($pages[$kind])) throw new ApiError('not_found','بخش پیدا نشد.',404);
        $a=self::auth($pages[$kind],$method!=='GET');
        if ($method!=='GET' && $method!=='POST') throw new ApiError('method','روش نامعتبر است.',405);
        if ($kind==='dashboard') return ['username'=>$a['username'],'role'=>$a['role'],'items'=>[
            ['title'=>'کاربران','body'=>(string)Db::value('SELECT COUNT(*) FROM {users}')],['title'=>'اشتراک‌های فعال','body'=>(string)Db::value("SELECT COUNT(*) FROM {entitlements} WHERE status = 'active' AND (expires_at IS NULL OR expires_at > ?)",[Clock::sql()])],['title'=>'پاداش‌های پرداخت‌شده','body'=>(string)Db::value('SELECT COUNT(*) FROM {coin_transactions}')],['title'=>'درآمد تبلیغات','body'=>'ناموجود']]];
        if ($kind==='contacts') {
            if ($method==='POST') { foreach (['support_text','contact_address','sponsor_contact'] as $k) if(isset($in[$k])) Settings::set($k,mb_substr((string)$in[$k],0,1000));Admin::audit('mobile_contacts','settings',null); }
            $fields=[];foreach (['support_text'=>'متن پشتیبانی','contact_address'=>'نشانی تماس','sponsor_contact'=>'تماس ثبت تبلیغ و اسپانسری'] as $k=>$l) $fields[]=self::field($k,$l,Settings::get($k,''));return ['fields'=>$fields,'items'=>[]];
        }
        if ($kind==='notices') {
            if ($method==='POST') {
                if (isset($in['toggle_id'])) { $id=(int)$in['toggle_id'];Db::run('UPDATE {notices} SET active = 1 - active, updated_at = ? WHERE id = ?',[Clock::sql(),$id]);Admin::audit('mobile_notice_toggle','notice',$id); }
                else {
                    $title=trim((string)($in['title']??''));$body=trim((string)($in['body']??''));$target=(string)($in['target']??'');$audience=(string)($in['audience']??'free');
                    if ($title===''||mb_strlen($title)>80||$body===''||mb_strlen($body)>300||!Notices::validTarget($target)||!isset(Notices::AUDIENCES[$audience])) throw new ApiError('notice','عنوان، متن یا مقصد اعلان معتبر نیست.');
                    $stores=(string)($in['stores']??'bazaar,myket,direct');if(array_diff(explode(',',$stores),array_keys(Notices::STORES)))throw new ApiError('notice','نسخهٔ مقصد نامعتبر است.');
                    $row=['title'=>$title,'body'=>$body,'target'=>$target,'audience'=>$audience,'stores'=>$stores,'active'=>1,'admin_id'=>$a['id'],'created_at'=>Clock::sql(),'updated_at'=>Clock::sql()];
                    $id=Db::insert('notices',$row);Admin::audit('mobile_notice_create','notice',$id);
                }
            }
            return ['items'=>array_map(fn($n)=>['id'=>(int)$n['id'],'title'=>$n['title'],'body'=>$n['body'].' — نمایش '.$n['shown'].' / کلیک '.$n['opened'],'active'=>(bool)$n['active']],Db::all('SELECT id,title,body,shown,opened,active FROM {notices} ORDER BY id DESC LIMIT 100')),
                'fields'=>[self::field('title','عنوان',''),self::field('body','متن',''),self::field('target','مقصد','divan://pro'),self::field('audience','مخاطبان','free','select',['free','all']),self::field('stores','نسخه‌ها','bazaar,myket,direct')],
                'message'=>'اعلان هنگام باز بودن اپ و بررسی پس‌زمینه به گوشی می‌رسد؛ ارسال لحظه‌ای نیست و به مجوز کاربر و محدودیت Android وابسته است.'];
        }
        if ($kind==='gifts') {
            if ($method==='POST') {
                if (isset($in['revoke_id'])) { $id=(int)$in['revoke_id'];Db::update('user_grants',['revoked_at'=>Clock::sql()],'id = ?',[$id]);Admin::audit('mobile_gift_revoke','grant',$id); }
                else {
                    $u=Users::byName((string)($in['username']??''));$feature=(string)($in['feature']??'pro');$days=filter_var($in['days']??30,FILTER_VALIDATE_INT);
                    if($u===null||($feature!=='pro'&&Db::value('SELECT k FROM {features} WHERE k = ?',[$feature])===null)||$days===false||$days<0||$days>3650)throw new ApiError('gift','کاربر، قابلیت یا مدت هدیه معتبر نیست.');
                    $id=Db::insert('user_grants',['user_id'=>$u['id'],'feature_k'=>$feature,'expires_at'=>$days===0?null:Clock::sql(Clock::now()+$days*86400),'reason'=>mb_substr((string)($in['reason']??'هدیه از مدیریت'),0,300),'admin_id'=>$a['id'],'created_at'=>Clock::sql()]);Admin::audit('mobile_gift','grant',$id);
                }
            }
            return ['items'=>array_map(fn($g)=>['id'=>(int)$g['id'],'title'=>$g['username'].' — '.$g['feature_k'],'body'=>($g['expires_at']??'دائمی').' / '.$g['reason'],'active'=>$g['revoked_at']===null],Db::all('SELECT g.*,u.username FROM {user_grants} g JOIN {users} u ON u.id=g.user_id ORDER BY g.id DESC LIMIT 100')),
                'fields'=>[self::field('username','نام کاربری',''),self::field('feature','قابلیت','pro','select',array_merge(['pro'],array_column(Db::all('SELECT k FROM {features} ORDER BY k'),'k'))),self::field('days','مدت به روز؛ صفر یعنی دائمی',30,'number'),self::field('reason','توضیح','هدیه از مدیریت')]];
        }
        if ($method!=='GET') throw new ApiError('method','این بخش فقط گزارش است.',405);
        $offset=max(0,min(100000,(int)($in['offset']??0)));$q=mb_substr((string)($in['q']??''),0,80);
        if ($kind==='users') return ['items'=>array_map(fn($u)=>['id'=>(int)$u['id'],'title'=>$u['username'],'body'=>$u['status'].' / '.$u['created_at']],Db::all('SELECT id,username,status,created_at FROM {users} WHERE username LIKE ? ORDER BY id DESC LIMIT 100 OFFSET '.$offset,['%'.$q.'%']))];
        if ($kind==='purchases') return ['items'=>array_map(fn($v)=>['id'=>(int)$v['id'],'title'=>$v['username'].' / '.$v['sku'],'body'=>$v['status'].' — '.$v['store'].' / '.($v['amount_toman']??'ناموجود')],Db::all('SELECT p.id,p.sku,p.status,p.store,p.amount_toman,u.username FROM {purchases} p JOIN {users} u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 100 OFFSET '.$offset))];
        if ($kind==='subscriptions') return ['items'=>array_map(fn($v)=>['title'=>$v['username'].' / '.$v['plan_code'],'body'=>$v['status'].' — '.($v['expires_at']??'دائمی')],Db::all('SELECT e.*,u.username FROM {entitlements} e JOIN {users} u ON u.id=e.user_id ORDER BY e.id DESC LIMIT 100 OFFSET '.$offset))];
        if ($kind==='plans') return ['items'=>array_map(fn($v)=>['title'=>$v['title'],'body'=>$v['code'].' — '.($v['duration_days']??'').' روز'],Plans::all())];
        if ($kind==='features') return ['items'=>array_map(fn($v)=>['title'=>$v['title'],'body'=>$v['k'].' — '.$v['mode']],Db::all('SELECT k,title,mode FROM {features} ORDER BY sort,k'))];
        if ($kind==='direct_ads') return ['items'=>array_map(fn($v)=>['title'=>$v['name'],'body'=>$v['format'].' — '.($v['active']?'فعال':'خاموش')],Db::all('SELECT name,format,active FROM {ads} ORDER BY id DESC LIMIT 100'))];
        if ($kind==='media') return ['items'=>array_map(fn($v)=>['title'=>$v['original_name']??$v['path'],'body'=>$v['kind']],Db::all('SELECT * FROM {media} ORDER BY id DESC LIMIT 100'))];
        if ($kind==='audit') return ['items'=>array_map(fn($v)=>['title'=>$v['action'],'body'=>$v['created_at']],Db::all('SELECT action,created_at FROM {audit_log} ORDER BY id DESC LIMIT 100'))];
        throw new ApiError('not_found','مسیر پیدا نشد.',404);
    }
}
