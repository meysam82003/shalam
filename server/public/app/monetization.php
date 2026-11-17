<?php
/** Iranian network policy and coin accounting. Existing ads, gifts and Pro rewards are retained. */
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }

final class Monetization
{
    public const NETWORKS = ['tapsell', 'adivery'];
    public const FORMATS = ['banner', 'interstitial', 'rewarded'];
    public const EVENTS = ['request', 'loaded', 'impression', 'click', 'close', 'complete', 'error'];
    public static function defaults(): array
    {
        return ['revision' => 1, 'enabled' => false, 'primary' => 'tapsell', 'fallback' => 'adivery', 'vip_ads' => false,
            'banner' => ['enabled' => true, 'pages' => ['home', 'shop'], 'placements' => ['home_inline' => true, 'home_bottom' => true, 'shop_bottom' => true]],
            'interstitial' => ['enabled' => true, 'hands' => 3, 'min_seconds' => 180, 'daily_cap' => 10],
            'rewarded' => ['enabled' => true, 'amount' => 100, 'daily_cap' => 5, 'min_seconds' => 30],
            'networks' => [
                'tapsell' => ['enabled' => true, 'test' => true, 'app_id' => '76798342-99a7-4a5f-bf5a-60a088d5dcfb', 'zones' => ['banner' => '4bf4e11d-0967-41e6-91f6-a128d9462f2a', 'interstitial' => '0c4e2849-feea-4688-8280-0b0ae2ee0728', 'rewarded' => '63b92f04-3d0f-4805-84a4-abeccf4edc18']],
                'adivery' => ['enabled' => true, 'test' => true, 'app_id' => '738c5e66-2e4f-4ff0-8118-ef6c8235a5e9', 'zones' => ['banner' => '06f0c520-3cc8-45c0-bbf1-f866f7eac525', 'interstitial' => '5788d25b-b7d6-442c-a562-b4bef582c18c', 'rewarded' => '29f509a9-3826-4ada-8404-4735aa14b510']],
            ]];
    }
    public static function settings(): array
    {
        $raw=(array)Settings::get('iran_ads', []);$c=array_replace_recursive(self::defaults(),$raw);
        if(isset($raw['banner']['pages']))$c['banner']['pages']=$raw['banner']['pages'];return $c;
    }
    public static function save(array $in): array
    {
        $old = self::settings(); $c = $old;
        foreach (['enabled', 'vip_ads'] as $k) if (array_key_exists($k, $in)) $c[$k] = self::boolean($in[$k]);
        foreach (['primary', 'fallback'] as $k) if (array_key_exists($k, $in)) {
            if (!in_array($in[$k], array_merge(self::NETWORKS, $k === 'fallback' ? ['none'] : []), true)) throw new ApiError('settings', 'شبکه نامعتبر است.');
            $c[$k] = $in[$k];
        }
        foreach (self::FORMATS as $format) if (isset($in[$format])) {
            if (!is_array($in[$format])) throw new ApiError('settings', 'تنظیم نامعتبر است.');
            if (isset($in[$format]['enabled'])) $c[$format]['enabled'] = self::boolean($in[$format]['enabled']);
        }
        foreach (['hands' => [1,100], 'min_seconds' => [180,86400], 'daily_cap' => [1,100]] as $k => $range)
            if (isset($in['interstitial'][$k])) $c['interstitial'][$k] = self::number($in['interstitial'][$k], $range[0], $range[1]);
        foreach (['amount' => [1,100000], 'daily_cap' => [1,100], 'min_seconds' => [10,300]] as $k => $range)
            if (isset($in['rewarded'][$k])) $c['rewarded'][$k] = self::number($in['rewarded'][$k], $range[0], $range[1]);
        if (isset($in['banner']['pages'])) {
            $pages = $in['banner']['pages'];
            if (!is_array($pages) || array_diff($pages, ['home','shop','history','settings','hub','leagues','ranking','about'])) throw new ApiError('settings', 'صفحهٔ حساس برای تبلیغات مجاز نیست.');
            $c['banner']['pages'] = array_values(array_unique($pages));
        }
        if (isset($in['banner']['placements'])) {
            $slots = (array)$in['banner']['placements'];
            foreach ($slots as $key => $on) {
                if (!preg_match('/^[a-z_]{3,60}$/', (string)$key) || !in_array($key, array_merge(array_column(Ads::placements(), 'k'), ['shop_bottom']), true)) throw new ApiError('settings', 'جایگاه نامعتبر است.');
                $c['banner']['placements'][$key] = self::boolean($on);
            }
        }
        foreach (self::NETWORKS as $n) if (isset($in['networks'][$n])) {
            $v = (array)$in['networks'][$n];
            foreach (['enabled','test'] as $k) if (isset($v[$k])) $c['networks'][$n][$k] = self::boolean($v[$k]);
            if (isset($v['app_id'])) $c['networks'][$n]['app_id'] = self::identifier($v['app_id'], true);
            foreach (self::FORMATS as $f) if (isset($v['zones'][$f])) $c['networks'][$n]['zones'][$f] = self::identifier($v['zones'][$f], true);
            if (!$c['networks'][$n]['test'] && $c['networks'][$n]['app_id'] === self::defaults()['networks'][$n]['app_id']) throw new ApiError('test_credentials', 'شناسه‌های تست را نمی‌توان به‌عنوان تبلیغ واقعی ثبت کرد.');
        }
        $c['revision'] = (int)$old['revision'] + 1;
        Settings::set('iran_ads', $c);
        if(array_key_exists('enabled',$in)) Settings::set('ads_enabled',$c['enabled']);
        return $c;
    }
    private static function boolean($v): bool { if (!is_bool($v) && !in_array($v,[0,1,'0','1'],true)) throw new ApiError('settings','مقدار روشن/خاموش نامعتبر است.'); return (bool)$v; }
    private static function number($v, int $min, int $max): int {
        if (filter_var($v,FILTER_VALIDATE_INT) === false || (int)$v < $min || (int)$v > $max) throw new ApiError('settings','عدد خارج از محدوده است.');
        return (int)$v;
    }
    private static function identifier($v, bool $empty = false): string {
        if (!is_string($v) || (!($empty && $v === '') && !preg_match('/^[a-zA-Z0-9_-]{8,160}$/',$v))) throw new ApiError('settings','شناسهٔ تبلیغ نامعتبر است.'); return $v;
    }
    public static function publicConfig(bool $pro): array
    {
        $c = self::settings();
        $c['enabled'] = $c['enabled'] && (bool)Settings::get('ads_enabled', true) && (!$pro || $c['vip_ads']);
        $c['server_time'] = Clock::now(); $c['ttl_seconds'] = 300;
        $c['reward_verification'] = 'callback_with_server_limits';
        $c['ssv_available'] = false;
        return $c;
    }
    public static function wallet(int $uid): array
    {
        $balance = (int)(Db::value('SELECT balance FROM {coin_wallets} WHERE user_id = ?',[$uid]) ?? 0);
        $start = self::dayStart();
        $used = (int)Db::value('SELECT COUNT(*) FROM {coin_transactions} WHERE user_id = ? AND created_at >= ?',[$uid,Clock::sql($start)]);
        return ['balance'=>$balance,'rewarded_today'=>$used,'daily_cap'=>self::settings()['rewarded']['daily_cap'],'next_day_at'=>$start+86400];
    }
    private static function dayStart(): int { return (int)(new DateTimeImmutable('@'.Clock::now()))->setTimezone(new DateTimeZone('Asia/Tehran'))->setTime(0,0)->getTimestamp(); }
    public static function start(array $u, array $session, array $in): array
    {
        $uid=(int)$u['id']; $device=Devices::clean($session['device_id']??null);
        if ($device===null) throw new ApiError('no_device','شناسهٔ دستگاه لازم است.');
        $key=(string)($in['request_id']??'');
        if (!preg_match('/^[a-zA-Z0-9_-]{16,64}$/',$key)) throw new ApiError('request_id','شناسهٔ درخواست معتبر نیست.');
        return Db::tx(function() use($u,$uid,$session,$in,$device,$key) {
            Db::one('SELECT id FROM {users} WHERE id = ? FOR UPDATE',[$uid]);
            $existing=Db::one('SELECT * FROM {coin_watches} WHERE user_id = ? AND start_key = ?',[$uid,$key]);
            if ($existing!==null) return self::watchView($existing);
            $cfg=self::publicConfig(Entitlements::summary($uid,$session['store']??null)['pro']);
            $network=(string)($in['network']??'');
            if (!$cfg['enabled'] || !$cfg['rewarded']['enabled'] || !in_array($network,[$cfg['primary'],$cfg['fallback']],true) || !in_array($network,self::NETWORKS,true)) throw new ApiError('ads_disabled','تبلیغ جایزه‌دار اکنون فعال نیست.');
            $n=$cfg['networks'][$network];
            if (!$n['enabled'] || $n['app_id']==='' || $n['zones']['rewarded']==='') throw new ApiError('publisher_pending','در انتظار تنظیمات ناشر');
            self::quota($uid,$device,(int)$cfg['rewarded']['daily_cap']);
            $w=['id'=>bin2hex(random_bytes(16)),'user_id'=>$uid,'device'=>$device,'start_key'=>$key,'network'=>$network,'placement'=>$n['zones']['rewarded'],'config_revision'=>$cfg['revision'],'amount'=>$cfg['rewarded']['amount'],'daily_cap'=>$cfg['rewarded']['daily_cap'],'test_mode'=>$n['test']?1:0,'started_at'=>Clock::now(),'min_seconds'=>$cfg['rewarded']['min_seconds'],'completed_at'=>null,'claimed_at'=>null];
            Db::insert('coin_watches',$w);return self::watchView($w);
        });
    }
    private static function watchView(array $w): array { return ['watch_id'=>$w['id'],'network'=>$w['network'],'placement'=>$w['placement'],'test_mode'=>(bool)$w['test_mode'],'amount'=>(int)$w['amount'],'expires_at'=>(int)$w['started_at']+3600,'claimed'=>$w['claimed_at']!==null]; }
    private static function quota(int $uid,string $device,int $cap): void {
        $day=self::dayStart();
        $n=(int)Db::value('SELECT COUNT(*) FROM {coin_watches} WHERE claimed_at >= ? AND (user_id = ? OR device = ?)',[$day,$uid,$device]);
        if ($n >= $cap) throw new ApiError('reward_limit','سقف روزانهٔ پاداش دریافت شده است.',429);
    }
    public static function events(array $u, array $session, array $in): array
    {
        $uid=(int)$u['id']; $accepted=0;
        $events=$in['events']??[];
        if (!is_array($events) || count($events)>50) throw new ApiError('events','تعداد رویدادها نامعتبر است.');
        foreach ($events as $e) {
            if (!is_array($e)) throw new ApiError('events','رویداد نامعتبر است.');
            $key=(string)($e['id']??'');$network=(string)($e['network']??'');$format=(string)($e['format']??'');$event=(string)($e['event']??'');$placement=(string)($e['placement']??'');
            if (!preg_match('/^[a-zA-Z0-9_-]{16,64}$/',$key) || !in_array($network,self::NETWORKS,true) || !in_array($format,self::FORMATS,true) || !in_array($event,self::EVENTS,true) || !preg_match('/^[a-zA-Z0-9_-]{1,100}$/',$placement)) throw new ApiError('events','رویداد تبلیغات معتبر نیست.');
            $watch=$e['watch_id']??null;
            if($watch!==null && (!is_string($watch)||!preg_match('/^[a-f0-9]{32}$/',$watch)))throw new ApiError('watch','شناسهٔ ویدئو معتبر نیست.');
            Db::tx(function() use($uid,$session,$e,$key,$network,$format,$event,$placement,$watch,&$accepted) {
                $test=self::settings()['networks'][$network]['test']?1:0;
                if ($event==='complete') {
                    if ($format!=='rewarded' || !is_string($watch) || !preg_match('/^[a-f0-9]{32}$/',$watch)) throw new ApiError('watch','شناسهٔ ویدئو معتبر نیست.');
                    $w=Db::one('SELECT * FROM {coin_watches} WHERE id = ? AND user_id = ? FOR UPDATE',[$watch,$uid]);
                    if ($w===null || $w['network']!==$network || $w['placement']!==$placement || $w['device']!==($session['device_id']??null) || Clock::now()<(int)$w['started_at']+(int)$w['min_seconds'] || Clock::now()>(int)$w['started_at']+3600) throw new ApiError('incomplete_watch','ویدئو کامل نشده یا درخواست منقضی شده است.');
                    $test=(int)$w['test_mode'];
                    if ($w['completed_at']===null) Db::update('coin_watches',['completed_at'=>Clock::now()],'id = ?',[$watch]);
                }
                $stmt=Db::run('INSERT IGNORE INTO {network_ad_events} (event_key,user_id,network,format,placement,event,watch_id,test_mode,created_at) VALUES (?,?,?,?,?,?,?,?,?)',[$key,$uid,$network,$format,$placement,$event,is_string($watch)?$watch:null,$test,Clock::sql()]);
                $accepted += $stmt->rowCount();
            });
        }
        return ['accepted'=>$accepted];
    }
    public static function claim(array $u,array $session,array $in): array
    {
        $uid=(int)$u['id'];$device=Devices::clean($session['device_id']??null);$id=(string)($in['watch_id']??'');
        if ($device===null || !preg_match('/^[a-f0-9]{32}$/',$id)) throw new ApiError('watch','شناسهٔ ویدئو معتبر نیست.');
        return Db::tx(function() use($uid,$device,$id) {
            Db::one('SELECT id FROM {users} WHERE id = ? FOR UPDATE',[$uid]);
            Db::run('INSERT IGNORE INTO {coin_device_locks} (device) VALUES (?)',[$device]);
            Db::one('SELECT device FROM {coin_device_locks} WHERE device = ? FOR UPDATE',[$device]);
            $w=Db::one('SELECT * FROM {coin_watches} WHERE id = ? AND user_id = ? AND device = ? FOR UPDATE',[$id,$uid,$device]);
            if ($w===null) throw new ApiError('watch','درخواست ویدئو پیدا نشد.',404);
            $tx=Db::one('SELECT * FROM {coin_transactions} WHERE watch_id = ?',[$id]);
            if ($tx!==null) return ['wallet'=>self::wallet($uid),'transaction_id'=>(int)$tx['id'],'duplicate'=>true];
            if ($w['claimed_at']!==null) return ['wallet'=>self::wallet($uid),'test_reward'=>true,'duplicate'=>true];
            if ($w['completed_at']===null || Clock::now()<(int)$w['started_at']+(int)$w['min_seconds'] || Clock::now()>(int)$w['started_at']+3600) throw new ApiError('incomplete_watch','ویدئو کامل نشده است.');
            self::quota($uid,$device,(int)$w['daily_cap']);
            Db::update('coin_watches',['claimed_at'=>Clock::now()],'id = ?',[$id]);
            if ((int)$w['test_mode']===1) return ['wallet'=>self::wallet($uid),'test_reward'=>true,'message'=>'ویدئوی آزمایشی تکمیل شد؛ سکهٔ واقعی پرداخت نمی‌شود.'];
            Db::run('INSERT IGNORE INTO {coin_wallets} (user_id,balance,updated_at) VALUES (?,0,?)',[$uid,Clock::sql()]);
            Db::one('SELECT user_id FROM {coin_wallets} WHERE user_id = ? FOR UPDATE',[$uid]);
            Db::run('UPDATE {coin_wallets} SET balance = balance + ?, updated_at = ? WHERE user_id = ?',[$w['amount'],Clock::sql(),$uid]);
            $balance=(int)Db::value('SELECT balance FROM {coin_wallets} WHERE user_id = ?',[$uid]);
            $txid=Db::insert('coin_transactions',['user_id'=>$uid,'watch_id'=>$id,'amount'=>$w['amount'],'balance_after'=>$balance,'network'=>$w['network'],'created_at'=>Clock::sql()]);
            return ['wallet'=>self::wallet($uid),'transaction_id'=>$txid,'duplicate'=>false];
        });
    }
    public static function stats(): array
    {
        return ['events'=>Db::all('SELECT network,event,test_mode,COUNT(*) AS total FROM {network_ad_events} WHERE created_at >= ? GROUP BY network,event,test_mode',[Clock::sql(Clock::now()-30*86400)]),
            'paid_rewards'=>Db::all('SELECT network,COUNT(*) AS total,SUM(amount) AS coins FROM {coin_transactions} GROUP BY network'),
            'history'=>Db::all('SELECT t.id,u.username,t.amount,t.balance_after,t.network,t.created_at FROM {coin_transactions} t JOIN {users} u ON u.id = t.user_id ORDER BY t.id DESC LIMIT 100'),
            'revenue'=>null,'revenue_label'=>'ناموجود','verification'=>'رویداد SDK و کنترل‌های سرور؛ SSV رسمی پیکربندی نشده است.'];
    }
}
