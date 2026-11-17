<?php
declare(strict_types=1);
if(!defined('DIVAN')){http_response_code(403);exit;}
final class PushPage {
    public static function show():void {
        if(!Admin::owner())throw new ApiError('admin_forbidden','تنظیم کلید خصوصی فقط برای مالک مجاز است.',403);
        if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){
            PushDelivery::save(['enabled'=>isset($_POST['enabled']),'project_id'=>$_POST['project_id']??'','sender_id'=>$_POST['sender_id']??'','api_key'=>$_POST['api_key']??'','apps'=>$_POST['apps']??[],'service_account'=>$_POST['service_account']??'']);
            Admin::audit('push_settings','settings','push_fcm','تغییر تنظیمات Push');Admin::flash('تنظیمات ذخیره شد. کلید خصوصی رمزنگاری شده و دوباره نمایش داده نمی‌شود.');Admin::redirect('p=push');
        }
        Admin::layout('اعلان بدون باز کردن اپ',function(){
            $c=PushDelivery::settings();$stats=PushDelivery::stats();
            echo '<div class="card"><h2>Push واقعی با FCM HTTP v1</h2><p>اعلان در اپ بسته دریافت می‌شود؛ باز شدن برنامه لازم نیست. حساب Firebase، کلید Service Account، اینترنت Google Play Services و دسترسی خروجی هاست به Google لازم است. در حالت Force stop اندروید تا باز شدن دوبارهٔ برنامه تحویل را متوقف می‌کند.</p><p>در cPanel این فایل را هر دقیقه اجرا کنید: <code>php /home/USER/public_html/divan/push-cron.php</code>. بررسی دوره‌ای اندروید نیز به‌عنوان پشتیبان باقی می‌ماند.</p><p>پروژه: '.e($c['project_id']?:'در انتظار تنظیمات ناشر').' — کلید خصوصی: '.(Settings::secret('push_service_account')!==''?'ثبت شده':'ثبت نشده').'</p><p>دستگاه‌های مجاز: '.$stats['devices'].' — تحویل به سرویس: '.$stats['sent'].' — در صف: '.$stats['pending'].'</p></div>';
            echo '<form method="post" class="card">'.Admin::csrfField().'<label class="check"><input type="checkbox" name="enabled" '.($c['enabled']?'checked':'').'> ارسال Push فعال</label>';
            foreach(['project_id'=>'Project ID','sender_id'=>'Messaging Sender ID','api_key'=>'Web API Key (شناسهٔ عمومی محدود به اپ)'] as $k=>$label)echo '<label>'.e($label).'</label><input name="'.e($k).'" value="'.e($c[$k]).'">';
            foreach(PushDelivery::CLIENTS as $client)echo '<label>Firebase Android App ID — '.e($client).'</label><input name="apps['.e($client).']" value="'.e($c['apps'][$client]).'">';
            echo '<label>فایل JSON حساب سرویس — خالی بماند کلید قبلی حفظ می‌شود</label><textarea name="service_account" autocomplete="off"></textarea><p>شناسه‌های عمومی را از تنظیمات هر چهار اپ Firebase بگیرید. کلید خصوصی فقط روی سرور ذخیره می‌شود؛ آن را در APK یا گیت قرار ندهید. برای محدود کردن API Key بسته‌ها و امضای رسمی را در Google Cloud مجاز کنید.</p><button>ذخیرهٔ تنظیمات</button></form>';
            foreach($stats['errors'] as $error)echo '<div class="card">'.e($error['last_error']).' — '.(int)$error['total'].'</div>';
        },'push');
    }
}
