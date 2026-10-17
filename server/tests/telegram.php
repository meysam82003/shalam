<?php
/** Included by run.php: real MariaDB, fake Telegram transport. Never reads production config or calls Telegram. */
scenario('telegram: migrations repeat without data loss and encrypted settings',function(){
    $count=(int)Db::value('SELECT COUNT(*) FROM {users}');Migrations::run();Migrations::run();check((int)Db::value('SELECT COUNT(*) FROM {users}')===$count,'migration never deletes existing accounts');
    Settings::setSecret('tg_bot_token','123456:abcdefghijklmnopqrstuvwxyz_123456789');
    check(Settings::secret('tg_bot_token')==='123456:abcdefghijklmnopqrstuvwxyz_123456789','encrypted bot token roundtrip');
    check(strpos((string)Db::value('SELECT v FROM {settings} WHERE k=?',['tg_bot_token']),'abcdefghijklmnopqrstuvwxyz')===false,'token is not stored in plaintext');
    Settings::set('telegram',['bot_enabled'=>true,'app_enabled'=>true,'paused'=>false,'app_url'=>App::baseUrl().'/tg/app/']);
});
function tgSigned(string $id,string $nonce='a',?int $when=null): string {
    $fields=['user'=>json_encode(['id'=>(int)$id,'first_name'=>'آزمایشی']), 'auth_date'=>(string)($when??Clock::now()),'query_id'=>$nonce];ksort($fields);$lines=[];foreach($fields as $k=>$v)$lines[]=$k.'='.$v;
    $fields['hash']=hash_hmac('sha256',implode("\n",$lines),hash_hmac('sha256',TgConfig::token(),'WebAppData',true));return http_build_query($fields);
}
function tgAdmin(string $role,string $webRole,string $tid): array {
    $a=Db::insert('admins',['username'=>'tg_'.$role.'_'.substr($tid,-4),'password_hash'=>Crypto::passwordHash('Test-secret-123'),'role'=>$webRole,'active'=>1,'totp_enc'=>Crypto::encrypt(Totp::secret()),'created_at'=>Clock::sql()]);
    $code=TgAuth::issueLink($a,$tid,$role);$m=TgAuth::link($code,$tid);$signed=tgSigned($tid,bin2hex(random_bytes(8)));$session=TgAuth::exchange($signed);$_SERVER['HTTP_AUTHORIZATION']='Bearer '.$session['token'];return TgAuth::current()+['test_token'=>$session['token'],'test_init'=>$signed];
}
$owner=null;$support=null;$analyst=null;$finance=null;$content=null;
scenario('telegram: official HMAC validation and parsing attacks',function(){
    $raw=tgSigned('1234567890123');check(TgAuth::validate($raw,TgConfig::token())['telegram_id']==='1234567890123','52-bit Telegram id retained');
    throws(fn()=>TgAuth::validate(str_replace('query_id=a','query_id=b',$raw),TgConfig::token()),'init_data','tampered signature rejected');
    throws(fn()=>TgAuth::validate($raw.'&user=%7B%22id%22%3A7%7D',TgConfig::token()),'init_data','duplicate parameter rejected');
    throws(fn()=>TgAuth::validate($raw.'&%75ser=%7B%7D',TgConfig::token()),'init_data','encoded duplicate rejected');
    throws(fn()=>TgAuth::validate(tgSigned('123','expired',Clock::now()-301),TgConfig::token()),'expired','expired signed input rejected');
    throws(fn()=>TgAuth::validate(tgSigned('123','future',Clock::now()+31),TgConfig::token()),'expired','future auth_date rejected');
    throws(fn()=>TgAuth::validate($raw,''),'init_data','empty bot token never authenticates');
    throws(fn()=>TgAuth::exchange(tgSigned('55555555','outsider')),'forbidden','ordinary Telegram account denied');
    check(jdate('2026-10-17 21:10:00')===TgEvents::date('2026-10-17 21:10:00'),'existing Persian calendar reused');
});
scenario('telegram: owner pairing, automatic passwordless login and replay',function()use(&$owner,&$support,&$analyst,&$finance,&$content){
    $owner=tgAdmin('owner','owner','700000001');$support=tgAdmin('support','support','700000002');$analyst=tgAdmin('analyst','analyst','700000003');$finance=tgAdmin('finance','manager','700000004');$content=tgAdmin('content','manager','700000005');
    check($owner['role']==='owner' && !empty($owner['session_id']),'owner has real linked session');
    throws(fn()=>TgAuth::exchange($owner['test_init']),'replay','same signed initData cannot open a second session');
    $code=TgAuth::issueLink((int)$support['admin_id'],'700000002','support');throws(fn()=>TgAuth::link($code,'700000088'),'link','one-time link is bound to numeric Telegram id');$m=TgAuth::link($code,'700000002');
    throws(fn()=>TgAuth::link($code,'700000002'),'link','one-time pairing cannot be reused');
    $_SERVER['HTTP_AUTHORIZATION']='Bearer '.$support['test_token'];throws(fn()=>TgAuth::current(),'unauthorized','relink revokes old sessions');
    $s=TgAuth::exchange(tgSigned('700000002','support-refresh'));$_SERVER['HTTP_AUTHORIZATION']='Bearer '.$s['token'];$support=TgAuth::current();
    $code=TgAuth::issueLink((int)$support['admin_id'],'700000002','support');$time=Clock::$fixed;Clock::$fixed+=301;throws(fn()=>TgAuth::link($code,'700000002'),'link','expired pairing denied');Clock::$fixed=$time;
    throws(fn()=>TgAuth::issueLink((int)$support['admin_id'],'700000002','owner'),'role','web support cannot be paired as owner');
});
scenario('telegram: role matrix and web account ceiling',function()use(&$owner,&$support,&$analyst,&$finance,&$content){
    foreach(TgPolicy::PERMISSIONS as $p)check(TgPolicy::can($owner,$p),'owner permission '.$p);
    check(TgPolicy::can($support,'users.write')&&!TgPolicy::can($support,'pro.write'),'support cannot grant Pro');
    check(TgPolicy::can($finance,'plans.write')&&!TgPolicy::can($finance,'users.write'),'finance cannot mutate accounts');
    check(TgPolicy::can($content,'content.write')&&!TgPolicy::can($content,'sales.read'),'content cannot see finance');
    foreach(['users.write','pro.write','plans.write','gateways.write','content.write','support.write','telegram.manage'] as $p)check(!TgPolicy::can($analyst,$p),'analyst read-only '.$p);
    $fake=$owner;$fake['admin_role']='support';check(!TgPolicy::can($fake,'pro.write')&&!TgPolicy::can($fake,'telegram.manage'),'Telegram role cannot exceed web role');
    $fake=$owner;$fake['permissions']='["dashboard"]';check(!TgPolicy::can($fake,'users.read'),'per-user permissions narrow role');
    throws(fn()=>TgRead::list($content,'users'),'forbidden','read API permission checked');throws(fn()=>TgRead::gateways($content),'forbidden','financial settings hidden from content');
    throws(fn()=>TgRead::detail($analyst,'user',1),'forbidden','analyst cannot fetch personal profile');
});
$tgUser=null;
scenario('telegram: transactional events and no fabricated financial changes',function()use(&$owner,&$support,&$tgUser){
    freshIp();[$tgUser]=Users::register('tg_customer','RandomStrong_177');$id=(int)$tgUser['id'];
    check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='registered' AND entity_id=?",[$id])===1,'registration emits a real event');
    TgEvents::emit('registered','user',$id,'user:'.$id);check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='registered' AND entity_id=?",[$id])===1,'event key prevents duplicates');
    $before=(int)Db::value('SELECT COUNT(*) FROM {tg_events}');try{Db::tx(function(){TgEvents::emit('ticket_created','support',9999,'rollback-ticket');throw new RuntimeException('rollback');});}catch(RuntimeException $e){}
    check((int)Db::value('SELECT COUNT(*) FROM {tg_events}')===$before,'event rolls back with business transaction');
    $data=['id'=>$id,'days'=>5,'reason'=>'هدیه آزمایشی'];$key='same_request_00000001';$n=(int)Db::value('SELECT COUNT(*) FROM {user_grants} WHERE user_id=?',[$id]);
    $r=TgApi::mutate($owner,'user.gift_pro',$data,$key,'',true);$r2=TgApi::mutate($owner,'user.gift_pro',$data,$key,'',true);
    check($r===$r2 && (int)Db::value('SELECT COUNT(*) FROM {user_grants} WHERE user_id=?',[$id])===$n+1,'authorized grant needs no password/TOTP and request id executes once');
    check(Entitlements::summary($id)['pro'],'gift activates existing Pro logic');check((int)Db::value('SELECT COUNT(*) FROM {purchases} WHERE user_id=?',[$id])===0,'gift never creates paid purchase');
    throws(fn()=>TgApi::mutate($owner,'user.gift_pro',array_merge($data,['days'=>6]),$key,'',true),'conflict','same key cannot change request');
    throws(fn()=>TgApi::mutate($support,'user.gift_pro',$data,'support_denied_1234','',true),'forbidden','support grant denied server-side');
    throws(fn()=>TgApi::mutate($owner,'purchase.paid',$data,'forged_payment_123','',true),'action','no mark-paid endpoint');
    throws(fn()=>TgApi::mutate($owner,'order.refund',$data,'forged_refund_1234','',true),'action','no refund endpoint');
    throws(fn()=>TgApi::mutate($owner,'user.disable',$data,'no_explicit_12345','',false),'confirmation','execution must be intentional form submit');
    $json=json_encode(TgRead::detail($owner,'user',$id));foreach(['password_hash','recovery_hash','token_hash','token_enc','raw_enc','activation_enc','totp_enc'] as $secret)check(strpos($json,$secret)===false,'profile excludes '.$secret);
});
scenario('telegram: shared media and placement validation',function()use(&$owner){
    $png=App::$root.'/fixture.png';$img=imagecreatetruecolor(16,16);imagepng($img,$png);imagedestroy($img);$mid=Media::store(['tmp_name'=>$png,'name'=>'safe.png','error'=>0]);
    $d=['name'=>'تلگرام آگهی','format'=>'image','media_id'=>$mid,'placements'=>['home_inline'],'stores'=>['bazaar'],'active'=>1,'reason'=>'آگهی آزمایشی'];
    $a=Management::saveAd($d);check($a>0,'real ad writes existing table');
    throws(fn()=>Management::saveAd($d+['target_url'=>'javascript:alert(1)']),'url','XSS URL refused');
    throws(fn()=>Management::saveAd(array_merge($d,['placements'=>['between_games']])),'placements','incompatible placement refused');
    throws(fn()=>Management::saveAd(array_merge($d,['format'=>'rewarded','placements'=>['rewarded_offer']])),'media','rewarded requires MP4');
    throws(fn()=>Management::saveAd(array_merge($d,['starts_at'=>'2026-02-30 00:00:00'])),'date','impossible calendar date refused');
    throws(fn()=>Management::saveAd(array_merge($d,['starts_at'=>'2026-10-20 00:00:00','ends_at'=>'2026-10-19 00:00:00'])),'date','reversed schedule refused');
    throws(fn()=>Management::saveAd(array_merge($d,['daily_cap'=>-1])),'number','negative cap refused');
    throws(fn()=>Media::delete($mid),'in_use','shared Media refuses deleting attached file');
    $file=App::$root.'/attack.php';file_put_contents($file,'<?php echo 1;');throws(fn()=>Media::store(['tmp_name'=>$file,'name'=>'photo.jpg','error'=>0]),'upload_type','executable disguised as image rejected');
    $gift=['kind'=>'discount','percent'=>99,'reason'=>'آزمایش','active'=>1];throws(fn()=>Management::savePromo((int)$owner['admin_id'],$gift),'number','discount capped to existing 90 percent');
    $gift['percent']=10;$pid=Management::savePromo((int)$owner['admin_id'],$gift);check($pid>0,'discount uses existing promo table');
    $n=Management::perform((int)$owner['admin_id'],'notice.save',['title'=>'اعلان آزمون','body'=>'محتوا','media_id'=>$mid,'stores'=>['bazaar'],'audience'=>'free','active'=>1,'reason'=>'اعلان آزمایشی']);
    Db::run('DELETE FROM {ads} WHERE id=?',[$a]);throws(fn()=>Media::delete($mid),'in_use','media referenced by notice also protected in web service');
});
scenario('telegram: actual purchase verification event and sales totals',function()use(&$tgUser,$storeKey,$bazaar,$owner){
    Settings::set('bazaar',['enabled'=>true,'package'=>'com.meysam.divanemtiaz','public_key'=>preg_replace('/-----[^-]+-----|\s/','',openssl_pkey_get_details($storeKey)['key'])]);
    Settings::set('bazaar_dynamic_key','');syncPrices();freshIp();$p=buy($tgUser,'m1','bazaar','tg_purchase_token_1',$storeKey);
    $pid=$p['purchase']['id']??$p['id']??Db::value('SELECT MAX(id) FROM {purchases} WHERE user_id=?',[$tgUser['id']]);
    check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='purchase_success' AND entity='purchase' AND entity_id=?",[$pid])===1,'verified purchase emits once inside transaction');
    TgEvents::emit('purchase_success','purchase',$pid,'purchase:'.$pid);check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='purchase_success' AND entity_id=? AND entity='purchase'",[$pid])===1,'repeated verification notification deduplicated');
    $d=TgRead::dashboard($owner);$expected=(int)Db::value("SELECT COALESCE(SUM(amount_toman),0) FROM {purchases} WHERE status='verified' AND store IN ('bazaar','myket')")+(int)Db::value("SELECT COALESCE(SUM(amount_toman),0) FROM {pay_orders} WHERE status='paid'");check((int)$d['sales']['total']===$expected,'direct paid orders not counted twice');
    foreach(['transactions','orders','users','subscriptions','plans','promos','campaigns','ads','media','notices','support','audit','security','ad_stats','backups'] as $r){$v=TgRead::list($owner,$r);check(isset($v['rows'],$v['total']),'resource query works: '.$r);$j=json_encode($v);foreach(['token_enc','raw_enc','recovery_hash','password_hash'] as $k)check(strpos($j,'"'.$k.'"')===false,$r.' omits '.$k);}
});
scenario('telegram: support creation and internal note only',function()use(&$owner,&$tgUser){
    freshIp();$id=Support::create((int)$tgUser['id'],['message'=>'این یک درخواست پشتیبانی آزمایشی است.','kind'=>'purchase']);
    check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='ticket_created' AND entity_id=?",[$id])===1,'ticket event stored');
    $r=TgApi::mutate($owner,'ticket.save',['id'=>$id,'status'=>'in_review','priority'=>'high','reason'=>'پیگیری داخلی آزمایشی'],'ticket_internal_123','',true);
    check($r['delivery']==='internal_only','no false claim of reply delivery');$d=TgRead::detail($owner,'ticket',$id);check(!$d['reply_supported'] && $d['meta']['priority']==='high','priority and unsupported reply explicit');
});
scenario('telegram: forged callbacks, private chat, update deduplication',function()use(&$owner){
    Db::run('DELETE FROM {tg_outbox}');$message=['date'=>Clock::now(),'chat'=>['id'=>700000001,'type'=>'private'],'from'=>['id'=>700000001],'text'=>'/dashboard'];$u=['update_id'=>99001,'message'=>$message];
    TgBot::handle($u);TgBot::handle($u);check((int)Db::value('SELECT COUNT(*) FROM {tg_outbox}')===1,'webhook update enqueues once');
    $before=(int)Db::value('SELECT COUNT(*) FROM {user_grants}');TgBot::handle(['update_id'=>99002,'callback_query'=>['id'=>'forged','from'=>['id'=>700000001],'message'=>$message,'data'=>'user.gift_pro:1:life']]);check((int)Db::value('SELECT COUNT(*) FROM {user_grants}')===$before,'forged callback cannot grant Pro');
    $g=$message;$g['chat']=['id'=>-1001,'type'=>'supergroup'];TgBot::handle(['update_id'=>99003,'message'=>$g]);check((int)Db::value('SELECT COUNT(*) FROM {tg_outbox}')===1,'no group disclosure');
    $g=$message;$g['from']['id']=700000099;TgBot::handle(['update_id'=>99004,'message'=>$g]);check((int)Db::value('SELECT COUNT(*) FROM {tg_outbox}')===1,'mismatched sender/chat ignored');
});
scenario('telegram: queue success, revocation, retry, uncertain delivery, dead letter',function()use(&$owner){
    Db::run('DELETE FROM {tg_outbox}');$sent=[];TgClient::$testTransport=function($method,$data)use(&$sent){$sent[]=$data;return ['ok'=>true,'result'=>['message_id'=>11]];};
    TgQueue::message($owner,['text'=>'آزمون صف']);$r=TgQueue::run();check($r['sent']===1 && count($sent)===1,'worker sends through fake transport');check(!empty($sent[0]['protect_content']),'sensitive chat content protected');check(Db::value('SELECT payload_enc FROM {tg_outbox} LIMIT 1')===null,'delivered command payload erased');
    TgQueue::run();check(count($sent)===1,'delivered item not resent');
    Clock::$fixed+=5;TgQueue::message($owner,['text'=>'دسترسی لغوشده']);Db::run('UPDATE {tg_members} SET epoch=epoch+1 WHERE id=?',[$owner['id']]);$r=TgQueue::run();check($r['skipped']===1&&count($sent)===1,'revoke checked again before send');Db::run('UPDATE {tg_members} SET epoch=epoch-1 WHERE id=?',[$owner['id']]);
    Clock::$fixed+=5;TgClient::$testTransport=fn()=>['ok'=>false,'class'=>'retry','error'=>'telegram_http_429_curl_0','retry_after'=>90];TgQueue::message($owner,['text'=>'محدودیت']);$r=TgQueue::run();check($r['retry']===1,'429 queued for bounded retry');$row=Db::one("SELECT * FROM {tg_outbox} WHERE state='pending' ORDER BY id DESC LIMIT 1");check(Clock::ts($row['available_at'])>=Clock::now()+90,'respects retry_after');
    Clock::$fixed+=95;Db::update('tg_outbox',['attempts'=>5],'id=?',[$row['id']]);$r=TgQueue::run();check($r['dead']===1,'retry maximum enforced');
    Clock::$fixed+=5;TgClient::$testTransport=fn()=>['ok'=>false,'class'=>'uncertain','error'=>'telegram_http_0_curl_28'];TgQueue::message($owner,['text'=>'ابهام']);$r=TgQueue::run();check($r['uncertain']===1,'timeout retained as uncertain, not silently retried');
    Clock::$fixed+=5;Db::insert('tg_outbox',['member_id'=>$owner['id'],'epoch'=>$owner['epoch'],'state'=>'sending','locked_at'=>Clock::sql(Clock::now()-180),'available_at'=>Clock::sql(),'created_at'=>Clock::sql()]);TgQueue::run();check((int)Db::value("SELECT COUNT(*) FROM {tg_outbox} WHERE state='uncertain'")===2,'interrupted send becomes uncertain');
    TgClient::$testTransport=null;
});
scenario('telegram: pause, notification rules, scheduler and revocation',function()use(&$owner,&$tgUser){
    $before=(int)Db::value('SELECT COUNT(*) FROM {tg_events}');TgEvents::saveRule(['type'=>'registered','enabled'=>0,'priority'=>1,'hourly_cap'=>5]);TgEvents::emit('registered','user',989898,'disabled-rule');check((int)Db::value('SELECT COUNT(*) FROM {tg_events}')===$before,'per-type off switch works');
    $cfg=TgConfig::get();Settings::set('telegram',array_merge($cfg,['paused'=>true]));$n=(int)Db::value("SELECT COUNT(*) FROM {tg_outbox} WHERE state='sent'");TgQueue::run();check((int)Db::value("SELECT COUNT(*) FROM {tg_outbox} WHERE state='sent'")===$n,'pause blocks all deliveries');Settings::set('telegram',$cfg);
    Settings::set('tg_scheduler_heartbeat',Clock::now()-2000);TgQueue::schedule();check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='cron_gap'")>=1,'cron gap observed on recovery');
    $_SERVER['HTTP_AUTHORIZATION']='Bearer '.$owner['test_token'];Db::update('admins',['active'=>0],'id=?',[$owner['admin_id']]);throws(fn()=>TgAuth::current(),'unauthorized','web admin revocation immediately blocks Telegram');Db::update('admins',['active'=>1],'id=?',[$owner['admin_id']]);
    Settings::set('telegram',array_merge($cfg,['app_enabled'=>false]));throws(fn()=>TgAuth::current(),'disabled','global Mini App kill switch');Settings::set('telegram',$cfg);
});
scenario('telegram: local SEO scan and path protection',function()use(&$owner){
    $root=App::$root.'/site-test';mkdir($root);App::$config['test_site_root']=$root;
    file_put_contents($root.'/index.html','<!doctype html><html><head><title>قبل</title><meta name="description" content="قبلی"></head><body><main>متن نمونه</main><a href="missing.html">لینک</a></body></html>');file_put_contents($root.'/robots.txt','User-agent: *');
    $scan=TgSeo::scan();check(count($scan['broken'])===1,'local broken link reported');check(TgSeo::file('../storage/config.php')===null,'path traversal refused');
    TgSeo::save(['page'=>'index.html','title'=>'عنوان جدید','description'=>'متن <script> امن']);$html=file_get_contents($root.'/index.html');check(strpos($html,'<title>عنوان جدید</title>')!==false,'title saved in actual static page');check(strpos($html,'&lt;script&gt;')!==false,'SEO metadata escaped');check(count(glob(App::$root.'/storage/tg-site-backups/*'))===1,'original page backed up');
    unset(App::$config['test_site_root']);
});
scenario('telegram: delivery uses current web role and digest respects preferences',function()use(&$owner){
    Db::run('DELETE FROM {tg_outbox}');$sent=[];TgClient::$testTransport=function($method,$data)use(&$sent){$sent[]=$data;return ['ok'=>true,'result'=>['message_id'=>12]];};
    TgQueue::message($owner,TgBot::summary($owner,'dashboard'));Db::update('admins',['role'=>'analyst'],'id=?',[$owner['admin_id']]);Db::update('tg_members',['permissions'=>'["dashboard","content.read"]'],'id=?',[$owner['id']]);Clock::$fixed+=5;TgQueue::run();
    check(count($sent)===1 && strpos($sent[0]['text'],'فروش')===false && strpos($sent[0]['text'],'کل کاربران')===false,'summary rebuilt after permissions downgrade');
    Db::update('admins',['role'=>'owner'],'id=?',[$owner['admin_id']]);Db::update('tg_members',['permissions'=>null],'id=?',[$owner['id']]);
    $hour=(int)gmdate('G',Clock::now()+12600);Db::update('tg_members',['digest'=>'daily','quiet_from'=>$hour,'quiet_to'=>($hour+1)%24],'id=?',[$owner['id']]);
    TgEvents::emit('digest','digest',$owner['id'],'test-digest-quiet');Clock::$fixed+=5;TgQueue::run();check(count($sent)===1,'digest obeys quiet hours');
    check((int)Db::value("SELECT COUNT(*) FROM {tg_outbox} WHERE event_id IS NOT NULL")===1,'personal digest targets one member');
    Db::update('tg_members',['digest'=>'off','quiet_from'=>null,'quiet_to'=>null],'id=?',[$owner['id']]);Clock::$fixed+=305;TgQueue::run();check(count($sent)===1,'disabled digest skipped before delivery');TgClient::$testTransport=null;
});
scenario('telegram: scheduler progresses across batches and expiry keys',function()use(&$owner,&$tgUser){
    $expires=Clock::sql(Clock::now()+100);$id=Db::insert('user_grants',['user_id'=>$tgUser['id'],'feature_k'=>'pro','expires_at'=>$expires,'reason'=>'test schedule','admin_id'=>$owner['admin_id'],'created_at'=>Clock::sql()]);
    TgQueue::schedule();$key=hash('sha256','subscription_expiring|grant:'.$id.':'.$expires);check((int)Db::value('SELECT COUNT(*) FROM {tg_events} WHERE event_key=?',[$key])===1,'grant expiry emits from real table');TgQueue::schedule();check((int)Db::value('SELECT COUNT(*) FROM {tg_events} WHERE event_key=?',[$key])===1,'scheduler SQL dedup matches PHP key');
    $_SERVER['HTTP_AUTHORIZATION']='Bearer '.$owner['test_token'];Db::update('tg_sessions',['expires_at'=>Clock::sql(Clock::now()+30)],'id=?',[$owner['session_id']]);TgAuth::current();check(Clock::ts(Db::value('SELECT expires_at FROM {tg_sessions} WHERE id=?',[$owner['session_id']]))===Clock::now()+1800,'active session renewed without password, code or initData replay');
});

scenario('telegram: global search and CSV export boundary',function()use(&$owner,&$content){
    $found=TgApi::handle($owner,'search','GET',['q'=>'tg_customer']);check(count($found['users'])===1,'global search uses correct resource permission');$limited=TgApi::handle($content,'search','GET',['q'=>'tg_customer']);check(!isset($limited['users'])&&!isset($limited['orders']),'search filters unauthorized resources');
    $before=Db::value('SELECT COUNT(*) FROM {users}');TgRead::list($owner,'users',['q'=>"%' OR 1=1 --"]);check(Db::value('SELECT COUNT(*) FROM {users}')===$before,'search safely binds SQL input');
    throws(fn()=>TgApi::handle($owner,'action','GET',[]),'method','read request cannot mutate');
});

scenario('telegram: owner web settings produce redacted alert',function()use(&$owner){
    $before=(int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='sensitive_changed'");Audit::log('admin',(int)$owner['admin_id'],'telegram_save','telegram',null,'test config change');check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='sensitive_changed'")===$before+1,'web Telegram setting changes emit sensitive alert');
});

scenario('telegram: additive extension preserves purchased time and rejects unauthorized changes',function()use(&$owner,&$support,&$tgUser){
    $id=(int)$tgUser['id'];$before=Entitlements::summary($id);$paid=Db::all('SELECT * FROM {purchases} WHERE user_id=? ORDER BY id',[$id]);$periods=Db::all('SELECT * FROM {entitlements} WHERE user_id=? ORDER BY id',[$id]);
    $data=['id'=>$id,'days'=>7,'reason'=>'تمدید آزمایشی بدون پرداخت'];
    TgApi::mutate($owner,'user.extend_pro',$data,'extension_request_123','',true);
    $e=Db::one("SELECT * FROM {entitlements} WHERE user_id=? AND source='admin_extension' ORDER BY id DESC LIMIT 1",[$id]);
    check(Clock::ts($e['starts_at'])===max(Clock::now(),(int)$before['expires_at']),'extension starts after paid and gift chain');
    check(Clock::ts($e['expires_at'])===Clock::ts($e['starts_at'])+7*86400,'extension adds requested days');
    check($e['purchase_id']===null && $e['plan_code']==='gift','extension is explicitly unpaid');
    check(Db::all('SELECT * FROM {purchases} WHERE user_id=? ORDER BY id',[$id])===$paid,'purchases unchanged by extension');
    foreach($periods as $old)check(Db::one('SELECT * FROM {entitlements} WHERE id=?',[$old['id']])===$old,'existing paid period not rewritten');
    TgApi::mutate($owner,'user.extend_pro',$data,'extension_request_123','',true);
    check((int)Db::value("SELECT COUNT(*) FROM {entitlements} WHERE user_id=? AND source='admin_extension'",[$id])===1,'extension request replay cannot double time');
    throws(fn()=>TgApi::mutate($support,'user.extend_pro',$data,'denied_extension_123','',true),'forbidden','support cannot extend');
    throws(fn()=>TgApi::mutate($owner,'user.extend_pro',array_merge($data,['days'=>0]),'zero_extension_1234','',true),'number','zero duration rejected');
    throws(fn()=>TgApi::mutate($owner,'user.revoke_extension',['id'=>$id,'entitlement'=>$periods[0]['id'],'reason'=>'دستکاری خرید'],'revoke_paid_denied12','',true),'not_found','extension cancellation cannot revoke paid periods');
    TgApi::mutate($owner,'user.revoke_extension',['id'=>$id,'entitlement'=>$e['id'],'reason'=>'لغو تمدید آزمایشی'],'revoke_extension_123','',true);
    check(Entitlements::summary($id)===$before,'canceling extension preserves original access');
    $gift=Management::grant((int)$owner['admin_id'],$id,'pro',null,'آزمایش مادام العمر');
    throws(fn()=>TgApi::mutate($owner,'user.extend_pro',$data,'lifetime_extension12','',true),'lifetime','lifetime cannot be extended');
    Db::update('user_grants',['revoked_at'=>Clock::sql()],'id=?',[$gift]);
    freshIp();[$free]=Users::register('extension_free','Strong-password-123');$eid=Management::extendPro((int)$owner['admin_id'],(int)$free['id'],3,'آزمایش حساب رایگان');
    $f=Db::one('SELECT * FROM {entitlements} WHERE id=?',[$eid]);check($f['starts_at']===Clock::sql() && Entitlements::summary((int)$free['id'])['pro'],'free user extension starts now');
    $next=Management::extendPro((int)$owner['admin_id'],(int)$free['id'],2,'تمدید دوم');check(Db::value('SELECT starts_at FROM {entitlements} WHERE id=?',[$next])===$f['expires_at'],'consecutive extensions stack');
    check((int)Db::value("SELECT COUNT(*) FROM {tg_events} WHERE type='subscription_extended' AND entity_id=?",[$id])===1,'one event for one extension despite replay');
});
scenario('telegram: installation diagnostics never substitute for real acceptance',function(){
    Settings::set('tg_installation_probe',[]);$r=TgReadiness::report();check(!$r['production_approved']&&!$r['checks_passed'],'missing host evidence is not approved');
    $calls=[];TgClient::$testTransport=function($method,$params)use(&$calls){$calls[]=$method;
        if($method==='getMe')return ['ok'=>true,'result'=>['is_bot'=>true,'username'=>'test_bot','secret_echo'=>'do-not-output']];
        if($method==='getWebhookInfo')return ['ok'=>true,'result'=>['url'=>App::baseUrl().'/tg/webhook.php','pending_update_count'=>0,'last_error_message'=>'do-not-output']];
        return ['ok'=>true,'result'=>['type'=>'web_app','web_app'=>['url'=>TgConfig::get()['app_url']]]];};
    $r=TgReadiness::probe();$checks=array_column($r['checks'],null,'key');check($checks['connection']['ok']&&$checks['webhook']['ok']&&$checks['menu']['ok'],'real probe checks identity, URL and menu using transport');
    check($calls===['getMe','getWebhookInfo','getChatMenuButton'],'diagnostic makes only read-only Telegram calls');
    check(strpos(json_encode(Settings::get('tg_installation_probe')),'do-not-output')===false && strpos(json_encode($r),TgConfig::token())===false,'diagnostic contains neither raw remote bodies nor token');
    check(!$r['production_approved'],'successful runtime checks cannot approve production');
    $probe=Settings::get('tg_installation_probe');$probe['at']=Clock::now()-901;Settings::set('tg_installation_probe',$probe);$checks=array_column(TgReadiness::report()['checks'],null,'key');check(!$checks['connection']['ok'],'stale connection result requires retest');
    $probe['at']=Clock::now();$probe['webhook_ok']=false;$probe['webhook_last_error']=Clock::now();Settings::set('tg_installation_probe',$probe);$checks=array_column(TgReadiness::report()['checks'],null,'key');check(!$checks['webhook']['ok']&&!$checks['webhook_error']['ok'],'wrong webhook and recent error block readiness');
    TgClient::$testTransport=null;Settings::set('tg_installation_probe',[]);
});
