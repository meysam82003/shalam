Title: وب‌هوک و بررسی امضا

URL Source: https://abangateway.ir/docs/webhook

Published Time: 2026-08-01

Markdown Content:
[آموزش](https://abangateway.ir/docs)/برای برنامه‌نویس‌ها
## وب‌هوک و بررسی امضا

به محض تشخیص واریز یک درخواست امضاشده می‌فرستیم. ساختار دقیق payload، هدرها، روش بررسی امضا و قاعده idempotency.

در این صفحه

*   [چه وقت می‌فرستیم](https://abangateway.ir/docs/webhook#%DA%86%D9%87%20%D9%88%D9%82%D8%AA%20%D9%85%DB%8C%E2%80%8C%D9%81%D8%B1%D8%B3%D8%AA%DB%8C%D9%85)
*   [ساختار درخواست](https://abangateway.ir/docs/webhook#%D8%B3%D8%A7%D8%AE%D8%AA%D8%A7%D8%B1%20%D8%AF%D8%B1%D8%AE%D9%88%D8%A7%D8%B3%D8%AA)
*   [بررسی امضا](https://abangateway.ir/docs/webhook#%D8%A8%D8%B1%D8%B1%D8%B3%DB%8C%20%D8%A7%D9%85%D8%B6%D8%A7)
*   [تکراری بودن و پاسخ](https://abangateway.ir/docs/webhook#%D8%AA%DA%A9%D8%B1%D8%A7%D8%B1%DB%8C%20%D8%A8%D9%88%D8%AF%D9%86%20%D9%88%20%D9%BE%D8%A7%D8%B3%D8%AE)

## چه وقت می‌فرستیم

| رویداد | چه وقت |
| --- | --- |
| invoice.paid | فاکتور کامل تسویه شد |
| invoice.partially_paid | یک تکه از فاکتور چندتکه پرداخت شد |
| invoice.expired | مهلت تمام شد بدون پرداخت کامل |
| invoice.cancelled | فاکتور لغو شد |

## ساختار درخواست

```
POST https://shop.ir/callback
Content-Type: application/json
X-Event: invoice.paid
X-Delivery-Id: whd_k2m8p1q7r4s6t9v3
X-Timestamp: 1785571087
X-Signature: 3f9a2b8c1d4e...
User-Agent: AbanGateway-Webhook/1
```

بدنه

```
{
  "event": "invoice.paid",
  "invoice_id": "inv_a3f9k2m8p1q7r4s6",
  "order_id": "ORD-2291",
  "amount_rial": 150000000,
  "payable_rial": 150000010,
  "fee_rial": 40000,
  "status": "paid",
  "is_test": false,
  "paid_at": "2026-08-01T14:32:11Z",
  "metadata": {}
}
```

## بررسی امضا

X-Signature یک HMAC-SHA256 است روی بایت‌های دقیقِ بدنه، با کلید وب‌هوک شما. بدنه را قبل از بررسی دوباره سریالایز نکنید — یک فاصله اضافه یا تغییر ترتیب کلیدها امضا را باطل می‌کند.

```
$body = file_get_contents('php://input');
$expected = hash_hmac('sha256', $body, $webhookSecret);

if (!hash_equals($expected, $_SERVER['HTTP_X_SIGNATURE'] ?? '')) {
    http_response_code(400);
    exit;
}

$event = json_decode($body, true);
```

```
import crypto from "node:crypto";

// The raw body, not the parsed object.
app.post("/callback", express.raw({ type: "application/json" }), (req, res) => {
  const expected = crypto
    .createHmac("sha256", process.env.ABAN_WEBHOOK_SECRET)
    .update(req.body)
    .digest("hex");

  const given = req.get("X-Signature") ?? "";
  if (!crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(given))) {
    return res.sendStatus(400);
  }

  const event = JSON.parse(req.body.toString());
  res.sendStatus(200);
});
```

## تکراری بودن و پاسخ

یک وب‌هوک ممکن است بیش از یک بار برسد. تحویل با تلاش مجدد و backoff انجام می‌شود، و شبکه هم شبکه است. سمت خودتان بر اساس invoice_id عمل کنید: اگر آن سفارش را قبلاً تحویل داده‌اید، ۲۰۰ برگردانید و کاری نکنید.

*   پاسخ ۲xx یعنی دریافت شد و دیگر تلاش نمی‌کنیم.
*   هر پاسخ دیگری یا تایم‌اوت یعنی دوباره تلاش می‌کنیم.
*   ریدایرکت دنبال نمی‌شود — آدرس نهایی را بدهید.
*   سریع پاسخ بدهید و کار سنگین را به صف خودتان بسپارید؛ تایم‌اوت داریم.
