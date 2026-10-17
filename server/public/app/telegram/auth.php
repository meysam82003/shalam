<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }

final class TgConfig
{
    public static function get(): array { return (array)Settings::get('telegram', []) + ['bot_enabled'=>false,'app_enabled'=>false,'paused'=>true,'app_url'=>App::baseUrl().'/tg/app/','pending_minutes'=>60,'retention_days'=>30]; }
    public static function token(): string { return Settings::secret('tg_bot_token'); }
    public static function requireHttps(): void
    {
        // Do not trust arbitrary forwarding headers. Reverse proxies must set HTTPS at the web server.
        if (PHP_SAPI !== 'cli' && (empty($_SERVER['HTTPS']) || $_SERVER['HTTPS'] === 'off')) throw new ApiError('https_required','اتصال امن HTTPS لازم است.',403);
        if (stripos(App::baseUrl(), 'https://') !== 0) throw new ApiError('https_required','base_url سرور باید HTTPS باشد.',503);
    }
    public static function appUrl(string $route = 'dashboard', string $id = ''): string
    {
        return self::get()['app_url'].'#'.rawurlencode($route).($id !== '' ? '/'.rawurlencode($id) : '');
    }
    public static function safeUrl(string $url): string
    {
        $expected = rtrim(App::baseUrl(), '/').'/tg/app/';
        if ($url !== $expected || stripos($url,'https://') !== 0) throw new ApiError('app_url','مسیر مینی‌اپ باید دقیقاً '.$expected.' باشد.');
        return $url;
    }
}

final class TgLimit
{
    // Atomic fixed windows; unlike SELECT-then-increment, concurrent requests cannot all pass.
    public static function hit(string $key, int $max, int $seconds): bool
    {
        $k = hash('sha256',$key); $window = intdiv(Clock::now(),$seconds)*$seconds;
        return Db::tx(function() use($k,$window,$max) {
            Db::run('INSERT IGNORE INTO {tg_limits} (k,window_start,hits) VALUES (?,?,0)',[$k,$window]);
            $r = Db::one('SELECT * FROM {tg_limits} WHERE k=? FOR UPDATE',[$k]);
            $hits = (int)$r['window_start'] === $window ? (int)$r['hits'] : 0;
            if ($hits >= $max) return false;
            Db::update('tg_limits',['window_start'=>$window,'hits'=>$hits+1],'k=?',[$k]);
            return true;
        });
    }
    public static function require(string $key,int $max,int $seconds): void
    {
        if (!self::hit($key,$max,$seconds)) throw new ApiError('rate_limited','درخواست زیاد است؛ کمی بعد دوباره تلاش کنید.',429);
    }
}

final class TgPolicy
{
    public const ROLES = ['owner'=>'مدیر اصلی','super_admin'=>'مدیر ارشد','finance'=>'مالی','support'=>'پشتیبان','content'=>'مدیریت محتوا','analyst'=>'تحلیلگر'];
    public const PERMISSIONS = ['dashboard','users.read','users.write','pro.write','sales.read','plans.write','gateways.write','content.read','content.write','support.read','support.write','reports','audit.read','health','alerts','telegram.manage'];
    private const ROLE = [
        'owner'=>self::PERMISSIONS,
        'super_admin'=>['dashboard','users.read','users.write','pro.write','sales.read','plans.write','gateways.write','content.read','content.write','support.read','support.write','reports','audit.read','health','alerts'],
        'finance'=>['dashboard','sales.read','plans.write','reports','health','alerts'],
        'support'=>['dashboard','users.read','users.write','sales.read','support.read','support.write','alerts'],
        'content'=>['dashboard','content.read','content.write','alerts'],
        'analyst'=>['dashboard','sales.read','content.read','reports','health','alerts'],
    ];
    public static function permissions(array $m): array
    {
        $ceiling = ['owner'=>'owner','manager'=>'super_admin','support'=>'support','analyst'=>'analyst'][$m['admin_role'] ?? ''] ?? '';
        $perms = array_values(array_intersect(self::ROLE[$m['role']] ?? [],self::ROLE[$ceiling] ?? []));
        if (isset($m['permissions']) && $m['permissions'] !== null) $perms = array_values(array_intersect($perms,(array)json_decode($m['permissions'],true)));
        return $perms;
    }
    public static function can(array $m,string $p): bool { return in_array($p,self::permissions($m),true); }
    public static function require(array $m,string $p): void { if (!self::can($m,$p)) throw new ApiError('forbidden','برای این عملیات مجوز ندارید.',403); }
    public static function member(string $telegramId): ?array
    {
        return Db::one('SELECT m.*, a.role admin_role, a.username FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id WHERE m.telegram_id=? AND m.active=1 AND a.active=1',[$telegramId]);
    }
}

final class TgAuth
{
    public static function validate(string $raw,string $token,?int $now=null): array
    {
        if ($token === '' || strlen($raw)>8192 || $raw === '') throw new ApiError('init_data','اطلاعات ورود تلگرام معتبر نیست.',401);
        $values=[];
        foreach (explode('&',$raw) as $pair) {
            $parts=explode('=',$pair,2);
            if(count($parts)!==2) throw new ApiError('init_data','اطلاعات ورود تلگرام معتبر نیست.',401);
            $key=urldecode($parts[0]);
            if(!preg_match('/^[a-z_]+$/D',$key) || array_key_exists($key,$values)) throw new ApiError('init_data','پارامتر تکراری یا نامعتبر.',401);
            $values[$key]=urldecode($parts[1]);
        }
        $hash=$values['hash'] ?? ''; unset($values['hash']);
        if(!preg_match('/^[a-f0-9]{64}$/D',$hash)) throw new ApiError('init_data','امضای ورود نامعتبر است.',401);
        ksort($values,SORT_STRING); $lines=[]; foreach($values as $k=>$v) $lines[]=$k.'='.$v;
        $secret=hash_hmac('sha256',$token,'WebAppData',true);
        $expected=hash_hmac('sha256',implode("\n",$lines),$secret);
        if(!hash_equals($expected,$hash)) throw new ApiError('init_data','امضای ورود نامعتبر است.',401);
        $now=$now ?? Clock::now(); $auth=$values['auth_date'] ?? '';
        if(!ctype_digit($auth) || (int)$auth < $now-300 || (int)$auth > $now+30) throw new ApiError('expired','ورود منقضی است؛ مینی‌اپ را از ربات دوباره باز کنید.',401);
        $u=json_decode($values['user'] ?? '',true);
        if(!is_array($u) || !isset($u['id']) || !preg_match('/^[1-9][0-9]{0,15}$/D',(string)$u['id']) || !empty($u['is_bot'])) throw new ApiError('init_data','حساب تلگرام معتبر نیست.',401);
        return ['telegram_id'=>(string)$u['id'],'digest'=>$hash,'expires_at'=>Clock::sql((int)$auth+331)];
    }
    public static function exchange(string $raw): array
    {
        TgConfig::requireHttps();
        if(!TgConfig::get()['app_enabled']) throw new ApiError('disabled','مینی‌اپ غیرفعال است.',503);
        TgLimit::require('auth:'.Http::ip(),20,60);
        $v=self::validate($raw,TgConfig::token()); $m=TgPolicy::member($v['telegram_id']);
        if(!$m) { Audit::security('tg_unauthorized',null); throw new ApiError('forbidden','حساب تلگرام به مدیر مجاز متصل نیست.',403); }
        return Db::tx(function() use($v,$m) {
            $n=Db::run('INSERT IGNORE INTO {tg_replays} (digest,expires_at) VALUES (?,?)',[$v['digest'],$v['expires_at']])->rowCount();
            if($n!==1) throw new ApiError('replay','این ورود قبلاً استفاده شده؛ مینی‌اپ را ببندید و دوباره از ربات باز کنید.',401);
            $token=Crypto::token(); $expires=Clock::sql(Clock::now()+1800);
            Db::insert('tg_sessions',['token_hash'=>Crypto::hash($token),'member_id'=>$m['id'],'epoch'=>$m['epoch'],'expires_at'=>$expires,'created_at'=>Clock::sql()]);
            Audit::log('admin',(int)$m['admin_id'],'tg_login','tg_member',$m['id']);
            return ['token'=>$token,'expires_at'=>$expires,'me'=>self::publicMember($m)];
        });
    }
    public static function publicMember(array $m): array { return ['id'=>(int)$m['id'],'username'=>$m['username'],'role'=>$m['role'],'permissions'=>TgPolicy::permissions($m)]; }
    public static function current(): array
    {
        TgConfig::requireHttps();
        if(!TgConfig::get()['app_enabled']) throw new ApiError('disabled','مینی‌اپ غیرفعال است.',503);
        $h=Http::header('Authorization');
        if(!preg_match('/^Bearer ([A-Za-z0-9_-]{40,64})$/D',$h,$match)) throw new ApiError('unauthorized','نشست معتبر لازم است.',401);
        $m=Db::one('SELECT m.*,a.role admin_role,a.username,s.id session_id,s.expires_at session_expires FROM {tg_sessions} s JOIN {tg_members} m ON m.id=s.member_id JOIN {admins} a ON a.id=m.admin_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>? AND s.epoch=m.epoch AND m.active=1 AND a.active=1',[Crypto::hash($match[1]),Clock::sql()]);
        if(!$m) throw new ApiError('unauthorized','نشست پایان یافته است؛ دوباره از ربات وارد شوید.',401);
        TgLimit::require('session:'.$m['session_id'],120,60);
        // Active use renews the idle timeout silently; revocation and live role checks still apply.
        if(Clock::ts($m['session_expires'])<Clock::now()+900)Db::update('tg_sessions',['expires_at'=>Clock::sql(Clock::now()+1800)],'id=? AND revoked_at IS NULL',[$m['session_id']]);
        return $m;
    }
    public static function requestHash(string $action,array $data): string
    {
        $sort=function($v) use(&$sort) { if(!is_array($v)) return $v; if(array_keys($v)!==range(0,count($v)-1)) ksort($v); foreach($v as &$x) $x=$sort($x); return $v; };
        return hash('sha256',$action."\n".json_encode($sort($data),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES));
    }
    public static function issueLink(int $adminId,string $telegramId,string $role): string
    {
        if(!preg_match('/^[1-9][0-9]{0,15}$/D',$telegramId) || !isset(TgPolicy::ROLES[$role])) throw new ApiError('link','شناسه عددی یا نقش نامعتبر است.');
        $a=Db::one('SELECT * FROM {admins} WHERE id=? AND active=1',[$adminId]);
        if(!$a || ($role==='owner' && $a['role']!=='owner')) throw new ApiError('role','نقش مالک فقط برای حساب مالک مجاز است.');
        $code=Crypto::token(24);
        Db::update('tg_links',['used_at'=>Clock::sql()],'admin_id=? AND used_at IS NULL',[$adminId]);
        Db::insert('tg_links',['code_hash'=>Crypto::hash($code),'admin_id'=>$adminId,'telegram_id'=>$telegramId,'role'=>$role,'expires_at'=>Clock::sql(Clock::now()+300),'created_at'=>Clock::sql()]);
        return $code;
    }
    public static function link(string $code,string $telegramId): array
    {
        TgLimit::require('link:'.$telegramId,5,300);
        return Db::tx(function() use($code,$telegramId) {
            $r=Db::one('SELECT l.*,a.active FROM {tg_links} l JOIN {admins} a ON a.id=l.admin_id WHERE code_hash=? FOR UPDATE',[Crypto::hash($code)]);
            if(!$r || !$r['active'] || $r['used_at']!==null || Clock::ts($r['expires_at'])<=Clock::now() || !hash_equals((string)$r['telegram_id'],$telegramId)) throw new ApiError('link','کد اتصال معتبر نیست یا برای حساب دیگری صادر شده.',403);
            $other=Db::one('SELECT id FROM {tg_members} WHERE telegram_id=? AND admin_id<>?',[$telegramId,$r['admin_id']]);
            if($other) throw new ApiError('link','این حساب تلگرام قبلاً متصل است.',409);
            Db::run('INSERT INTO {tg_members} (admin_id,telegram_id,role,active,epoch,created_at) VALUES (?,?,?,1,1,?) ON DUPLICATE KEY UPDATE telegram_id=VALUES(telegram_id),role=VALUES(role),permissions=NULL,active=1,epoch=epoch+1',[$r['admin_id'],$telegramId,$r['role'],Clock::sql()]);
            Db::update('tg_links',['used_at'=>Clock::sql()],'code_hash=?',[$r['code_hash']]);
            $m=TgPolicy::member($telegramId); Audit::log('admin',(int)$r['admin_id'],'tg_linked','tg_member',$m['id']); return $m;
        });
    }
}
