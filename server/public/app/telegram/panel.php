<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class TgPanel
{
    public static function page(): void
    {
        if(!Admin::owner())throw new ApiError('role','مدیریت اتصال تلگرام فقط برای مالک است.',403);
        TgConfig::requireHttps();$once=null;$result=null;
        if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){
            // Existing web login and CSRF are the trust anchor. No extra password/code prompt (owner's explicit choice).
            Admin::checkPost();$action=(string)($_POST['action']??'');$reason=trim((string)($_POST['reason']??''))?:'مدیریت تلگرام از پنل وب';
            switch($action){
                case 'save':
                    $token=trim((string)($_POST['bot_token']??''));if($token!=='' && !preg_match('/^[0-9]{5,20}:[A-Za-z0-9_-]{25,100}$/D',$token))throw new ApiError('token','ساختار توکن ربات معتبر نیست.');
                    if($token!==''){Settings::setSecret('tg_bot_token',$token);Db::run('UPDATE {tg_members} SET epoch=epoch+1');}
                    if(Settings::secret('tg_webhook_secret')==='')Settings::setSecret('tg_webhook_secret',Crypto::token());
                    $cfg=['bot_enabled'=>isset($_POST['bot_enabled']),'app_enabled'=>isset($_POST['app_enabled']),'paused'=>isset($_POST['paused']),'app_url'=>TgConfig::safeUrl(trim((string)($_POST['app_url']??''))),'pending_minutes'=>Management::integer($_POST['pending_minutes']??60,5,10080),'retention_days'=>Management::integer($_POST['retention_days']??30,7,90)];
                    Settings::set('telegram',$cfg);Admin::flash('تنظیمات ذخیره شد. ورود و عملیات تلگرام بدون رمز یا کد مجدد است.');break;
                case 'link':
                    $code=TgAuth::issueLink((int)($_POST['admin_id']??0),trim((string)($_POST['telegram_id']??'')),(string)($_POST['role']??''));
                    $name=(string)Settings::get('tg_bot_username','');$once=['code'=>$code,'url'=>$name!==''?'https://t.me/'.$name.'?start=link_'.$code:null];break;
                case 'revoke':$id=(int)($_POST['member_id']??0);Db::run('UPDATE {tg_members} SET active=0,epoch=epoch+1 WHERE id=?',[$id]);Admin::flash('دسترسی و تمام نشست‌های آن مدیر لغو شد.');break;
                case 'sessions':Db::run('UPDATE {tg_members} SET epoch=epoch+1');Admin::flash('تمام نشست‌ها باطل شدند؛ مدیران مجاز با بازکردن دوباره مینی‌اپ خودکار وارد می‌شوند.');break;
                case 'member':
                    $id=(int)($_POST['member_id']??0);$role=(string)($_POST['role']??'');$m=Management::row('tg_members',$id);$a=Management::row('admins',(int)$m['admin_id']);
                    if(!isset(TgPolicy::ROLES[$role]) || ($role==='owner' && $a['role']!=='owner'))throw new ApiError('role','نقش مالک فقط برای حساب مالک مجاز است.');
                    $perms=array_values(array_intersect((array)($_POST['permissions']??[]),TgPolicy::PERMISSIONS));Db::run('UPDATE {tg_members} SET role=?,permissions=?,epoch=epoch+1 WHERE id=?',[$role,json_encode($perms),$id]);Admin::flash('نقش و مجوزها به‌روز شد.');break;
                case 'test':
                    $r=TgClient::call('getMe');$result=$r['ok']?'ارتباط Telegram API موفق بود.':('ارتباط ناموفق: '.$r['error']);
                    if($r['ok'])Settings::set('tg_bot_username',preg_replace('/[^A-Za-z0-9_]/','',(string)($r['result']['username']??'')));break;
                case 'webhook':
                    if(Settings::secret('tg_webhook_secret')==='')Settings::setSecret('tg_webhook_secret',Crypto::token());
                    $r=TgClient::call('setWebhook',['url'=>App::baseUrl().'/tg/webhook.php','secret_token'=>Settings::secret('tg_webhook_secret'),'max_connections'=>2,'allowed_updates'=>['message','callback_query'],'drop_pending_updates'=>false]);
                    $result=$r['ok']?'Webhook امن ثبت شد.':'ثبت Webhook ناموفق: '.$r['error'];break;
                case 'menu':
                    $r=TgClient::call('setChatMenuButton',['menu_button'=>['type'=>'web_app','text'=>'مدیریت دیوان','web_app'=>['url'=>TgConfig::appUrl()]]]);
                    $commands=[];foreach(TgBot::COMMANDS as $k=>$v)$commands[]=['command'=>$k,'description'=>$v];$c=TgClient::call('setMyCommands',['commands'=>$commands]);$result=($r['ok']&&$c['ok'])?'منوی مینی‌اپ و فرمان‌ها ثبت شدند.':'تنظیم کامل نشد؛ اتصال را بررسی کنید.';break;
                case 'info':
                    $r=TgClient::call('getWebhookInfo');$info=$r['ok']?array_intersect_key($r['result'],array_flip(['url','pending_update_count','last_error_date','max_connections'])):['error'=>$r['error']];Settings::set('tg_webhook_info',$info);$result=json_encode($info,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);break;
                case 'notify':
                    $m=Db::one('SELECT m.*,a.role admin_role,a.username FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id WHERE m.admin_id=? AND m.active=1',[(int)Admin::$me['id']]);if(!$m)throw new ApiError('link','ابتدا حساب خود را متصل کنید.');
                    TgQueue::message($m,['text'=>'پیام آزمایشی اتصال دیوان امتیاز — '.jdate(Clock::sql())]);Admin::flash('پیام آزمایشی در صف قرار گرفت؛ Cron آن را ارسال می‌کند.');break;
                case 'rule':TgEvents::saveRule($_POST);Admin::flash('قانون اعلان ذخیره شد.');break;
                default:throw new ApiError('action','عملیات ناشناخته.');
            }
            Admin::audit('telegram_'.$action,'telegram',null,$reason,null,['action'=>$action]);
        }
        $cfg=TgConfig::get();$members=Db::all('SELECT m.*,a.username,a.role admin_role FROM {tg_members} m JOIN {admins} a ON a.id=m.admin_id ORDER BY m.id');$admins=Db::all('SELECT id,username,role FROM {admins} WHERE active=1');$has=TgConfig::token()!=='';$rules=TgEvents::rules();
        Admin::layout('مدیریت تلگرام',function()use($cfg,$members,$admins,$has,$once,$result,$rules){
            if($result)echo '<div class="alert">'.e($result).'</div>';
            if($once){echo '<div class="card"><h3>اتصال یک‌باره — اعتبار ۵ دقیقه</h3><p>فقط حساب تلگرام با شناسه عددی ثبت‌شده می‌تواند از این پیوند استفاده کند.</p>';if($once['url'])echo '<a class="btn gold" href="'.e($once['url']).'" rel="noreferrer">اتصال حساب در تلگرام</a>';else echo '<p>ابتدا تست ارتباط را بزنید تا نام ربات ثبت شود. در چت خصوصی ربات این فرمان را ارسال کنید:</p><code dir="ltr">/start link_'.e($once['code']).'</code>';echo '</div>';}
            ?><div class="card"><p>ورود و عملیات تلگرام پس از اتصال اولیه، بدون نام کاربری، رمز و کد امنیتی است. مجوزها سمت سرور بررسی می‌شوند. دسترسی به تلگرام باز مدیر، اختیار همان مدیر را می‌دهد.</p>
            <form method="post" autocomplete="off"><?=Admin::csrfField()?><input type="hidden" name="action" value="save">
            <label>Bot Token — <?= $has?'ثبت شده؛ خالی بگذارید تا حفظ شود':'ثبت نشده'?></label><input type="password" name="bot_token" autocomplete="new-password" class="ltr" placeholder="توکن تازه (اختیاری)">
            <label>مسیر Mini App</label><input name="app_url" class="ltr" value="<?=e($cfg['app_url'])?>" required>
            <label class="check"><input type="checkbox" name="bot_enabled" <?=$cfg['bot_enabled']?'checked':''?>> ربات فعال</label>
            <label class="check"><input type="checkbox" name="app_enabled" <?=$cfg['app_enabled']?'checked':''?>> مینی‌اپ فعال</label>
            <label class="check"><input type="checkbox" name="paused" <?=$cfg['paused']?'checked':''?>> توقف ارسال صف</label>
            <label>هشدار پرداخت در انتظار پس از چند دقیقه</label><input type="number" name="pending_minutes" min="5" max="10080" value="<?=e($cfg['pending_minutes'])?>">
            <label>نگهداری تاریخچه ارسال (روز)</label><input type="number" name="retention_days" min="7" max="90" value="<?=e($cfg['retention_days'])?>"><button class="btn gold">ذخیره تنظیمات</button></form></div>
            <div class="card"><h3>راه‌اندازی و آزمون اتصال</h3><div class="row">
            <?php foreach(['test'=>'آزمایش Telegram API','webhook'=>'ثبت Webhook امن','menu'=>'تنظیم دکمه Mini App','info'=>'وضعیت Webhook','notify'=>'پیام آزمایشی به خودم','sessions'=>'لغو همه نشست‌های تلگرام'] as $action=>$label){?><form method="post"><?=Admin::csrfField()?><input type="hidden" name="action" value="<?=e($action)?>"><button class="btn ghost"><?=e($label)?></button></form><?php }?>
            </div><p>وضعیت آخرین ارتباط: <?=e(json_encode(Settings::get('tg_connection'),JSON_UNESCAPED_UNICODE))?></p><p>آخرین Worker: <?=e(Settings::get('tg_worker_heartbeat')?jdate(Clock::sql((int)Settings::get('tg_worker_heartbeat'))):'هنوز اجرا نشده')?></p>
            <p>Cron در cPanel، هر دقیقه:</p><code dir="ltr">* * * * * /usr/local/bin/php <?=e(App::$root)?>/tg/worker.php</code><p>مسیر PHP را مطابق نسخه انتخاب‌شده هاست تعیین کنید. اجرای صف فقط CLI است.</p></div>
            <div class="card"><h3>اتصال مدیر مجاز</h3><form method="post"><?=Admin::csrfField()?><input type="hidden" name="action" value="link"><label>حساب مدیر وب</label><select name="admin_id"><?php foreach($admins as $a)echo '<option value="'.(int)$a['id'].'">'.e($a['username'].' — '.$a['role']).'</option>';?></select><label>شناسه عددی حساب تلگرام (نه نام کاربری)</label><input name="telegram_id" inputmode="numeric" pattern="[1-9][0-9]{0,15}" required><label>نقش تلگرام</label><select name="role"><?php foreach(TgPolicy::ROLES as $k=>$v)echo '<option value="'.e($k).'">'.e($v).'</option>';?></select><button class="btn gold">ساخت پیوند اتصال</button></form><p>نقش تلگرام هرگز از مجوزهای حساب وب فراتر نمی‌رود.</p></div>
            <?php foreach($members as $m){?><div class="card"><h3><?=e($m['username'])?> — <?=e($m['telegram_id'])?> — <?=$m['active']?'فعال':'لغوشده'?></h3><form method="post"><?=Admin::csrfField()?><input type="hidden" name="action" value="member"><input type="hidden" name="member_id" value="<?=(int)$m['id']?>"><select name="role"><?php foreach(TgPolicy::ROLES as $k=>$v)echo '<option value="'.e($k).'" '.($m['role']===$k?'selected':'').'>'.e($v).'</option>';?></select><p>مجوزهای سقف نقش:</p><?php foreach(TgPolicy::PERMISSIONS as $p)echo '<label class="check"><input type="checkbox" name="permissions[]" value="'.e($p).'" '.(TgPolicy::can($m,$p)?'checked':'').'> '.e($p).'</label>';?><button class="btn">ذخیره مجوزها</button></form><form method="post"><?=Admin::csrfField()?><input type="hidden" name="action" value="revoke"><input type="hidden" name="member_id" value="<?=(int)$m['id']?>"><button class="btn red">لغو دسترسی این مدیر</button></form></div><?php }?>
            <div class="card"><h3>قوانین اعلان</h3><?php foreach($rules as $type=>$r){?><details><summary><?=e(TgEvents::TYPES[$type][0])?></summary><form method="post"><?=Admin::csrfField()?><input type="hidden" name="action" value="rule"><input type="hidden" name="type" value="<?=e($type)?>"><label class="check"><input type="checkbox" name="enabled" <?=$r['enabled']?'checked':''?>> فعال</label><label>اولویت ۱ تا ۳</label><input type="number" name="priority" min="1" max="3" value="<?=e($r['priority'])?>"><label>سقف ارسال به هر مدیر در ساعت</label><input type="number" name="hourly_cap" min="1" max="200" value="<?=e($r['hourly_cap'])?>"><p>گیرندگان — خالی یعنی همه مدیران دارای مجوز:</p><?php foreach($members as $m)echo '<label class="check"><input type="checkbox" name="recipients[]" value="'.(int)$m['id'].'" '.(in_array((int)$m['id'],$r['recipients'],true)?'checked':'').'> '.e($m['username']).'</label>';?><button class="btn">ذخیره</button></form></details><?php }?></div>
            <div class="card"><h3>صف و خطاها</h3><pre><?=e(json_encode(Db::all('SELECT state,COUNT(*) count FROM {tg_outbox} GROUP BY state'),JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT))?></pre><p>uncertain یعنی ممکن است پیام رسیده باشد؛ برای جلوگیری از تکرار، خودکار دوباره ارسال نمی‌شود.</p><pre><?=e(json_encode(Db::all("SELECT id,state,attempts,last_error FROM {tg_outbox} WHERE state IN ('dead','uncertain') ORDER BY id DESC LIMIT 20"),JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT))?></pre><h3>تاریخچه مدیریت تلگرام</h3><pre><?=e(json_encode(Db::all("SELECT actor_id,action,created_at FROM {audit_log} WHERE action LIKE 'telegram_%' OR action LIKE 'tg_%' ORDER BY id DESC LIMIT 25"),JSON_UNESCAPED_UNICODE|JSON_PRETTY_PRINT))?></pre></div>
            <?php
        },'telegram');
    }
}
