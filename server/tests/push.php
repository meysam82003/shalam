<?php
require_once dirname(__DIR__) . '/public/app/app_updates.php';
require __DIR__ . '/transfer_links.php';

scenario('Android update policies are opt-in and isolated by store', function () {
    check(apiGet('v1/app-update', ['store' => 'bazaar'])['version_code'] === 0, 'new installation has no update policy');
    throws(fn() => AppUpdates::fromForm(['version_code' => '0', 'required' => '1']), 'update_required', 'mandatory requires a published version');
    throws(fn() => AppUpdates::fromForm(['version_code' => '33', 'version_name' => 'https://elsewhere.invalid']), 'update_version', 'version label must be valid');
    $policy = AppUpdates::fromForm(['version_code' => '34', 'version_name' => '5.6.7', 'notes' => 'توضیحات', 'required' => '1']);
    Settings::set('android_update_myket', $policy);
    check(apiGet('v1/app-update', ['store' => 'myket']) === $policy, 'Myket sees the configured version and mandatory flag');
    check(apiGet('v1/app-update', ['store' => 'direct'])['version_code'] === 0, 'the website channel remains independent');
    Settings::set('android_update_myket', AppUpdates::fromForm(['version_code' => '0']));
});

scenario('Push: encrypted credentials, real HTTP v1 payload, retries and consent',function(){
    $old=Settings::get('push_fcm');$oldSecret=Settings::secret('push_service_account');$oldAccess=Settings::secret('push_access_token');$enabled=Settings::get('notices_enabled');
    $key=openssl_pkey_new(['private_key_bits'=>2048,'private_key_type'=>OPENSSL_KEYTYPE_RSA]);openssl_pkey_export($key,$private);
    $config=['enabled'=>true,'project_id'=>'divan-test-project','sender_id'=>'123456789','api_key'=>str_repeat('a',39),'apps'=>array_fill_keys(PushDelivery::CLIENTS,'1:123456789:android:abcdef123456')];
    throws(fn()=>PushDelivery::save($config+['service_account'=>'{"private_key":"wrong"}']),'push_secret','invalid credential rejected');
    $secret=json_encode(['type'=>'service_account','project_id'=>'divan-test-project','client_email'=>'push@divan-test-project.iam.gserviceaccount.com','private_key'=>$private]);
    PushDelivery::save($config+['service_account'=>$secret]);
    check(PushDelivery::publicConfig('bazaar')['enabled'],'configured app can initialize Firebase without new APK');
    check(strpos(json_encode(PushDelivery::publicConfig('admin')),'private_key')===false,'private key never exposed to either app');
    check(strpos((string)Db::value('SELECT v FROM {settings} WHERE k=?',['push_service_account']),'BEGIN PRIVATE KEY')===false,'service account encrypted at rest');
    PushDelivery::save($config);check(Settings::secret('push_service_account')!=='','blank secret preserves existing credentials');
    throws(fn()=>PushDelivery::register(['token'=>str_repeat('t',30),'store'=>'admin'],null,null),'push_client','player cannot register as administrator');
    $device=hash('sha256','push-phone');$token='test_push_token_'.str_repeat('t',40);
    Settings::set('notices_enabled',true);
    Db::run('UPDATE {notices} SET active=0');
    $nid=Db::insert('notices',['title'=>'اعلان تست واقعی مسیر','body'=>'این متن فقط در دیتابیس است','stores'=>'bazaar','audience'=>'all','active'=>1,'created_at'=>Clock::sql(),'updated_at'=>Clock::sql()]);
    $input=['token'=>$token,'device'=>$device,'store'=>'bazaar','offers'=>false,'pro_optin'=>false];
    PushDelivery::register($input,null,null);PushDelivery::enqueue();
    check((int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE device_hash=?',[Crypto::hash($token)])===0,'disabled consent does not enqueue');
    $input['offers']=true;PushDelivery::register($input,null,null);PushDelivery::enqueue();PushDelivery::enqueue();
    check((int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE device_hash=?',[Crypto::hash($token)])===1,'repeated cron does not duplicate a delivery');
    $calls=[];$fail=true;$public=openssl_pkey_get_details($key)['key'];
    PushDelivery::$testTransport=function($url,$headers,$body)use(&$calls,&$fail,$public){
        $calls[]=$url;
        if(strpos($url,'oauth2.googleapis.com')!==false){parse_str($body,$form);$parts=explode('.',$form['assertion']);$sig=base64_decode(strtr($parts[2],'-_','+/'));check(openssl_verify($parts[0].'.'.$parts[1],$sig,$public,OPENSSL_ALGO_SHA256)===1,'OAuth assertion signed with registered service key');return ['status'=>200,'body'=>json_encode(['access_token'=>'only-a-test-token','expires_in'=>3600])];}
        $p=json_decode($body,true);
        if (isset($p['message']['topic'])) {
            check($p['message']['topic']==='divan_android_updates_direct' && $p['message']['data']===['kind'=>'app_update'], 'update wake-up targets only the configured store');
        } else {
            check($p['message']['android']['priority']==='high'&&$p['message']['data']['kind']==='notices','real FCM high priority data payload');
        }
        check(!isset($p['message']['notification'])&&strpos($body,'این متن')===false,'payload contains no personal or offer text; client rechecks consent and live server state');
        return ['status'=>$fail?503:200,'body'=>$fail?'{}':'{"name":"projects/divan-test-project/messages/test"}'];
    };
    $r=PushDelivery::flush();check($r['errors']>=1&&$r['sent']===0,'provider outage keeps delivery pending');
    check(PushDelivery::flush()['errors']===0,'retry backoff respected');
    Clock::$fixed+=121;$fail=false;$r=PushDelivery::flush();check($r['sent']===1,'next cron retries without app launch');
    $count=count($calls);PushDelivery::flush();check(count($calls)===$count,'successful delivery not resent');
    [$premium]=Users::register('push_pro_consent','VeryStrong_password_908');
    Db::insert('user_grants',['user_id'=>$premium['id'],'feature_k'=>'pro','expires_at'=>Clock::sql(Clock::now()+3600),'reason'=>'test consent fixture','created_at'=>Clock::sql()]);
    $proDevice=hash('sha256','pro-push-phone');$proToken='pro_push_token_'.str_repeat('p',40);
    $proInput=['token'=>$proToken,'device'=>$proDevice,'store'=>'bazaar','offers'=>false,'pro_optin'=>true];
    PushDelivery::register($proInput,$premium,['device_id'=>$proDevice,'store'=>'bazaar']);PushDelivery::enqueue();
    check((int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE device_hash=?',[Crypto::hash($proToken)])===1,'Pro-only consent receives push even when ordinary free-user offers were never enabled');
    $proInput['pro_optin']=false;PushDelivery::register($proInput,$premium,['device_id'=>$proDevice,'store'=>'bazaar']);
    $beforeCancel=count($calls);PushDelivery::flush();check(count($calls)===$beforeCancel,'revoking Pro consent cancels unsent notification without contacting provider');
    check((int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE device_hash=?',[Crypto::hash($proToken)])===0,'canceled unsent queue record does not permanently suppress future opt-in');
    $proInput['pro_optin']=true;PushDelivery::register($proInput,$premium,['device_id'=>$proDevice,'store'=>'bazaar']);PushDelivery::enqueue();
    check((int)Db::value('SELECT COUNT(*) FROM {push_deliveries} WHERE device_hash=?',[Crypto::hash($proToken)])===1,'re-enabling Pro consent can enqueue an eligible unseen campaign again');
    $input['offers']=false;PushDelivery::register($input,null,null);check((int)Db::value('SELECT offers FROM {push_devices} WHERE token_hash=?',[Crypto::hash($token)])===0,'revoked consent persisted');
    $bad=$input;$bad['device']=hash('sha256','other-phone');throws(fn()=>PushDelivery::register($bad,['id'=>1],['device_id'=>$device,'store'=>'bazaar']),'push_device','signed-in token binding must match authenticated device');
    Settings::set('android_update_direct', AppUpdates::fromForm(['version_code'=>'34','version_name'=>'5.6.7']));
    check(PushDelivery::sendAppUpdates()===1 && PushDelivery::sendAppUpdates()===0, 'one update wake-up per policy');
    Settings::set('android_update_direct', AppUpdates::fromForm(['version_code'=>'34','version_name'=>'5.6.7','required'=>'1']));
    check(PushDelivery::sendAppUpdates()===1, 'changing enforcement wakes the same update channel');
    PushDelivery::$testTransport=null;Settings::set('push_fcm',$old);Settings::setSecret('push_service_account',$oldSecret);Settings::setSecret('push_access_token',$oldAccess);Settings::set('notices_enabled',$enabled);
});
