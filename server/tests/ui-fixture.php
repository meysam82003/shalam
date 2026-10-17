<?php
declare(strict_types=1);
// Test-only fixture. Loopback DB and fixed test prefix; refuses non-test environments.
if(PHP_SAPI!=='cli' || !str_contains((string)getenv('DIVAN_TEST_DSN'),'host=127.0.0.1;'))exit(1);
define('DIVAN_TEST',true);require dirname(__DIR__).'/public/app/bootstrap.php';
$root=$argv[1];if(!is_dir($root))mkdir($root,0700,true);foreach(['storage','storage/keys','storage/backups','media']as$d)if(!is_dir($root.'/'.$d))mkdir($root.'/'.$d,0700,true);
App::$root=$root;App::$config=['db_prefix'=>'dvtui_','app_key'=>base64_encode(random_bytes(32)),'base_url'=>'https://127.0.0.1:9443/divan','backup_key'=>base64_encode(random_bytes(32)),'db_name'=>'divan_test2'];
$pdo=new PDO('mysql:'.getenv('DIVAN_TEST_DSN').';charset=utf8mb4',getenv('DIVAN_TEST_USER'),(string)getenv('DIVAN_TEST_PASS'),[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC,PDO::ATTR_EMULATE_PREPARES=>false]);Db::use($pdo,'dvtui_');
foreach($pdo->query("SHOW TABLES LIKE 'dvtui\\_%'")->fetchAll(PDO::FETCH_COLUMN)as$t)$pdo->exec("DROP TABLE `$t`");Migrations::run();
file_put_contents($root.'/storage/config.php','<?php return '.var_export(App::$config,true).';');file_put_contents($root.'/storage/install.lock','test');
Settings::setSecret('tg_bot_token','123456:abcdefghijklmnopqrstuvwxyz_123456789');Settings::set('telegram',['bot_enabled'=>true,'app_enabled'=>true,'paused'=>true,'app_url'=>App::baseUrl().'/tg/app/']);
$aid=Db::insert('admins',['username'=>'test_owner','password_hash'=>Crypto::passwordHash('TestOnly-123'),'role'=>'owner','active'=>1,'created_at'=>Clock::sql()]);
TgAuth::link(TgAuth::issueLink($aid,'700000001','owner'),'700000001');
$image=imagecreatetruecolor(16,16);imagepng($image,$root.'/upload.png');imagedestroy($image);
$hash=Crypto::passwordHash('Example-Pass-123');
for($i=1;$i<=18;$i++){Db::insert('users',['username'=>'test_user_'.$i,'username_norm'=>'test_user_'.$i,'password_hash'=>$hash,'status'=>'active','created_at'=>Clock::sql(Clock::now()-$i*36000),'updated_at'=>Clock::sql(),'last_login_at'=>Clock::sql()]);}
Management::grant($aid,1,'pro',30,'هدیه در دیتابیس تست');Management::grant($aid,2,'pro',null,'هدیه دائمی در دیتابیس تست');
for($i=1;$i<=8;$i++){Db::insert('pay_orders',['code'=>str_pad((string)$i,20,'a',STR_PAD_LEFT),'user_id'=>$i,'plan_id'=>1,'method'=>$i%2?'blupal':'zarinpal','channel'=>'direct','amount_toman'=>19000,'amount_rial'=>190000,'status'=>'paid','created_at'=>Clock::sql(Clock::now()-$i*86400),'updated_at'=>Clock::sql(),'paid_at'=>Clock::sql(Clock::now()-$i*86400)]);}
Support::create(1,['username'=>'test_user_1','message'=>'درخواست آزمایشی برای بررسی رابط پشتیبانی','kind'=>'purchase']);
Management::saveAd(['name'=>'تبلیغ آزمایشی','title'=>'دورهمی با دیوان','body'=>'داده آزمایشی فقط در محیط تست مرورگر','format'=>'text','placements'=>['home_inline'],'stores'=>['bazaar','myket'],'active'=>1]);
$fields=['user'=>json_encode(['id'=>700000001,'first_name'=>'تست']),'auth_date'=>(string)Clock::now(),'query_id'=>Crypto::token(8)];ksort($fields);$lines=[];foreach($fields as$k=>$v)$lines[]=$k.'='.$v;$fields['hash']=hash_hmac('sha256',implode("\n",$lines),hash_hmac('sha256',TgConfig::token(),'WebAppData',true));
file_put_contents($root.'/init.json',json_encode(['initData'=>http_build_query($fields)]));echo "UI fixture ready\n";
