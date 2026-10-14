<?php
/**
 * The introduction site, managed from the panel («مدیریت سایت»): a notice bar, a discount-code banner, ads in the
 * site's own slots, the order and visibility of the home page sections, its main texts, and the business, contact and
 * download details. The site's pages stay static; site.js reads this once per visit from site.php and applies it.
 * An empty value never replaces what the site's own files say.
 */
declare(strict_types=1);

final class SiteSettings
{
    /** Home page sections that can be moved or hidden (the top introduction always stays first). */
    public const SECTIONS = ['games' => 'هفت بازی', 'screens' => 'نمای برنامه', 'features' => 'امکانات', 'pro' => 'اشتراک Pro', 'faq' => 'پرسش‌ها', 'download' => 'دریافت برنامه'];

    /** Texts of the home page that can be replaced (plain text only). */
    public const TEXTS = [
        'hero_kicker' => ['عنوان کوچک بالای صفحه', 80], 'hero_title' => ['عنوان اصلی', 60], 'hero_lead' => ['متن معرفی زیر عنوان', 600],
        'pro_lead' => ['متن بخش اشتراک Pro', 400], 'download_title' => ['عنوان بخش دریافت', 80], 'download_lead' => ['متن زیر عنوان دریافت', 200],
    ];

    public const BUSINESS = ['owner' => ['صاحب امتیاز (مطابق اینماد)', 120], 'ownerType' => ['نوع: حقیقی یا حقوقی', 20], 'registration' => ['شناسهٔ ملی یا شمارهٔ ثبت', 40], 'since' => ['سال شروع فعالیت', 10]];
    public const CONTACT = [
        'email' => ['ایمیل پشتیبانی', 120], 'phone' => ['تلفن ثابت', 30], 'mobile' => ['تلفن همراه', 30], 'address' => ['نشانی', 300], 'postalCode' => ['کد پستی', 20],
        'hours' => ['ساعات پاسخ‌گویی', 120], 'telegram' => ['تلگرام (بدون @)', 40], 'eitaa' => ['ایتا (بدون @)', 40], 'bale' => ['بله (بدون @)', 40],
    ];
    /** [key, title, has size, has sha256] */
    public const DOWNLOADS = [['android', 'اندروید — APK سایت', true, true], ['windows', 'ویندوز', true, true], ['iphone', 'آیفون — پروفایل نصب', true, false],
        ['bazaar', 'کافه‌بازار', false, false], ['myket', 'مایکت', false, false]];
    public const TONES = ['gold' => 'طلایی', 'teal' => 'فیروزه‌ای (خبر خوب)', 'crimson' => 'زرشکی (هشدار)'];
    public const AD_SLOTS = ['site_top', 'site_middle', 'site_bottom', 'site_pages'];

    public static function defaults(): array
    {
        $sections = [];
        $i = 1;
        foreach (self::SECTIONS as $k => $_) $sections[$k] = ['order' => $i++, 'show' => true];
        $dl = ['version' => '', 'github' => ''];
        foreach (self::DOWNLOADS as [$k]) $dl[$k] = ['url' => '', 'size' => '', 'sha256' => ''];
        return [
            'announce' => ['on' => false, 'text' => '', 'link' => '', 'link_text' => '', 'tone' => 'gold', 'home_only' => false],
            'promo' => ['on' => false, 'code' => '', 'title' => '', 'text' => ''],
            'ads' => ['on' => false],
            'sections' => $sections,
            'texts' => array_fill_keys(array_keys(self::TEXTS), ''),
            'business' => array_fill_keys(array_keys(self::BUSINESS), ''),
            'contact' => array_fill_keys(array_keys(self::CONTACT), ''),
            'downloads' => $dl,
        ];
    }

    public static function get(): array
    {
        return self::merge(self::defaults(), (array)Settings::get('site_cfg', []));
    }

    /** Stored values over the defaults, keeping only known keys. */
    private static function merge(array $base, array $over): array
    {
        foreach ($base as $k => $v) {
            if (!array_key_exists($k, $over)) continue;
            $base[$k] = is_array($v) ? self::merge($v, (array)$over[$k]) : (is_bool($v) ? (bool)$over[$k] : (is_int($v) ? (int)$over[$k] : (string)$over[$k]));
        }
        return $base;
    }

    private static function text($v, int $max): string
    {
        return mb_substr(trim(preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', (string)$v)), 0, $max);
    }

    /** A link the site may open: https, or a path on the same site. Anything else is dropped. */
    public static function link($v): string
    {
        $v = trim((string)$v);
        if ($v === '') return '';
        if (preg_match('#^/(?!/)[^\s<>"\'\\\\]*$#', $v)) return mb_substr($v, 0, 400);
        if (preg_match('#^https://[^\s<>"\'\\\\]+$#i', $v) && filter_var($v, FILTER_VALIDATE_URL)) return mb_substr($v, 0, 400);
        throw new ApiError('bad_link', 'نشانی «' . mb_substr($v, 0, 60) . '» معتبر نیست؛ فقط https:// یا مسیری که با / شروع شود.');
    }

    /** One part of the form («announce», «promo», …); the other parts keep their values. */
    public static function save(string $part, array $in): void
    {
        $c = self::get();
        switch ($part) {
            case 'announce':
                $c['announce'] = ['on' => !empty($in['on']), 'text' => self::text($in['text'] ?? '', 300), 'link' => self::link($in['link'] ?? ''),
                    'link_text' => self::text($in['link_text'] ?? '', 40), 'tone' => array_key_exists($in['tone'] ?? '', self::TONES) ? $in['tone'] : 'gold', 'home_only' => !empty($in['home_only'])];
                if ($c['announce']['on'] && $c['announce']['text'] === '') throw new ApiError('empty', 'متن اعلان را بنویسید.');
                break;
            case 'promo':
                $code = Promo::normalize((string)($in['code'] ?? ''));
                $c['promo'] = ['on' => !empty($in['on']), 'code' => $code, 'title' => self::text($in['title'] ?? '', 80), 'text' => self::text($in['text'] ?? '', 300)];
                if ($c['promo']['on']) {
                    $p = $code === '' ? null : Db::one('SELECT * FROM {promo_codes} WHERE code = ?', [$code]);
                    if ($p === null || $p['kind'] !== 'discount') throw new ApiError('promo', 'کد تخفیف را از فهرست «کدهای تخفیف و هدیه» انتخاب کنید (فقط کدهای نوع «تخفیف خرید»).');
                }
                break;
            case 'ads':
                $c['ads'] = ['on' => !empty($in['on'])];
                break;
            case 'sections':
                foreach (self::SECTIONS as $k => $_) {
                    $c['sections'][$k] = ['order' => max(1, min(99, (int)($in['order'][$k] ?? $c['sections'][$k]['order']))), 'show' => !empty($in['show'][$k])];
                }
                break;
            case 'texts':
                foreach (self::TEXTS as $k => [, $max]) $c['texts'][$k] = self::text($in[$k] ?? '', $max);
                break;
            case 'business':
                foreach (self::BUSINESS as $k => [, $max]) $c['business'][$k] = self::text($in['business'][$k] ?? '', $max);
                foreach (self::CONTACT as $k => [, $max]) $c['contact'][$k] = self::text($in['contact'][$k] ?? '', $max);
                if ($c['contact']['email'] !== '' && !filter_var($c['contact']['email'], FILTER_VALIDATE_EMAIL)) throw new ApiError('email', 'ایمیل معتبر نیست.');
                foreach (['telegram', 'eitaa', 'bale'] as $k) {
                    $c['contact'][$k] = ltrim($c['contact'][$k], '@');
                    if ($c['contact'][$k] !== '' && !preg_match('/^[A-Za-z0-9_.]{3,40}$/', $c['contact'][$k])) throw new ApiError('handle', 'شناسهٔ پیام‌رسان فقط حروف انگلیسی، عدد و _ است.');
                }
                break;
            case 'downloads':
                $c['downloads']['version'] = self::text($in['version'] ?? '', 20);
                $c['downloads']['github'] = self::link($in['github'] ?? '');
                foreach (self::DOWNLOADS as [$k, , $size, $sha]) {
                    $sum = strtolower(trim((string)($in[$k]['sha256'] ?? '')));
                    if ($sum !== '' && !preg_match('/^[a-f0-9]{64}$/', $sum)) throw new ApiError('sha', 'کد SHA-256 باید ۶۴ نویسهٔ 0-9 و a-f باشد.');
                    $c['downloads'][$k] = ['url' => self::link($in[$k]['url'] ?? ''), 'size' => $size ? self::text($in[$k]['size'] ?? '', 30) : '', 'sha256' => $sha ? $sum : ''];
                }
                break;
            default:
                throw new ApiError('bad_part', 'بخش نامعتبر.');
        }
        Settings::set('site_cfg', $c);
    }

    /** What the site reads (site.php): only filled values, the live discount banner and the current ads. */
    public static function publicView(bool $countAds = true): array
    {
        $c = self::get();
        $out = ['v' => 1];
        if ($c['announce']['on'] && $c['announce']['text'] !== '') $out['announce'] = $c['announce'];
        if ($c['promo']['on'] && $c['promo']['code'] !== '') {
            $p = Db::one('SELECT * FROM {promo_codes} WHERE code = ?', [$c['promo']['code']]);
            $now = Clock::now();
            $live = $p !== null && $p['active'] && $p['kind'] === 'discount' && ($p['starts_at'] === null || Clock::ts($p['starts_at']) <= $now)
                && ($p['ends_at'] === null || Clock::ts($p['ends_at']) > $now) && ($p['max_uses'] === null || (int)$p['uses'] < (int)$p['max_uses']) && trim((string)$p['users']) === '';
            if ($live) {
                $plan = $p['plan_code'] ? Plans::byCode($p['plan_code']) : null;
                $out['promo'] = ['code' => $p['code'], 'percent' => (int)$p['percent'], 'plan' => $plan['title'] ?? '', 'ends_at' => $p['ends_at'] === null ? null : Clock::ts($p['ends_at']),
                    'title' => $c['promo']['title'], 'text' => $c['promo']['text'], 'buy' => App::baseUrl() . '/pay/buy.php?promo=' . rawurlencode($p['code'])];
            }
        }
        $out['sections'] = $c['sections'];
        $out['texts'] = array_filter($c['texts'], fn($v) => $v !== '');
        $out['business'] = array_filter($c['business'], fn($v) => $v !== '');
        $out['contact'] = array_filter($c['contact'], fn($v) => $v !== '');
        $dl = [];
        foreach ($c['downloads'] as $k => $v) {
            if (is_array($v)) { $v = array_filter($v, fn($x) => $x !== ''); if ($v) $dl[$k] = $v; }
            elseif ($v !== '') $dl[$k] = $v;
        }
        $out['downloads'] = $dl;
        $out['buyUrl'] = App::baseUrl() . '/pay/buy.php';
        if ($c['ads']['on']) {
            $ads = [];
            foreach (self::AD_SLOTS as $slot) { $a = Ads::pick($slot, $countAds); if ($a !== null) $ads[$slot] = $a; }
            if ($ads) $out['ads'] = $ads;
        }
        return $out;
    }
}
