<?php
/** Real FCM HTTP v1 delivery. No purchase, user or gift contents travel in the push payload. */
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class PushDelivery
{
    public const CLIENTS=['bazaar','myket','direct','admin'];
    public static $testTransport=null;
    public static function settings():array { return array_replace_recursive(['enabled'=>false,'project_id'=>'','sender_id'=>'','api_key'=>'','apps'=>array_fill_keys(self::CLIENTS,'')],(array)Settings::get('push_fcm',[])); }
    public static function publicConfig(string $client):array {
        if(!in_array($client,self::CLIENTS,true))throw new ApiError('push_client','نسخهٔ برنامه نامعتبر است.');
        $c=self::settings();return ['enabled'=>$c['enabled']&&$c['project_id']!==''&&$c['apps'][$client]!==''&&Settings::secret('push_service_account')!=='','project_id'=>$c['project_id'],'sender_id'=>$c['sender_id'],'api_key'=>$c['api_key'],'app_id'=>$c['apps'][$client]];
    }
    public static function save(array $in):void {
        $c=self::settings();$c['enabled']=!empty($in['enabled']);
        foreach(['project_id','sender_id','api_key'] as $key)$c[$key]=trim((string)($in[$key]??''));
        if(($c['project_id']!==''&&!preg_match('/^[a-z][a-z0-9-]{4,62}$/',$c['project_id']))||($c['sender_id']!==''&&!preg_match('/^[0-9]{5,30}$/',$c['sender_id']))||($c['api_key']!==''&&!preg_match('/^[A-Za-z0-9_-]{20,100}$/',$c['api_key'])))throw new ApiError('push_config','شناسه‌های Firebase معتبر نیستند.');
        foreach(self::CLIENTS as $client){$id=trim((string)($in['apps'][$client]??''));if($id!==''&&!preg_match('/^1:[0-9]+:android:[a-f0-9]+$/',$id))throw new ApiError('push_config','شناسهٔ اندروید معتبر نیست.');$c['apps'][$client]=$id;}
        $secret=trim((string)($in['service_account']??''));
        if($secret!==''){
            $s=json_decode($secret,true);
            if(!is_array($s)||($s['type']??'')!=='service_account'||($s['project_id']??'')!==$c['project_id']||!filter_var($s['client_email']??'',FILTER_VALIDATE_EMAIL)||!openssl_pkey_get_private($s['private_key']??''))throw new ApiError('push_secret','فایل Service Account با پروژه تطابق ندارد.');
            $secret=json_encode(['type'=>'service_account','project_id'=>$s['project_id'],'client_email'=>$s['client_email'],'private_key'=>$s['private_key']],JSON_UNESCAPED_SLASHES);
        }
        Db::tx(function()use($c,$secret){Settings::set('push_fcm',$c);if($secret!==''){Settings::setSecret('push_service_account',$secret);Settings::setSecret('push_access_token','');}});
    }
    public static function register(array $in,?array $user,?array $session,?array $admin=null):array {
        if(!RateLimit::hit('push_register:'.Http::ip(),120,3600))throw new ApiError('rate_limited','درخواست زیاد است.',429);
        $token=(string)($in['token']??'');if(!preg_match('/^[A-Za-z0-9:_-]{20,4096}$/',$token))throw new ApiError('push_token','توکن اعلان نامعتبر است.');
        $client=$admin!==null?'admin':(string)($in['store']??'');if(!in_array($client,self::CLIENTS,true)||($admin===null&&$client==='admin'))throw new ApiError('push_client','نسخه نامعتبر است.');
        $device=$admin!==null?'':(string)($in['device']??'');if($admin===null&&!preg_match('/^[a-f0-9]{64}$/',$device))throw new ApiError('no_device','شناسهٔ دستگاه نامعتبر است.');
        if($user!==null && (($session['device_id']??'')!==$device||($session['store']??'')!==$client))throw new ApiError('push_device','دستگاه با نشست تطابق ندارد.',403);
        $h=Crypto::hash($token);$kind=$admin!==null?'admin':'player';
        $row=['kind'=>$kind,'client'=>$client,'token_enc'=>Crypto::encrypt($token),'device'=>$device,'user_id'=>$user['id']??null,'admin_id'=>$admin['id']??null,'session_hash'=>$admin!==null?Crypto::hash(substr(Http::header('Authorization'),7)):null,'offers'=>!empty($in[$admin!==null?'enabled':'offers'])?1:0,'pro_optin'=>!empty($in['pro_optin'])?1:0,'seen_at'=>Clock::now()];
        Db::tx(function()use($h,$row){$old=Db::one('SELECT * FROM {push_devices} WHERE token_hash=? FOR UPDATE',[$h]);if($old===null)Db::insert('push_devices',['token_hash'=>$h]+$row);else Db::update('push_devices',$row,'token_hash=?',[$h]);});
        return ['registered'=>true,'delivery'=>'fcm_http_v1','publisher_ready'=>self::publicConfig($client)['enabled']];
    }
    private static function adminValid(array $d):bool {
        $s=Db::one('SELECT s.*,a.password_hash,a.active,a.role FROM {admin_mobile_sessions} s JOIN {admins} a ON a.id=s.admin_id WHERE s.token_hash=? AND s.admin_id=?',[$d['session_hash'],$d['admin_id']]);
        return $s!==null&&(bool)$s['active']&&$s['revoked_at']===null&&(int)$s['expires_at']>Clock::now()&&hash_equals($s['password_stamp'],Crypto::hash($s['password_hash']))&&in_array($s['role'],['owner','manager','support'],true);
    }
    /** A cron runs this every minute. Queuing and delivery never block registration or payment verification. */
    public static function enqueue(int $limit=200):int {
        if(!self::settings()['enabled'])return 0;$count=0;
        foreach(Db::all('SELECT * FROM {push_devices} WHERE offers=1 AND seen_at>? ORDER BY last_scan,token_hash LIMIT '.max(1,min(1000,$limit)),[Clock::now()-2592000]) as $d){
            Db::update('push_devices',['last_scan'=>Clock::now()],'token_hash=?',[$d['token_hash']]);
            if(!self::publicConfig($d['client'])['enabled'])continue;
            $sources=[];
            if($d['kind']==='admin'){
                if(!self::adminValid($d))continue;
                $after=(int)(Db::value('SELECT created_at FROM {admin_mobile_sessions} WHERE token_hash=?',[$d['session_hash']])??Clock::now());
                foreach(Db::all("SELECT i.id FROM {admin_inbox} i WHERE i.created_at>=? AND NOT EXISTS (SELECT 1 FROM {push_deliveries} q WHERE q.device_hash=? AND q.source_key=CONCAT('inbox:',i.id)) ORDER BY i.id LIMIT 100",[Clock::sql($after),$d['token_hash']]) as $e)$sources[]='inbox:'.$e['id'];
            }else{
                $pro=$d['user_id']!==null&&Entitlements::summary((int)$d['user_id'],$d['client'])['pro'];
                foreach(Notices::due($d['device'],$d['client'],$pro,(bool)$d['pro_optin']) as $n)$sources[]='notices:'.$n['id'];
            }
            foreach($sources as $source){$q=Db::run('INSERT IGNORE INTO {push_deliveries} (device_hash,source_key,next_at,expires_at) VALUES (?,?,?,?)',[$d['token_hash'],$source,Clock::now(),Clock::now()+3600]);$count+=$q->rowCount();}
        }return $count;
    }
    private static function http(string $url,array $headers,string $body):array {
        if(defined('DIVAN_TEST')&&is_callable(self::$testTransport))return (self::$testTransport)($url,$headers,$body);
        return Http::request('POST',$url,$headers,$body,8);
    }
    private static function accessToken():string {
        $cached=json_decode(Settings::secret('push_access_token'),true);
        if(is_array($cached)&&($cached['expires_at']??0)>Clock::now()+60)return (string)$cached['token'];
        $s=json_decode(Settings::secret('push_service_account'),true);if(!is_array($s))throw new RuntimeException('publisher_pending');
        $enc=static fn(string $v):string=>rtrim(strtr(base64_encode($v),'+/','-_'),'=');
        $head=$enc(json_encode(['alg'=>'RS256','typ'=>'JWT']));
        $body=$enc(json_encode(['iss'=>$s['client_email'],'scope'=>'https://www.googleapis.com/auth/firebase.messaging','aud'=>'https://oauth2.googleapis.com/token','iat'=>Clock::now(),'exp'=>Clock::now()+3600]));
        $input=$head.'.'.$body;$signature='';if(!openssl_sign($input,$signature,$s['private_key'],OPENSSL_ALGO_SHA256))throw new RuntimeException('push_sign');
        $r=self::http('https://oauth2.googleapis.com/token',['Content-Type: application/x-www-form-urlencoded'],http_build_query(['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$input.'.'.$enc($signature)]));
        $data=json_decode($r['body'],true);if($r['status']!==200||empty($data['access_token']))throw new RuntimeException('push_oauth');
        Settings::setSecret('push_access_token',json_encode(['token'=>$data['access_token'],'expires_at'=>Clock::now()+min(3600,(int)($data['expires_in']??3600))]));return $data['access_token'];
    }
    public static function flush(int $limit=40):array {
        if(!self::settings()['enabled'])return ['sent'=>0,'errors'=>0];$sent=0;$errors=0;
        foreach(Db::all('SELECT id FROM {push_deliveries} WHERE sent_at IS NULL AND expires_at>? AND next_at<=? ORDER BY id LIMIT '.max(1,min(200,$limit)),[Clock::now(),Clock::now()]) as $row){
            // Lease serializes multiple cron workers; external sends cannot be exactly once, client notification IDs dedupe.
            $q=Db::run('UPDATE {push_deliveries} SET next_at=?,attempts=attempts+1 WHERE id=? AND next_at<=? AND sent_at IS NULL',[Clock::now()+120,$row['id'],Clock::now()]);if(!$q->rowCount())continue;
            $r=Db::one('SELECT q.*,d.* FROM {push_deliveries} q JOIN {push_devices} d ON d.token_hash=q.device_hash WHERE q.id=?',[$row['id']]);
            if($r===null||!$r['offers']||($r['kind']==='admin'&&!self::adminValid($r))){Db::run('UPDATE {push_deliveries} SET sent_at=?,last_error=? WHERE id=?',[Clock::now(),'canceled',$row['id']]);continue;}
            if($r['kind']==='player'){
                $pro=$r['user_id']!==null&&Entitlements::summary((int)$r['user_id'],$r['client'])['pro'];$due=Notices::due($r['device'],$r['client'],$pro,(bool)$r['pro_optin']);
                if(!$due||'notices:'.$due[0]['id']!==$r['source_key']){Db::run('UPDATE {push_deliveries} SET sent_at=?,last_error=? WHERE id=?',[Clock::now(),'canceled',$row['id']]);continue;}
            }
            try{
                $token=Crypto::decrypt($r['token_enc']);if($token===null)throw new RuntimeException('push_token');
                $payload=['message'=>['token'=>$token,'data'=>['kind'=>$r['kind']==='admin'?'inbox':'notices','source'=>$r['source_key']],'android'=>['priority'=>'high','ttl'=>'3600s']]];
                $response=self::http('https://fcm.googleapis.com/v1/projects/'.rawurlencode(self::settings()['project_id']).'/messages:send',['Authorization: Bearer '.self::accessToken(),'Content-Type: application/json'],json_encode($payload));
                if($response['status']!==200){
                    $json=json_decode($response['body'],true);foreach($json['error']['details']??[] as $detail)if(($detail['errorCode']??'')==='UNREGISTERED')Db::update('push_devices',['offers'=>0],'token_hash=?',[$r['token_hash']]);
                    if($response['status']===401)Settings::setSecret('push_access_token','');
                    throw new RuntimeException('push_http_'.(int)$response['status']);
                }
                Db::update('push_deliveries',['sent_at'=>Clock::now(),'last_error'=>null],'id=?',[$row['id']]);$sent++;
            }catch(Throwable $e){$errors++;$code=preg_match('/^push_[a-z0-9_]+$/',$e->getMessage())?$e->getMessage():'push_transport';Db::update('push_deliveries',['last_error'=>$code,'next_at'=>Clock::now()+min(1800,30*(2**min(6,(int)$r['attempts'])))],'id=?',[$row['id']]);}
        }return compact('sent','errors');
    }
    public static function stats():array {return ['devices'=>(int)Db::value('SELECT COUNT(*) FROM {push_devices} WHERE offers=1'),'sent'=>(int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE sent_at IS NOT NULL AND last_error IS NULL'),'pending'=>(int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE sent_at IS NULL AND expires_at>?',[Clock::now()]),'errors'=>Db::all('SELECT last_error,COUNT(*) AS total FROM {push_deliveries} WHERE last_error IS NOT NULL GROUP BY last_error')];}
}
