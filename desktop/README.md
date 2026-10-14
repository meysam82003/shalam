# دیوان امتیاز برای ویندوز

نسخهٔ ویندوز همان نسخهٔ وب است (کد خود اپ اندروید که به JavaScript کامپایل شده) در پنجرهٔ خودش؛ هیچ قابلیتی کم یا متفاوت نیست.

ساخت نصب‌کننده (روی لینوکس هم می‌شود؛ امضای دیجیتال ندارد):

    cd webapp && python3 build-play.py --channel windows --out ../desktop/play
    cd ../desktop && npm ci && npm run dist

خروجی: `desktop/dist/DivanEmtiaz-Setup-<version>.exe` — ویندوز ۱۰ و ۱۱ (۶۴ بیتی).
