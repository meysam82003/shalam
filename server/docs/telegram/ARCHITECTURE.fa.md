# معماری و قرارداد توسعه

ماژول `app/telegram/` در bootstrap موجود بارگذاری می‌شود. فایل‌های اجرایی HTTP فقط `tg/api.php` و `tg/webhook.php` هستند. `tg/worker.php` فقط CLI است. UI از HTML/CSS/JS محلی و SDK رسمی Telegram استفاده می‌کند؛ داده تجاری و کلید در آن قرار ندارد. هیچ کتابخانه build-time برای اجرای هاست لازم نیست.

## جریان احراز هویت بدون فرم ورود

Owner در نشست وب معتبر، شناسه عددی Telegram را به حساب مدیر وصل می‌کند. پیوند تصادفی ۱۹۲بیتی، hash‌شده، ۵ دقیقه‌ای، یک‌بارمصرف و مقید به شناسه است. مصرف در Webhook دارای secret اتفاق می‌افتد؛ username به‌تنهایی مجوز نیست.

Mini App مقدار خام `initData` را به `POST tg/api.php?r=auth` می‌فرستد. HMAC مطابق مستند رسمی Telegram با کلید مشتق از Bot Token، مقایسه ثابت‌زمان، پارامترهای یکتا، auth_date حداکثر ۵ دقیقه و تحمل آینده ۳۰ ثانیه کنترل می‌شوند. `initDataUnsafe` در تصمیم سرور استفاده نمی‌شود. hash هر launch برای یک exchange مصرف می‌شود. خروجی token تصادفی مستقل است؛ در DB فقط hash ذخیره می‌شود. درخواست‌های بعدی `Authorization: Bearer ...` دارند. نقش و active حساب وب/عضو، epoch و انقضا در هر درخواست بررسی می‌شوند. نشست فعال بدون فرم اضافی تمدید می‌شود.

اعمال نوشتنی، همان فرم عملیات و دکمه ثبت را دارند؛ رمز/TOTP یا challenge اضافی وجود ندارد. فیلد `confirmed:true` صرفاً نشانگر درخواست ارسال‌شده توسط فرم است، عامل احراز هویت نیست و مانع جعل را به‌تنهایی تأمین نمی‌کند. امنیت به امضای Telegram، bearer، نقش، HTTPS، origin و اعتبارسنجی دامنه متکی است.

## API

همه پاسخ‌ها `Cache-Control: no-store` دارند. موفقیت `{"ok":true,"data":...}`؛ خطا `{"ok":false,"error":{"code":"...","message":"..."}}`. داده‌ای برای کاربر ناشناس برنمی‌گردد. CORS باز نیست. POST دارای Origin ناسازگار رد می‌شود؛ روش‌های غیر GET/POST و تغییر با GET رد می‌شوند.

| r | روش | کاربرد |
|---|---|---|
| auth | POST | تبادل initData تازه با نشست |
| me / catalog | GET | نقش جاری و فهرست واقعی قابلیت‌ها |
| dashboard | GET | آمار مجاز بر حسب نقش |
| list | GET | resource، q، status، from/to UTC، page، user_id، pro، store، unread |
| detail | GET | kind=user/transaction/order/ticket/ad/promo و id |
| gateways / status / alerts | GET | وضعیت و تنظیمات غیرمحرمانه مجاز |
| site / seo | GET | تنظیمات محتوای سایت و بررسی محلی |
| search | GET | جست‌وجوی منابع مجاز |
| export | GET | CSV خصوصی با مجوز reports و مجوز منبع |
| action | POST | اجرای action مجاز با data، request_key و confirmed |
| logout | POST | لغو همان نشست |

نمونه درخواست عملیات (فقط روی داده آزمایشی):

```json
{"action":"user.note","data":{"id":1,"reason":"بررسی درخواست پشتیبانی"},"request_key":"random_uuid_per_intent","confirmed":true}
```

برای retry شبکه، همان request_key و همان data را استفاده کنید. درخواست همسان نتیجه قبلی را می‌گیرد؛ تغییر data با همان کلید 409 می‌دهد. ردیف نشست با FOR UPDATE قفل می‌شود و مجوز تازه داخل transaction دوباره کنترل می‌شود. کلید درخواست مخصوص همان نشست است.

لیست actionهای مجاز در `Management::ACTIONS` و projectionهای خواندنی در `TgRead::RESOURCES` مرجع قطعی‌اند. هیچ مسیری برای paid کردن خرید، خواندن recovery/password/private keys، تنظیم Bot Token از مینی‌اپ یا اجرای SQL آزاد وجود ندارد. upload با multipart و فیلد file و data[reason] است؛ محدودیت Media موجود اعمال می‌شود.

## سرویس مشترک و صف

Users/Sessions/Devices/Entitlements/Promo/Ads/Media/Notices/SiteSettings/PayRegistry منابع اصلی‌اند. validator پلن و تبلیغ و اقدامات کاربر در پنل وب نیز استفاده می‌شوند. داده کاربر/اشتراک/تراکنش در DB جدید کپی نمی‌شود. تمام تغییرهای مینی‌اپ Audit با شناسه admin واقعی دارند؛ داده خام ورودی و کلیدها log نمی‌شوند. عملیات فایل مثل upload/delete/SEO مانند سرویس فعلی نمی‌توانند با تراکنش DB به‌صورت واحد rollback شوند؛ بکاپ/خطای نوشتن مستقل مدیریت می‌شود.

ثبت دامنه در transaction → tg_events با کلید یکتا → tg_outbox برای اعضای همان زمان → Cron با GET_LOCK → کنترل مجوز/epoch/قانون/سکوت تازه → ارسال HTTPS. هیچ شبکه Telegram داخل transaction خرید یا ثبت‌نام نیست. Updateهای webhook یکتا هستند. وقفه شبکه بعد از ارسال نتیجه «نامعلوم» دارد؛ تضمین exactly-once خارجی ادعا نمی‌شود. جزئیات retention/retry در OPERATIONS است.

## مرزهای توسعه بعدی

افزودن اقدام باید ابتدا به سرویس دامنه و permission map برسد، سپس endpoint و UI؛ مخفی‌کردن دکمه کنترل دسترسی نیست. عملیات مالی فقط با verifier فعلی و استعلام معتبر انجام شوند. افزودن پیام تیکت به کاربر نیازمند مسیر تحویل واقعی، رضایت/هویت مقصد و نتیجه delivery است. برای relay و Search Console باید مدیریت کلید/OAuth جدا طراحی و تست شود. تست‌های مرورگر، داده مصنوعی و SDK test shim فقط در server/tests هستند و در public کپی نمی‌شوند.

مرجع روش Telegram: https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app و https://core.telegram.org/bots/api#setwebhook .
