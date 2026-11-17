<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }
final class IranAdsPage
{
    public static function show(): void
    {
        if (($_SERVER['REQUEST_METHOD']??'GET')==='POST') {
            if(!in_array(Admin::$me['role'],['owner','manager'],true))throw new ApiError('role','فقط مدیر مجاز است.',403);
            $input=[];
            foreach(AdminMobile::adsFields() as $f){
                $key=$f['key'];$name=str_replace('.','__',$key);$value=$_POST[$name]??'';
                if($f['type']==='bool')$value=isset($_POST[$name]);
                elseif($f['type']==='number')$value=(string)$value;
                elseif($f['type']==='list')$value=array_values(array_filter(array_map('trim',explode(',',(string)$value))));
                $cursor=&$input;$parts=explode('.',$key);$last=array_pop($parts);foreach($parts as $part){if(!isset($cursor[$part]))$cursor[$part]=[];$cursor=&$cursor[$part];}$cursor[$last]=$value;unset($cursor);
            }
            $before=Monetization::settings();$after=Monetization::save($input);Admin::audit('iran_ads_settings','settings','iran_ads','پنل مدیریت',$before,$after);Admin::flash('تنظیمات تبلیغات ذخیره شد؛ اپ در بررسی بعدی دریافت می‌کند.');Admin::redirect('p=iranads');
        }
        Admin::layout('مدیریت تبلیغات ایرانی',function(){
            echo '<div class="card"><h2>تپسل و ادیوری</h2><p>SDKهای رسمی؛ حالت آزمایشی درآمد یا سکهٔ واقعی تولید نمی‌کند. درآمد واقعی: <b>ناموجود</b>.</p><p>شناسهٔ جایگاه‌ها و قوانین نمایش پویا هستند. کلید راه‌اندازی تپسل در مانیفست نسخهٔ پایدار ثابت است؛ تغییر آن به ساخت APK جدید نیاز دارد. کلید ادیوری در شروع مجدد اپ اعمال می‌شود.</p><p>تأیید مستقل SSV رسمی پیکربندی نشده؛ پاداش به رویداد SDK و کنترل‌های ضدتکرار و محدودیت سرور متکی است.</p></div>';
            echo '<form method="post">'.Admin::csrfField().'<div class="grid g2">';
            foreach(AdminMobile::adsFields() as $f){$name=str_replace('.','__',$f['key']);echo '<div class="card"><label>'.e($f['label']).'</label>';
                if($f['type']==='bool')echo '<input type="checkbox" name="'.e($name).'" '.($f['value']?'checked':'').'>';
                elseif($f['type']==='select'){echo '<select name="'.e($name).'">';foreach($f['options'] as $v)echo '<option value="'.e($v).'" '.($f['value']===$v?'selected':'').'>'.e($v).'</option>';echo '</select>';}
                else echo '<input type="'.($f['type']==='number'?'number':'text').'" name="'.e($name).'" value="'.e(is_array($f['value'])?implode(',',$f['value']):(string)$f['value']).'">';
                echo '</div>';
            }
            echo '</div><button class="btn gold">ذخیره و اعمال</button></form>';
            $s=Monetization::stats();echo '<div class="card"><h3>رویدادهای ثبت‌شدهٔ ۳۰ روز اخیر</h3><table><tr><th>شبکه</th><th>رویداد</th><th>نوع</th><th>تعداد</th></tr>';
            foreach($s['events'] as $v)echo '<tr><td>'.e($v['network']).'</td><td>'.e($v['event']).'</td><td>'.((int)$v['test_mode']?'آزمایشی':'واقعی').'</td><td>'.(int)$v['total'].'</td></tr>';
            echo '</table><h3>تاریخچهٔ پاداش‌ها</h3><table><tr><th>کاربر</th><th>سکه</th><th>شبکه</th><th>زمان</th></tr>';
            foreach($s['history'] as $v)echo '<tr><td>'.e($v['username']).'</td><td>'.(int)$v['amount'].'</td><td>'.e($v['network']).'</td><td>'.e($v['created_at']).'</td></tr>';
            echo '</table></div>';
        },'iranads');
    }
}
