Source: https://blupal.top/documentation (fetched 2026-10-13)

مستندات API - بلو
مزایا
نحوه کار
تعرفه
سوالات
مستندات
تماس با ما
ورود / ثبت‌نام
مستندات API
راهنمای کامل استفاده از API برای ایجاد و مدیریت فاکتورهای پرداخت
دریافت API Key
برای استفاده از API، ابتدا باید یک کلید API (API Key) دریافت کنید:
از منوی داشبورد بلو روی مدیریت API Key بروید
یک API Key جدید بسازید یا از کلیدهای قبلی استفاده کنید
کلید را در محیط امن نگه دارید و آن را در درخواست‌های خود به سرور ارسال کنید
آدرس پایه (Base URL)
آدرس پایه برای درخواست‌های API:
https://blupal.top/api
تمام endpointها زیر این آدرس قرار دارند.
احراز هویت
در هر درخواست، API Key را در هدر X-API-Key ارسال کنید:
X-API-Key: YOUR_API_KEY
یا می‌توانید از query parameter استفاده کنید:
?api_key=YOUR_API_KEY
هشدار: API Key را در کد سمت کلاینت (مثل جاوااسکریپت مرورگر) قرار ندهید. همیشه از سمت سرور خودتان درخواست‌ها را ارسال کنید.
محیط آزمایشی (Sandbox)
تست بدون پول واقعی با کلید blu_test_... (Live: blu_live_... ). کارت واقعی لازم نیست؛ فاکتور حدود ۳۰ دقیقه اعتبار دارد.
جریان: ساخت فاکتور → شبیه‌سازی / صفحه پرداخت → چک وضعیت یا دریافت webhook
۱) ایجاد فاکتور
POST
https://blupal.top/api/v1/invoices/create
پارامتر
وضعیت
توضیح
X-API-Key
الزامی
کلید blu_test_...
amount
الزامی
ریال — حداقل 100000 و حداکثر 500000000
card_number
اختیاری
در Sandbox نادیده گرفته می‌شود
curl -X POST "https://blupal.top/api/v1/invoices/create" \
-H "Content-Type: application/json" \
-H "X-API-Key: blu_test_YOUR_KEY" \
-d '{"amount": 1000000}'
{
"success": true,
"invoice_id": 123,
"amount": 1000000,
"final_amount": 1000123,
"status": "PENDING",
"payment_link": "https://blupal.top/sandbox/payment/01ARZ3NDEKTSV4RRFFQ69G5FAV",
"card_number": "6219861012345678",
"mode": "sandbox",
"expires_at": "2026-07-14T12:30:00+03:30"
}
۲) شبیه‌سازی پرداخت
POST
https://blupal.top/api/v1/sandbox/invoices/{invoice_id}/simulate
فقط کلید Sandbox و فاکتور PENDING . Body: scenario (اختیاری، پیش‌فرض success )
scenario
وضعیت
Webhook
success
PAID
بله
wrong_amount
PENDING
خیر
expire
EXPIRED
خیر
cancel
CANCELED
خیر
curl -X POST "https://blupal.top/api/v1/sandbox/invoices/123/simulate" \
-H "Content-Type: application/json" \
-H "X-API-Key: blu_test_YOUR_KEY" \
-d '{"scenario": "success"}'
{
"success": true,
"status": "PAID",
"invoice_id": 123,
"transaction_id": 456
}
خطاهای رایج: unauthorized ، mode_mismatch ، invalid_scenario ، invalid_state ، not_found
۳) وضعیت / صفحه / Webhook
وضعیت: GET https://blupal.top/api/v1/invoices/{invoice_id} با کلید Sandbox
صفحه پرداخت: https://blupal.top/sandbox/payment/{public_token}
بعد از success webhook با mode: "sandbox" ارسال می‌شود
{
"success": true,
"event": "payment.completed",
"invoice_id": 123,
"status": "PAID",
"amount": 1000000,
"final_amount": 1000123,
"mode": "sandbox",
"payer_name": "علی رضایی",
"payer_card": "6037991234567890",
"payer_bank_name": "بانک ملی"
}
Endpoint های API
ایجاد فاکتور
POST
https://blupal.top/api/v1/invoices/create
این endpoint برای ایجاد یک فاکتور پرداخت جدید استفاده می‌شود. پس از ایجاد فاکتور، یک لینک پرداخت و شماره کارت برای واریز مبلغ دریافت خواهید کرد.
پارامترهای درخواست:
پارامتر
نوع
وضعیت
توضیحات
amount
integer
الزامی
مبلغ فاکتور به ریال (حداقل 100,000 و حداکثر 500,000,000 ریال)
card_number
string
اختیاری
شماره کارت مشخص برای واریز. در صورت عدم ارسال یا عدم تطابق، ابتدا کارت پیش‌فرض کاربر (در صورت تنظیم و فعال بودن) استفاده می‌شود؛ در غیر این صورت یک کارت فعال به‌صورت تصادفی انتخاب می‌شود
مثال درخواست (cURL):
curl -X POST "https://blupal.top/api/v1/invoices/create" \
-H "Content-Type: application/json" \
-H "X-API-Key: YOUR_API_KEY" \
-d '{
"amount": 1000000,
"card_number": "6219861012345678"
}'
مثال پاسخ موفق:
{
"success": true,
"invoice_id": 123,
"amount": 1000000,
"final_amount": 1000123,
"status": "PENDING",
"payment_link": "https://blupal.top/payment/01ARZ3NDEKTSV4RRFFQ69G5FAV",
"card_number": "6219861012345678",
"mode": "live",
"expires_at": null
}
نکته: فیلد final_amount = مبلغ اصلی + عدد تصادفی ۳ رقمی (۰–۹۹۹). با کلید Sandbox مقدار mode برابر sandbox است.
مثال پاسخ خطا:
{
"success": false,
"error": "amount_too_low",
"message": "مبلغ باید حداقل 100,000 ریال باشد"
}
بررسی وضعیت فاکتور
GET
https://blupal.top/api/v1/invoices/{invoice_id}
این endpoint برای بررسی وضعیت یک فاکتور استفاده می‌شود. می‌توانید وضعیت پرداخت، مبلغ و تاریخ پرداخت را دریافت کنید.
پارامترهای URL:
پارامتر
نوع
توضیحات
invoice_id
integer
شناسه فاکتور که از endpoint ایجاد فاکتور دریافت کرده‌اید (عدد)
مثال درخواست (cURL):
curl -X GET "https://blupal.top/api/v1/invoices/123" \
-H "X-API-Key: YOUR_API_KEY"
مثال پاسخ موفق:
{
"success": true,
"invoice_id": 123,
"status": "PAID",
"transaction_id": 456,
"amount": 1000000,
"final_amount": 1000123,
"mode": "live",
"expires_at": null,
"payer_name": "علی رضایی",
"payer_card": "6037991234567890",
"payer_bank_name": "بانک ملی"
}
نکته: فیلدهای payer_* فقط پس از پرداخت موفق پر می‌شوند؛ در وضعیت‌های دیگر null هستند. در فیلد payer_card برای همهٔ بانک‌ها شماره کارت ارسال می‌شود، به‌جز بلو بانک که فقط شماره شبا در دسترس است.
وضعیت‌های ممکن:
PENDING - فاکتور در انتظار پرداخت است
PAID - فاکتور پرداخت شده است
EXPIRED - فاکتور منقضی شده است
CANCELED - فاکتور لغو شده است
مثال پاسخ خطا:
{
"success": false,
"error": "not_found"
}
Webhook (اطلاع‌رسانی خودکار)
Webhook به شما امکان دریافت اطلاع‌رسانی خودکار را می‌دهد. زمانی که یک فاکتور پرداخت می‌شود، سیستم به صورت خودکار یک درخواست POST به آدرس Webhook شما ارسال می‌کند.
تنظیم Webhook URL
برای استفاده از Webhook، باید آدرس Webhook خود را در تنظیمات API Key ثبت کنید:
از بخش مدیریت API Key وارد شوید
API Key خود را انتخاب یا ایجاد کنید
فیلد Webhook URL را با آدرس سرور خود پر کنید
آدرس باید یک URL معتبر و قابل دسترسی از اینترنت باشد (HTTPS توصیه می‌شود)
نکته: Webhook URL باید یک endpoint معتبر باشد که درخواست‌های POST را دریافت می‌کند و پاسخ HTTP 200 برمی‌گرداند.
زمان ارسال Webhook
Webhook در زمان‌های زیر ارسال می‌شود:
پس از پرداخت موفق: بلافاصله پس از شناسایی و تطبیق پرداخت با فاکتور، Webhook ارسال می‌شود
فقط یک بار: برای هر فاکتور، Webhook فقط یک بار ارسال می‌شود (هنگام تغییر وضعیت به PAID)
فرمت درخواست Webhook
سیستم یک درخواست POST با محتوای JSON به آدرس Webhook شما ارسال می‌کند:
مثال Payload ارسالی:
{
"success": true,
"event": "payment.completed",
"invoice_id": 123,
"status": "PAID",
"amount": 1000000,
"final_amount": 1000123,
"mode": "live",
"payer_name": "علی رضایی",
"payer_card": "6037991234567890",
"payer_bank_name": "بانک ملی"
}
نکته: در فیلد payer_card برای همهٔ بانک‌ها شماره کارت ارسال می‌شود، به‌جز بلو بانک که فقط شماره شبا در دسترس است (مثلاً IR120170000000123456789001 ).
توضیحات فیلدها:
فیلد
نوع
توضیحات
success
boolean
همیشه true است (در صورت موفقیت)
event
string
نوع رویداد - همیشه "payment.completed" است
invoice_id
integer
شناسه یکتای فاکتور (عدد - همان شناسه‌ای که از endpoint ایجاد فاکتور دریافت کرده‌اید)
status
string
وضعیت فاکتور - همیشه "PAID" است
amount
integer
مبلغ اصلی فاکتور به ریال
final_amount
integer
مبلغ نهایی که باید واریز شود (مبلغ اصلی + عدد تصادفی 3 رقمی)
mode
string
live یا sandbox — محیط فاکتور را مشخص می‌کند
payer_name
string|null
نام پرداخت‌کننده (واریزکننده) طبق اطلاعات بانک
payer_card
string|null
شماره کارت پرداخت‌کننده برای همهٔ بانک‌ها؛ برای بلو بانک فقط شماره شبا (در صورت موجود بودن در داده بانک)
payer_bank_name
string|null
نام بانک مبدأ پرداخت‌کننده
مثال دریافت Webhook (PHP)
<?php
// دریافت داده‌های Webhook
$payload = json_decode(file_get_contents('php://input'), true);
// بررسی صحت داده‌ها
if ($payload && isset($payload['event']) && $payload['event'] === 'payment.completed') {
$invoiceId = $payload['invoice_id'];
$amount = $payload['amount'];
$finalAmount = $payload['final_amount'];
// پردازش پرداخت موفق
// مثلاً: به‌روزرسانی دیتابیس، ارسال ایمیل، و غیره
// پاسخ موفق به سرور
http_response_code(200);
echo json_encode(['received' => true]);
} else {
// پاسخ خطا
http_response_code(400);
echo json_encode(['error' => 'Invalid payload']);
}
?>
مثال دریافت Webhook (Node.js)
const express = require('express');
const app = express();
app.use(express.json());
app.post('/webhook', (req, res) => {
const payload = req.body;
// بررسی صحت داده‌ها
if (payload && payload.event === 'payment.completed') {
const { invoice_id, amount, final_amount } = payload;
// پردازش پرداخت موفق
console.log(`Payment completed for invoice: ${invoice_id}`);
console.log(`Amount: ${amount}, Final: ${final_amount}`);
// پاسخ موفق
res.status(200).json({ received: true });
} else {
res.status(400).json({ error: 'Invalid payload' });
}
});
app.listen(3000);
مثال دریافت Webhook (Python - Flask)
from flask import Flask, request, jsonify
app = Flask(__name__)
@app.route('/webhook', methods=['POST'])
def webhook():
payload = request.get_json()
# بررسی صحت داده‌ها
if payload and payload.get('event') == 'payment.completed':
invoice_id = payload.get('invoice_id')
amount = payload.get('amount')
final_amount = payload.get('final_amount')
# پردازش پرداخت موفق
print(f"Payment completed for invoice: {invoice_id}")
print(f"Amount: {amount}, Final: {final_amount}")
# پاسخ موفق
return jsonify({'received': True}), 200
else:
return jsonify({'error': 'Invalid payload'}), 400
if __name__ == '__main__':
app.run(port=3000)
نکات مهم Webhook
مهم: سرور شما باید در کمتر از 10 ثانیه پاسخ دهد، در غیر این صورت درخواست timeout می‌شود.
پاسخ سریع: سرور شما باید در کمتر از 10 ثانیه پاسخ HTTP 200 برگرداند
Idempotency: ممکن است Webhook چندین بار ارسال شود، پس باید منطق idempotent داشته باشید
HTTPS: برای امنیت بیشتر، از HTTPS استفاده کنید
اعتبارسنجی: همیشه invoice_id را با دیتابیس خود بررسی کنید
لاگ‌گیری: تمام Webhook‌های دریافتی را لاگ کنید تا در صورت مشکل بتوانید بررسی کنید
Retry: در صورت پاسخ ناموفق یا خطا، سیستم تا ۳ بار دیگر با فاصلهٔ ۱۰ ثانیه، ۳۰ ثانیه و ۶۰ ثانیه تلاش می‌کند؛ هر تلاش در لاگ Webhook Delivery ثبت می‌شود
پاسخ موفق
سرور شما باید پاسخ HTTP 200 با محتوای JSON برگرداند:
HTTP/1.1 200 OK
Content-Type: application/json
{
"received": true
}
تست Webhook
برای تست Webhook خود می‌توانید از ابزارهای زیر استفاده کنید:
webhook.site: یک URL موقت برای تست دریافت می‌دهد
ngrok: برای ایجاد تونل به سرور محلی خود
Postman: برای شبیه‌سازی درخواست Webhook
نکات امنیتی
API Key را محافظت کنید: هرگز API Key را در کد سمت کلاینت (مثل جاوااسکریپت مرورگر) قرار ندهید
استفاده از HTTPS: همیشه از HTTPS برای ارسال درخواست‌ها استفاده کنید
درخواست از سرور: تمام درخواست‌های API را از سمت سرور خودتان انجام دهید
مدیریت کلید: در صورت لو رفتن کلید، بلافاصله آن را غیرفعال کرده و کلید جدید بسازید
محدودیت Rate: از ارسال درخواست‌های بیش از حد خودداری کنید
کدهای خطا
کد خطا
توضیحات
کد HTTP
unauthorized
API Key نامعتبر یا وجود ندارد
401
amount_required
مبلغ ارسال نشده است
400
amount_too_low
مبلغ کمتر از حداقل مجاز (100,000 ریال) است
400
amount_too_high
مبلغ بیشتر از حداکثر مجاز (500,000,000 ریال) است
400
no_active_card
هیچ کارت فعالی برای این کاربر یافت نشد
400
invalid_invoice_id
شناسه فاکتور نامعتبر است
400
not_found
فاکتور یافت نشد
404
mode_mismatch
عدم تطابق حالت کلید و فاکتور (مثلاً کلید Live روی فاکتور Sandbox، یا simulate با کلید Live)
403
invalid_scenario
مقدار scenario در simulate نامعتبر است
400
invalid_state
فاکتور برای شبیه‌سازی آماده نیست (مثلاً دیگر PENDING نیست یا منقضی شده)
400
© 2026 بلوپال - درگاه ولی دوست‌داشتنی. تمامی حقوق محفوظ است.