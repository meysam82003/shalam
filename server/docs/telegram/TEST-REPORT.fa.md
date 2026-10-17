# گزارش آزمون — نامزد انتشار آزمایشی

## نتایج تأییدشده در محیط محلی

- PHP 8.3 و MariaDB واقعی، دیتابیس مصنوعی جدا: ۵۶۹ بررسی موفق در مجموعه Backend اصلی + Telegram.
- امضای initData، پارامتر تکراری/دست‌کاری، تاریخ آینده/انقضا، replay، اتصال یک‌باره مقید به شناسه، جلوگیری از ورود کاربر عادی.
- ماتریس شش نقش، سقف نقش وب، لغو فوری نشست، تمدید بی‌صدای نشست فعال بدون رمز/کد، محدودیت نرخ و عملیات idempotent.
- رویداد واقعی ثبت‌نام و خرید با verifier آزمایشی؛ rollback مشترک، جلوگیری از paid جعلی و refund دستی، محاسبه درآمد بدون دوباره‌شماری.
- خرید/امضای بازار و مایکت، واسط ایران و درگاه‌های زرین‌پال/بلوپال/آبان در تست‌های موجود با پاسخ آزمایشی. برداشت پول یا استعلام خصوصی Production انجام نشد.
- صف: ارسال یک‌باره، پاسخ 429، محدودیت تلاش، timeout مبهم، worker قطع‌شده، لغو دسترسی پیش از ارسال، جلوگیری از افشای خلاصه پس از کاهش مجوز، سکوت و خلاصه شخصی.
- رسانه واقعی PNG، MIME اجرایی جعلی، جایگاه/فرمت ناسازگار، لینک XSS، تاریخ و سقف نامعتبر؛ جلوگیری از حذف رسانه متصل به اعلان.
- تیکت، یادداشت داخلی، گزارش‌های خواندنی، عدم خروج password/recovery/token/raw secrets، SEO روی فایل آزمایشی و محافظت مسیر.
- مهاجرت تکرارپذیر، حفظ داده، بکاپ و restore، تست‌های اصلی API/دستگاه/کد تخفیف.

## اصلاح کشف‌شده در CI

MySQL 8 با ONLY_FULL_GROUP_BY در نمودار وضعیت دوره‌ها خطا نشان داد؛ عبارت گروه‌بندی به subquery با ستون مشتق تغییر کرد تا در حالت سخت‌گیر نیز معتبر باشد. تکرار CI پس از اصلاح لازم است. ۷۴ بررسی مستقل واسط پرداخت نیز محلی موفق شد.

## موارد در انتظار تأیید

Chrome در محیط محلی به علت محدودیت سوکت سیستم اجرا نشد (`socket() failed: Operation not permitted`). بنابراین آزمون مرورگر محلی و بازبینی تصویری موفق ثبت نمی‌شود. آزمون Playwright آماده است: viewport موبایل/تبلت، مسیرهای اصلی، عملیات واقعی روی API آزمایشی، RTL/overflow، حالت روشن/تاریک، قطع شبکه، CSV و HTTP جعلی. گردش‌کار `telegram-tests.yml` برای CI اضافه شده؛ نتیجه اجرای آن باید بررسی شود.

Android تغییر نکرده است، اما gate الزامی AGENTS یعنی `gradle --no-daemon testReleaseUnitTest assembleRelease` و امضای Release در این محیط اجرا نشده؛ Gradle/SDK نصب نیست. گردش‌کار موجود مخزن این gate را در PR اجرا می‌کند. تا عبور آن و staging، این نسخه آماده انتشار Production تلقی نمی‌شود.

هاست اصلی و DB زنده، نسخه PHP سرور، Telegram API از شبکه هاست، webhook واقعی، Telegram Android/iOS/Web، عملکرد موبایل اقتصادی و دانلود CSV داخل webview واقعی تأیید نشده‌اند. Native SDK تلگرام در تست مرورگر با initData امضاشده آزمایشی جایگزین می‌شود؛ احراز هویت PHP واقعی است.

## اجرای دوباره

فقط با DB خالی/آزمایشی اجرا کنید؛ اسکریپت‌های تست جدول‌های با پیشوند `dvt_` و `dvtui_` را حذف و دوباره می‌سازند. این رفتار در تست است؛ migration بسته نصب حذف ندارد.

```sh
DIVAN_TEST_DSN='host=127.0.0.1;port=3306;dbname=divan_test2' DIVAN_TEST_USER=divan_test DIVAN_TEST_PASS='TEST_DB_PASSWORD' php server/tests/run.php
php payment-bridge/divan-bridge/tests/run.php
npm install --no-save --package-lock=false playwright@1.58.2
npx playwright install chromium
DIVAN_TEST_DSN='host=127.0.0.1;port=3306;dbname=divan_test2' DIVAN_TEST_USER=divan_test DIVAN_TEST_PASS='TEST_DB_PASSWORD' python3 server/tests/run-browser-local.py
```

مرورگر فقط روی توسعه/CI لازم است؛ هاست تولید به Node یا Playwright نیاز ندارد. تنظیمات، داده مصنوعی و کلید تست در ZIP تولید قرار نمی‌گیرند. آزمون‌ها و CI از سورس قابل اجرا هستند.
