<?php
/** Added to the real-database suite; no fixture revenue is presented by production code. */
scenario('Iran ads: additive upgrade retains previous users, gifts and purchases',function(){
    $tables=['users','user_grants','purchases','user_backups'];$before=[];foreach($tables as $t)$before[$t]=(int)Db::value('SELECT COUNT(*) FROM {'.$t.'}');
    Migrations::run();foreach($tables as $t)check($before[$t]===(int)Db::value('SELECT COUNT(*) FROM {'.$t.'}'),'upgrade retains '.$t);
    check(Monetization::defaults()['rewarded']['amount']===100&&Monetization::defaults()['rewarded']['daily_cap']===5,'100 coins / five rewards defaults');
    check(Monetization::defaults()['interstitial']['hands']===3&&Monetization::defaults()['interstitial']['min_seconds']===180,'three hands / 180 seconds defaults');
});
scenario('Iran ads: wallet idempotency, SDK completion, quotas and server-owned amount',function(){
    [$u]=Users::register('iran_ads_user','VeryStrong_password_904');$device=hash('sha256','ads-first-device');$session=['device_id'=>$device,'store'=>'bazaar'];
    $old=Settings::get('iran_ads');$enabled=Settings::get('ads_enabled');
    Monetization::save(['enabled'=>true,'rewarded'=>['daily_cap'=>2,'min_seconds'=>10],'networks'=>['tapsell'=>['test'=>false,'app_id'=>'publisher_test_fixture','zones'=>['rewarded'=>'fixture_rewarded_zone']]]]);
    $start=['request_id'=>'request_key_00000001','network'=>'tapsell','amount'=>999999];
    $w=Monetization::start($u,$session,$start);check($w['amount']===100,'client cannot choose amount');
    check(Monetization::start($u,$session,$start)['watch_id']===$w['watch_id'],'start is idempotent');
    throws(fn()=>Monetization::claim($u,$session,['watch_id'=>$w['watch_id']]),'incomplete_watch','incomplete ad cannot pay');
    $event=['id'=>'completion_event_0001','network'=>'tapsell','format'=>'rewarded','placement'=>$w['placement'],'event'=>'complete','watch_id'=>$w['watch_id']];
    throws(fn()=>Monetization::events($u,$session,['events'=>[$event]]),'incomplete_watch','immediate forged completion rejected');
    Clock::$fixed+=11;
    check(Monetization::events($u,$session,['events'=>[$event]])['accepted']===1,'completion persisted');
    check(Monetization::events($u,$session,['events'=>[$event]])['accepted']===0,'duplicate event is not counted');
    global $root;$fixture=$root.'/coin-claim.json';file_put_contents($fixture,json_encode(['session'=>$session,'watch_id'=>$w['watch_id']]));$procs=[];
    for($i=0;$i<4;$i++){$pipes=[];$pr=proc_open([PHP_BINARY,__DIR__.'/race_child.php',$root,$fixture,(string)$u['id'],(string)Clock::now(),'coins'],[1=>['pipe','w'],2=>['pipe','w']],$pipes);$procs[]=[$pr,$pipes];}
    $outs=[];foreach($procs as [$pr,$pipes]){$outs[]=json_decode(stream_get_contents($pipes[1]),true);fclose($pipes[1]);fclose($pipes[2]);proc_close($pr);}
    check(count(array_filter($outs,fn($v)=>is_array($v)&&($v['wallet']['balance']??0)===100))===4,'four concurrent claims resolve to one credited balance');
    $paid=Monetization::claim($u,$session,['watch_id'=>$w['watch_id'],'amount'=>999999]);
    check($paid['wallet']['balance']===100,'only configured reward credited');
    check(Monetization::claim($u,$session,['watch_id'=>$w['watch_id']])['duplicate']===true&&Monetization::wallet((int)$u['id'])['balance']===100,'retry after lost response cannot pay twice');
    $w2=Monetization::start($u,$session,['request_id'=>'request_key_00000002','network'=>'tapsell']);
    Monetization::save(['rewarded'=>['amount'=>150]]);Clock::$fixed+=11;$event['id']='completion_event_0002';$event['watch_id']=$w2['watch_id'];Monetization::events($u,$session,['events'=>[$event]]);
    check(Monetization::claim($u,$session,['watch_id'=>$w2['watch_id']])['wallet']['balance']===200,'accepted watch keeps its server amount across config changes');
    check((int)Db::value('SELECT COUNT(*) FROM {coin_transactions} WHERE user_id=?',[$u['id']])===2,'every coin credit has one ledger transaction');
    throws(fn()=>Monetization::start($u,$session,['request_id'=>'request_key_00000003','network'=>'tapsell']),'reward_limit','daily cap enforced');
    [$other]=Users::register('iran_ads_other','VeryStrong_password_905');
    throws(fn()=>Monetization::claim($other,$session,['watch_id'=>$w['watch_id']]),'watch','another account cannot claim the watch');
    throws(fn()=>Monetization::start($other,$session,['request_id'=>'request_key_00000004','network'=>'tapsell']),'reward_limit','same device cannot farm accounts');
    Monetization::save(['enabled'=>false]);
    throws(fn()=>Monetization::start($u,$session,['request_id'=>'request_key_00000005','network'=>'tapsell']),'ads_disabled','panel off prevents a new watch');
    Monetization::save(['enabled'=>true,'banner'=>['pages'=>[]]]);check(Monetization::publicConfig(false)['banner']['pages']===[],'removing every banner page persists');
    $c=Monetization::publicConfig(true);check(!$c['enabled'],'VIP without ads remains exempt');
    throws(fn()=>Monetization::save(['interstitial'=>['min_seconds'=>10]]),'settings','cooldown cannot go below safety floor');
    check(Monetization::stats()['revenue']===null&&Monetization::stats()['revenue_label']==='ناموجود','no estimated revenue claimed as actual');
    Settings::set('iran_ads',$old);Settings::set('ads_enabled',$enabled);
});
scenario('Iran ads: official test completion never mints real coins',function(){
    [$u]=Users::register('iran_ads_demo','VeryStrong_password_906');$s=['device_id'=>hash('sha256','demo-device'),'store'=>'bazaar'];$old=Settings::get('iran_ads');$enabled=Settings::get('ads_enabled');
    Settings::set('iran_ads',Monetization::defaults());Monetization::save(['enabled'=>true]);
    $w=Monetization::start($u,$s,['network'=>'adivery','request_id'=>'demo_request_000001']);Clock::$fixed+=31;
    Monetization::events($u,$s,['events'=>[['id'=>'demo_complete_00001','network'=>'adivery','format'=>'rewarded','placement'=>$w['placement'],'event'=>'complete','watch_id'=>$w['watch_id']]]]);
    $paid=Monetization::claim($u,$s,['watch_id'=>$w['watch_id']]);check($paid['test_reward']&&$paid['wallet']['balance']===0,'test ad has no real reward');
    Settings::set('iran_ads',$old);Settings::set('ads_enabled',$enabled);
});
scenario('native admin: TOTP, same permissions, all panel forms and genuine event inbox',function(){
    $secret=Totp::secret();$name='native_owner';$id=Db::insert('admins',['username'=>$name,'password_hash'=>Crypto::passwordHash('Native_strong_pass_91'),'role'=>'owner','totp_enc'=>Crypto::encrypt($secret),'created_at'=>Clock::sql()]);
    freshIp();$login=AdminMobile::login(['username'=>$name,'password'=>'Native_strong_pass_91']);check(isset($login['challenge'])&&!isset($login['token']),'password alone cannot administer');
    throws(fn()=>AdminMobile::verify(['challenge'=>$login['challenge'],'code'=>'000000']),'totp','bad TOTP rejected');
    $session=AdminMobile::verify(['challenge'=>$login['challenge'],'code'=>Totp::code($secret,intdiv(Clock::now(),30))]);
    throws(fn()=>AdminMobile::verify(['challenge'=>$login['challenge'],'code'=>Totp::code($secret,intdiv(Clock::now(),30))]),'totp','challenge and TOTP cannot replay');
    $previous=$_SERVER['HTTP_AUTHORIZATION']??null;$_SERVER['HTTP_AUTHORIZATION']='Bearer '.$session['token'];
    $menu=NativePanel::menu();check(count($menu['items'])>=20,'native menu covers all management sections');
    foreach(['plans','features','gifts','ads','placements','notices','site','stores','paymethods','backups','admins','account','iranads'] as $page){$doc=NativePanel::handle(['query'=>'p='.$page,'verb'=>'GET']);check(count($doc['nodes'])>0,'native widgets generated from real '.$page.' controller');}
    $doc=NativePanel::document('<main><form method="post"><label>تعداد</label><input name="data[amount]" value="100"><input type="checkbox" name="pages[]" value="home" checked><button name="action" value="save">ذخیره</button></form></main>');
    check($doc[0]['fields'][0]['name']==='data[amount]'&&$doc[0]['fields'][1]['checked']&&$doc[0]['buttons'][0]['value']==='save','typed names, arrays and submit actions preserved');
    $admin=AdminMobile::auth('dashboard');$events=AdminInbox::list($admin);check(count($events['items'])>=3,'real registrations appear in inbox');
    $reg=(int)Db::value('SELECT COUNT(*) FROM {admin_inbox} WHERE source_key=?',['registration:'.Users::byName('iran_ads_user')['id']]);AdminInbox::registration((int)Users::byName('iran_ads_user')['id']);check($reg===1&&(int)Db::value('SELECT COUNT(*) FROM {admin_inbox} WHERE source_key=?',['registration:'.Users::byName('iran_ads_user')['id']])===1,'registration event is not duplicated');
    $purchase=Db::one("SELECT id FROM {purchases} WHERE status='verified' LIMIT 1");if($purchase!==null){AdminInbox::purchase((int)$purchase['id']);AdminInbox::purchase((int)$purchase['id']);check((int)Db::value('SELECT COUNT(*) FROM {admin_inbox} WHERE source_key=?',['purchase:'.$purchase['id']])===1,'verified purchase notification is idempotent');}
    $customer=Users::byName('iran_ads_user');$userToken=Sessions::create((int)$customer['id'],['device_id'=>hash('sha256','ordinary-admin-test'),'store'=>'bazaar']);$_SERVER['HTTP_AUTHORIZATION']='Bearer '.$userToken;
    throws(fn()=>AdminMobile::handle('admin/ads/settings','PUT',['enabled'=>true]),'admin_unauthorized','ordinary users cannot change ad settings');
    $_SERVER['HTTP_AUTHORIZATION']='Bearer '.$session['token'];Db::update('admins',['role'=>'analyst'],'id=?',[$id]);
    throws(fn()=>NativePanel::handle(['query'=>'p=plans','verb'=>'POST','fields'=>[]]),'admin_forbidden','read-only analyst cannot submit native panel forms');
    Db::update('admins',['role'=>'owner'],'id=?',[$id]);AdminMobile::handle('admin/mobile/logout','POST',[]);
    throws(fn()=>AdminMobile::auth('dashboard'),'admin_unauthorized','logout revokes server token');
    if($previous===null)unset($_SERVER['HTTP_AUTHORIZATION']);else $_SERVER['HTTP_AUTHORIZATION']=$previous;
});
