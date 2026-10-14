<?php
/**
 * Divan's own pages around a web payment (site, Windows, web and iPhone versions): the page before the gateway
 * (amount, notes, an optional offer) and the page the gateway sends the buyer back to (the real result, asked from
 * the gateway again, updated live). Texts and offers come from the panel; neither page ever decides a payment.
 */
declare(strict_types=1);

final class PayPages
{
    public const COOKIE = 'divan_pay';

    /** Methods paid by card-to-card: the buyer must send the exact amount the gateway page shows. */
    public const CARD_TO_CARD = ['blupal', 'aban'];

    public static function defaults(): array
    {
        return [
            'before_title' => 'یک قدم تا دیوان Pro',
            'before_notes' => "پرداخت در صفحهٔ امن درگاه انجام می‌شود؛ دیوان امتیاز هیچ‌وقت اطلاعات کارت شما را نمی‌بیند.\nPro بلافاصله پس از تأیید پرداخت روی حساب شما فعال می‌شود و با تعویض گوشی از بین نمی‌رود.\nاگر وسط کار ارتباط قطع شد نگران نباشید؛ پرداخت از سمت سرور بررسی و خودکار ثبت می‌شود.",
            'card_note' => 'در کارت به کارت، دقیقاً همان مبلغی را واریز کنید که صفحهٔ بعد نشان می‌دهد (چند ریال با مبلغ پلن فرق دارد)؛ همین تفاوت کوچک پرداخت شما را خودکار شناسایی می‌کند.',
            'after_note' => 'سپاس از اینکه با خرید Pro از دیوان امتیاز حمایت می‌کنید. همهٔ قابلیت‌ها همین حالا در برنامه باز است.',
            'show_ads' => true,
        ];
    }

    public static function cfg(): array { return (array)Settings::get('pay_pages', []) + self::defaults(); }

    private static ?string $nonce = null;

    public static function nonce(): string { return self::$nonce ??= rtrim(strtr(base64_encode(random_bytes(18)), '+/', '-_'), '='); }

    public static function headers(): void
    {
        header('Content-Type: text/html; charset=utf-8');
        header('Cache-Control: no-store');
        header('X-Content-Type-Options: nosniff');
        header('Referrer-Policy: no-referrer');
        header("Content-Security-Policy: default-src 'none'; style-src 'self'; font-src 'self'; img-src 'self' data:; script-src 'nonce-" . self::nonce() . "'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
    }

    /** Remembers the order for the return page (gateways with a fixed return address, like Blupal's «Callback page»). */
    public static function remember(string $code, int $days = 0): void
    {
        $path = rtrim((string)parse_url(App::baseUrl(), PHP_URL_PATH), '/') . '/pay/';
        setcookie(self::COOKIE, $code, ['expires' => time() + ($days > 0 ? $days * 86400 : 7200), 'path' => $path, 'secure' => true, 'httponly' => true, 'samesite' => 'Lax']);
    }

    /** Orders started on the site (not in the app): no app to go back to. */
    private static function fromSite(array $order): bool { return in_array($order['buyer'] ?? 'app', ['site', 'code'], true); }

    public static function siteHome(): string
    {
        $p = (string)parse_url(App::baseUrl(), PHP_URL_PATH);
        return rtrim(substr(App::baseUrl(), 0, strlen(App::baseUrl()) - strlen($p)), '/') . '/';
    }

    /**
     * The order a return visit is about: «o» (our return address), else the gateway's invoice id when the gateway adds
     * one to a fixed return address, else the order remembered by the page before payment.
     */
    public static function findOrder(array $get, array $cookie): ?array
    {
        $o = PayOrders::byCode((string)($get['o'] ?? ''));
        if ($o !== null) return $o;
        $m = (string)($get['m'] ?? '');
        if ($m !== '' && PayRegistry::get($m) !== null) {
            foreach (['invoice_id', 'invoiceId', 'invoice', 'id', 'inv'] as $k) {
                $ref = (string)($get[$k] ?? '');
                if (preg_match('/^[A-Za-z0-9_-]{1,80}$/', $ref)) {
                    $row = Db::one('SELECT * FROM {pay_orders} WHERE method = ? AND gateway_ref = ?', [$m, $ref]);
                    if ($row !== null) return $row;
                }
            }
        }
        return PayOrders::byCode((string)($cookie[self::COOKIE] ?? ''));
    }

    /** One active offer for a payment-page slot (panel → ads, slot «pay_before» / «pay_after»). */
    public static function ad(string $placement, bool $count = true): ?array
    {
        return self::cfg()['show_ads'] ? Ads::pick($placement, $count) : null;
    }

    // ------------------------------------------------------------ pieces
    private static function crown(string $fill = '#F0C565'): string
    {
        return '<svg viewBox="0 0 48 38" aria-hidden="true"><defs><linearGradient id="cg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFF3C8"/><stop offset=".55" stop-color="' . $fill . '"/><stop offset="1" stop-color="#B1832F"/></linearGradient></defs>'
            . '<path fill="url(#cg)" d="M4 30 1 8l12 9 11-15 11 15 12-9-3 22z"/><rect x="4" y="31.5" width="40" height="5" rx="2.5" fill="url(#cg)"/>'
            . '<circle cx="24" cy="20" r="3.2" fill="#2ED3C6"/><circle cx="13" cy="23" r="2.2" fill="#D0414B"/><circle cx="35" cy="23" r="2.2" fill="#D0414B"/></svg>';
    }

    private static function icon(string $name): string
    {
        $p = [
            'ok' => '<circle cx="24" cy="24" r="21" fill="none" stroke="#8EF0E6" stroke-width="3"/><path d="M14 25l7 7 14-15" fill="none" stroke="#E6FFFB" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"/>',
            'bad' => '<circle cx="24" cy="24" r="21" fill="none" stroke="#F5868C" stroke-width="3"/><path d="M16 16l16 16M32 16 16 32" stroke="#FFE2E4" stroke-width="4.5" stroke-linecap="round"/>',
            'wait' => '<path d="M14 6h20M14 42h20M16 6c0 10 16 12 16 18S16 32 16 42M32 6c0 10-16 12-16 18s16 8 16 18" fill="none" stroke="#FBE3A6" stroke-width="3" stroke-linecap="round"/><path d="M19 38c2-4 8-4 10 0z" fill="#E6BE6A"/>',
            'lock' => '<rect x="10" y="21" width="28" height="21" rx="5" fill="none" stroke="currentColor" stroke-width="3.5"/><path d="M16 21v-6a8 8 0 0 1 16 0v6" fill="none" stroke="currentColor" stroke-width="3.5"/>',
            'info' => '<circle cx="24" cy="24" r="20" fill="none" stroke="currentColor" stroke-width="3.5"/><path d="M24 22v13M24 14v1" stroke="currentColor" stroke-width="4" stroke-linecap="round"/>',
        ][$name];
        return '<svg viewBox="0 0 48 48" aria-hidden="true">' . $p . '</svg>';
    }

    /**
     * «Back to Divan» in the web, Windows and iPhone versions, where this page is a separate window: tells the app's
     * window (which asks the server for the result) and closes this one. Inside the Android app the divan:// link
     * itself closes the payment view.
     */
    private const BACK_SCRIPT = "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[href^=\"divan://\"]');if(!a||!window.opener)return;e.preventDefault();try{window.opener.postMessage({divan:'back',href:a.getAttribute('href')},'*');}catch(x){}window.close();});";

    private static function page(string $title, string $body, bool $demo, string $extraBodyAttr = '', string $script = ''): string
    {
        $css = App::baseUrl() . '/assets/css/pay.css?v=1';
        return '<!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
            . '<meta name="robots" content="noindex,nofollow"><meta name="theme-color" content="#050B1A"><meta name="referrer" content="no-referrer">'
            . '<title>' . e($title) . ' — دیوان امتیاز</title><link rel="stylesheet" href="' . e($css) . '"></head>'
            . '<body' . $extraBodyAttr . '><main class="wrap"><div class="brand">' . self::crown() . '<span>دیوان امتیاز</span></div>'
            . ($demo ? '<div class="ribbon">نمونهٔ نمایشی — پرداختی انجام نمی‌شود</div>' : '')
            . $body
            . '<p class="foot"><span class="lock">' . self::icon('lock') . 'پرداخت امن · اطلاعات کارت هرگز در دیوان امتیاز ذخیره نمی‌شود</span></p>'
            . '</main><script nonce="' . self::nonce() . '">' . self::BACK_SCRIPT . $script . '</script></body></html>';
    }

    private static function adBlock(?array $ad): string
    {
        if ($ad === null) return '';
        $img = $ad['image'] !== null ? '<img src="' . e($ad['image']) . '" alt="" loading="lazy">' : '';
        $more = $ad['url'] !== null ? '<a class="more" href="' . e($ad['url']) . '" rel="noopener noreferrer">' . e($ad['cta'] !== '' ? $ad['cta'] : 'بیشتر بدانید') . ' ←</a>' : '';
        return '<section class="card ad"><span class="tag">پیشنهاد ویژه</span>' . $img . '<div class="in"><h3>' . e($ad['title']) . '</h3>'
            . ($ad['body'] !== '' ? '<p>' . nl2br(e($ad['body'])) . '</p>' : '') . $more . '</div></section>';
    }

    private static function duration(array $plan): string
    {
        if ($plan['duration_days'] === null) return 'همیشگی';
        $d = (int)$plan['duration_days'];
        return $d % 30 === 0 ? fa_digits((string)($d / 30)) . ' ماه' : fa_digits((string)$d) . ' روز';
    }

    private static function demoOrder(string $status = 'pending'): array
    {
        $plan = Plans::byCode('m3') ?? ['id' => 0, 'code' => 'm3', 'title' => 'اشتراک سه‌ماهه', 'duration_days' => 90, 'base_price_toman' => 49000, 'description' => ''];
        return ['code' => 'demo0000000000000000', 'status' => $status, 'method' => 'blupal', 'plan_id' => $plan['id'], 'amount_toman' => (int)$plan['base_price_toman'],
            'ref_id' => $status === 'paid' ? 'BLU-48213907' : null, 'pay_url' => '#', 'user_id' => 0, 'expires_at' => null, '_plan' => $plan];
    }

    // ------------------------------------------------------------ before payment
    public static function renderStart(?array $order, bool $demo = false): string
    {
        $c = self::cfg();
        if ($demo) $order = self::demoOrder();
        if ($order === null) return self::page('سفارش پیدا نشد', '<section class="card hero"><div class="medal bad">' . self::icon('bad') . '</div><h1 class="bad">سفارش پیدا نشد</h1><p class="sub">از برنامه دوباره «خرید» را بزنید.</p></section>', false);
        $plan = $order['_plan'] ?? Plans::byId((int)$order['plan_id']);
        $a = PayRegistry::get((string)$order['method']);
        $methodTitle = $a !== null ? $a->title() : (string)$order['method'];
        $open = $demo || ($order['status'] === 'pending' && !empty($order['pay_url']) && ($order['expires_at'] === null || Clock::ts($order['expires_at']) > Clock::now()));
        $body = '<section class="card hero"><div class="medal crown">' . self::crown() . '</div>'
            . '<h1>' . e($c['before_title']) . '</h1><p class="sub">' . e($plan['title'] ?? '') . '</p>'
            . '<div class="amount"><span class="label">مبلغ قابل پرداخت</span><b>' . fa_digits(number_format((int)$order['amount_toman'])) . '</b><span class="unit">تومان</span></div>'
            . ((int)($order['list_toman'] ?? 0) > (int)$order['amount_toman'] ? '<p class="discount"><s>' . e(money((int)$order['list_toman'])) . '</s> <b>' . fa_digits((string)(int)round(((int)$order['list_toman'] - (int)$order['amount_toman']) * 100 / (int)$order['list_toman'])) . '٪ تخفیف</b> با کد تخفیف</p>' : '')
            . '<div class="meta"><div><span>پلن</span><b>' . e($plan['title'] ?? '') . '</b></div><div><span>مدت</span><b>' . e($plan ? self::duration($plan) : '') . '</b></div><div><span>روش پرداخت</span><b>' . e(preg_replace('/\s*\(.*\)$/u', '', $methodTitle)) . '</b></div></div></section>';
        if (!$open) {
            $body .= '<div class="note">' . self::icon('info') . '<div>' . ($order['status'] === 'paid' ? 'این سفارش پرداخت شده است.' : 'مهلت این سفارش تمام شده؛ از برنامه دوباره «خرید» را بزنید. اگر مبلغی کم شده، بانک طبق قانون ظرف ۷۲ ساعت آن را برمی‌گرداند.') . '</div></div>';
        } elseif (in_array((string)$order['method'], self::CARD_TO_CARD, true) && trim($c['card_note']) !== '') {
            $body .= '<div class="note">' . self::icon('info') . '<div>' . e($c['card_note']) . '</div></div>';
        }
        $notes = array_values(array_filter(array_map('trim', preg_split('/\r?\n/', (string)$c['before_notes']))));
        if ($notes) $body .= '<section class="card"><h2>پیش از پرداخت بدانید</h2><ul class="ticks">' . implode('', array_map(fn($n) => '<li>' . e($n) . '</li>', $notes)) . '</ul></section>';
        $body .= self::adBlock(self::ad('pay_before', !$demo));
        $back = self::fromSite($order) ? App::baseUrl() . '/pay/buy.php' : 'divan://pay/' . $order['code'];
        $body .= '<div class="actions">'
            . ($open ? '<a class="btn gold" href="' . e((string)$order['pay_url']) . '" rel="noreferrer">' . self::icon('lock') . 'ادامه و پرداخت امن</a>' : '<a class="btn gold" href="' . e($back) . '">' . (self::fromSite($order) ? 'بازگشت به صفحهٔ خرید' : 'بازگشت به دیوان امتیاز') . '</a>')
            . ($open ? '<a class="btn ghost" href="' . e($back) . '">انصراف و بازگشت</a>' : '')
            . '</div><p class="foot">شمارهٔ سفارش: <span class="ltr">' . e($order['code']) . '</span></p>';
        return self::page('پرداخت', $body, $demo);
    }

    // ------------------------------------------------------------ after payment
    public static function renderReturn(?array $order, bool $demo = false, string $demoState = 'paid', bool $buyerBrowser = false): string
    {
        $c = self::cfg();
        if ($demo) $order = self::demoOrder(in_array($demoState, ['paid', 'pending', 'failed'], true) ? $demoState : 'paid');
        $state = $order['status'] ?? 'unknown';
        if (in_array($state, ['pending', 'created', 'unknown'], true)) $state = $order === null ? 'unknown' : 'pending';
        [$cls, $icon, $title, $text] = [
            'paid' => ['ok', 'ok', 'پرداخت با موفقیت انجام شد', 'دیوان Pro روی حساب شما فعال شد. حالا به برنامه برگردید و از همهٔ قابلیت‌ها لذت ببرید.'],
            'failed' => ['bad', 'bad', 'پرداخت انجام نشد', 'اگر مبلغی از حساب شما کم شده، بانک طبق قانون ظرف ۷۲ ساعت آن را برمی‌گرداند. می‌توانید از برنامه دوباره خرید کنید.'],
            'expired' => ['bad', 'bad', 'مهلت پرداخت تمام شد', 'از برنامه دوباره «خرید» را بزنید.'],
            'canceled' => ['bad', 'bad', 'سفارش لغو شد', 'از برنامه دوباره «خرید» را بزنید.'],
            'pending' => ['wait', 'wait', 'در حال بررسی پرداخت', 'پرداخت شما از درگاه استعلام می‌شود. این صفحه خودکار به‌روز می‌شود؛ لازم نیست کاری انجام دهید.'],
            'unknown' => ['wait', 'wait', 'به دیوان امتیاز برگردید', 'نتیجهٔ پرداخت چند لحظهٔ دیگر در برنامه نمایش داده می‌شود.'],
        ][$state] ?? ['wait', 'wait', 'در حال بررسی پرداخت', ''];
        if ($state === 'paid' && $order !== null && ($order['buyer'] ?? 'app') === 'code') $text = 'پرداخت تأیید شد. کد فعال‌سازی شما آماده است؛ آن را در برنامه وارد کنید تا اشتراک روی حسابتان فعال شود.';
        if ($state === 'paid' && $order !== null && ($order['buyer'] ?? 'app') === 'site') $text = 'پرداخت تأیید شد و اشتراک به حساب شما اضافه شد.';
        $body = '<section class="card hero"><div class="medal ' . $cls . '">' . self::icon($icon) . '</div><h1 class="' . $cls . '">' . e($title) . '</h1><p class="sub">' . e($text) . '</p>';
        if ($state === 'pending') $body .= '<div class="wait-line"><span class="dots"><i></i><i></i><i></i></span>بررسی خودکار…</div>';
        if ($order !== null) {
            $plan = $order['_plan'] ?? Plans::byId((int)$order['plan_id']);
            $rows = ['پلن' => e($plan['title'] ?? ''), 'مبلغ' => e(money((int)$order['amount_toman']))];
            if (!empty($order['ref_id'])) $rows['کد پیگیری'] = '<span class="ltr">' . e($order['ref_id']) . '</span>';
            if ($state === 'paid' && ($order['buyer'] ?? 'app') === 'code') {
                $rows['کد فعال‌سازی'] = $order['redeemed_by'] !== null ? 'استفاده شده' : 'آمادهٔ استفاده';
            } elseif ($state === 'paid') {
                if ($demo) $rows['اعتبار Pro تا'] = e(jdate(Clock::sql(Clock::now() + 90 * 86400), false));
                else {
                    $sum = Entitlements::summary((int)$order['user_id']);
                    $rows['اعتبار Pro تا'] = $sum['lifetime'] ? 'همیشگی' : (!empty($sum['expires_at']) ? e(jdate(Clock::sql((int)$sum['expires_at']), false)) : '—');
                }
            }
            $rows['شمارهٔ سفارش'] = '<span class="ltr">' . e($order['code']) . '</span>';
            $body .= '<div class="rows">' . implode('', array_map(fn($k, $v) => '<div><span>' . e($k) . '</span><b>' . $v . '</b></div>', array_keys($rows), $rows)) . '</div>';
        }
        $body .= '</section>';
        $script = '';
        if ($state === 'paid' && $order !== null && ($order['buyer'] ?? 'app') === 'code') {
            // The code is shown only in the browser that made the purchase (its cookie); support can look it up by order number.
            $code = $buyerBrowser ? Crypto::decrypt($order['activation_enc'] ?? null) : null;
            if ($code !== null && $order['redeemed_by'] === null) {
                $body .= '<section class="card code-card"><h2>کد فعال‌سازی شما</h2><div class="code ltr" id="act">' . e($code) . '</div>'
                    . '<div class="code-actions"><button type="button" class="btn ghost" id="act-copy">کپی کد</button><button type="button" class="btn ghost" id="act-save">ذخیرهٔ کد در فایل</button></div>'
                    . '<ol class="steps"><li>دیوان امتیاز را باز کنید و با حساب خودتان وارد شوید (اگر حساب ندارید، فقط نام کاربری و رمز می‌خواهد).</li>'
                    . '<li>به «دیوان Pro» بروید و «کد تخفیف یا هدیه» را بزنید.</li><li>همین کد را وارد کنید؛ اشتراک فوراً روی حسابتان فعال می‌شود.</li></ol>'
                    . '<p class="hint">کد فقط یک بار استفاده می‌شود و تا وقتی استفاده نشده، هر زمان معتبر است. آن را جای امنی نگه دارید و به کسی ندهید.</p></section>';
                $script = "(function(){var c=document.getElementById('act');if(!c)return;var v=c.textContent.trim();document.getElementById('act-copy').addEventListener('click',function(e){var b=e.currentTarget;(navigator.clipboard?navigator.clipboard.writeText(v):Promise.reject()).then(function(){b.textContent='کپی شد ✓';},function(){var r=document.createRange();r.selectNodeContents(c);var s=getSelection();s.removeAllRanges();s.addRange(r);});});document.getElementById('act-save').addEventListener('click',function(){var t='کد فعال‌سازی دیوان امتیاز\\n'+v+'\\n\\nدر برنامه: دیوان Pro ← کد تخفیف یا هدیه\\n';var a=document.createElement('a');a.href=URL.createObjectURL(new Blob([t],{type:'text/plain;charset=utf-8'}));a.download='divan-activation-code.txt';document.body.appendChild(a);a.click();a.remove();});})();";
            } elseif ($order['redeemed_by'] === null) {
                $body .= '<div class="note">' . self::icon('info') . '<div>کد فعال‌سازی فقط در همان مرورگری نمایش داده می‌شود که خرید با آن انجام شد. اگر به آن دسترسی ندارید، با شمارهٔ سفارش <span class="ltr">' . e($order['code']) . '</span> به پشتیبانی پیام دهید.</div></div>';
            }
        } elseif ($state === 'paid' && $order !== null && ($order['buyer'] ?? 'app') === 'site') {
            $u = Users::byId((int)$order['user_id']);
            $body .= '<div class="note teal">' . self::icon('ok') . '<div>اشتراک به حساب «<b class="ltr">' . e($u['username'] ?? '') . '</b>» اضافه شد. در برنامه با همین حساب وارد شوید (دیوان Pro ← اشتراک من) تا فعال شود.</div></div>';
        }
        if ($state === 'paid' && ($order['buyer'] ?? 'app') !== 'code' && trim($c['after_note']) !== '') $body .= '<div class="note teal">' . self::icon('ok') . '<div>' . e($c['after_note']) . '</div></div>';
        $body .= self::adBlock(self::ad('pay_after', !$demo));
        if ($order !== null && self::fromSite($order)) {
            $body .= '<div class="actions"><a class="btn gold" href="' . e(self::siteHome() . 'download.html') . '">دریافت برنامهٔ دیوان امتیاز</a>'
                . ($state === 'paid' ? '' : '<a class="btn ghost" href="' . e(App::baseUrl() . '/pay/buy.php') . '">بازگشت به صفحهٔ خرید</a>') . '</div>';
        } else {
            $back = $order !== null ? 'divan://pay/' . $order['code'] : 'divan://pro';
            $body .= '<div class="actions"><a class="btn gold" href="' . e($back) . '">بازگشت به دیوان امتیاز</a></div>';
        }
        // Pending: asks our server (which asks the gateway) every few seconds and reloads once the result is known.
        $attr = '';
        if ($state === 'pending' && !$demo && $order !== null) {
            $attr = ' data-order="' . e($order['code']) . '"';
            $script = "(function(){var n=0,o=document.body.getAttribute('data-order');function t(){if(++n>45)return;fetch('state.php?o='+encodeURIComponent(o),{cache:'no-store',credentials:'same-origin'}).then(function(r){return r.json()}).then(function(j){if(j&&j.status&&j.status!=='pending'&&j.status!=='created'){location.replace('return.php?o='+encodeURIComponent(o))}else{setTimeout(t,4000)}})['catch'](function(){setTimeout(t,7000)})}setTimeout(t,3000)})();";
        }
        return self::page($title, $body, $demo, $attr, $script);
    }

    // ------------------------------------------------------------ buying on the site
    /** Signed, time-limited token of the purchase form (no session needed). */
    public static function formToken(): string { $t = (string)time(); return $t . '.' . substr(Crypto::hash('buyform|' . $t), 0, 32); }

    public static function validFormToken(string $tok): bool
    {
        $p = explode('.', $tok);
        if (count($p) !== 2 || !ctype_digit($p[0]) || abs(time() - (int)$p[0]) > 7200) return false;
        return hash_equals(substr(Crypto::hash('buyform|' . $p[0]), 0, 32), $p[1]);
    }

    /** The site's purchase page: plan, payment method, and either a username or an activation code. */
    public static function renderBuy(?string $error = null, array $old = []): string
    {
        $plans = array_values(array_filter(Plans::offers('direct'), fn($p) => !empty($p['purchasable'])));
        $methods = PayRegistry::methodsFor('direct');
        $body = '<section class="card hero"><div class="medal crown">' . self::crown() . '</div><h1>خرید دیوان Pro از سایت</h1>'
            . '<p class="sub">بدون نصب یا ورود؛ پس از پرداخت، اشتراک به حساب شما اضافه می‌شود یا یک کد فعال‌سازی می‌گیرید که در برنامه وارد می‌کنید.</p></section>';
        if (!$plans || !$methods) {
            $body .= '<div class="note">' . self::icon('info') . '<div>امکان خرید از سایت موقتاً در دسترس نیست. کمی بعد دوباره سر بزنید یا از داخل برنامه خرید کنید.</div></div>';
            return self::page('خرید اشتراک', $body, false);
        }
        if ($error !== null) $body .= '<div class="err">' . e($error) . '</div>';
        $sel = (string)($old['plan'] ?? ($plans[0]['code'] ?? ''));
        $selM = (string)($old['method'] ?? ($methods[0]['id'] ?? ''));
        $body .= '<form method="post" action="buy.php" autocomplete="off"><input type="hidden" name="t" value="' . e(self::formToken()) . '">';
        $body .= '<section class="card"><h2>پلن</h2><div class="plans">';
        foreach ($plans as $p) {
            $body .= '<label class="opt"><input type="radio" name="plan" value="' . e($p['code']) . '"' . ($p['code'] === $sel ? ' checked' : '') . '><span class="t"><b>' . e($p['title'])
                . (!empty($p['badge_text']) ? '<span class="tag-badge">' . e($p['badge_text']) . '</span>' : '') . '</b><small>' . e($p['description'] ?? '') . '</small></span>'
                . '<span class="p">' . e(money((int)$p['final_price'])) . '</span></label>';
        }
        $body .= '</div></section><section class="card"><h2>روش پرداخت</h2><div class="plans">';
        foreach ($methods as $m) {
            $body .= '<label class="opt"><input type="radio" name="method" value="' . e($m['id']) . '"' . ($m['id'] === $selM ? ' checked' : '') . '><span class="t"><b>' . e($m['title']) . '</b><small>' . e($m['description']) . '</small></span></label>';
        }
        $body .= '</div></section><section class="card"><h2>برای کدام حساب؟</h2>'
            . '<p class="sub" style="text-align:right">اگر در برنامه حساب دارید، نام کاربری‌اش را بنویسید تا اشتراک مستقیم به همان حساب اضافه شود (رمز لازم نیست). خالی بگذارید تا پس از پرداخت یک <b>کد فعال‌سازی</b> بگیرید و در برنامه، روی هر حسابی که خواستید، فعالش کنید.</p>'
            . '<input class="field-in" type="text" name="username" maxlength="40" placeholder="نام کاربری (اختیاری)" value="' . e((string)($old['username'] ?? '')) . '" spellcheck="false" autocapitalize="off"></section>'
            . '<section class="card"><h2>کد تخفیف</h2><p class="sub" style="text-align:right">اگر کد تخفیف دارید اینجا بنویسید؛ مبلغ کم‌شده در صفحهٔ بعد، پیش از پرداخت نشان داده می‌شود.</p>'
            . '<input class="field-in ltr" type="text" name="promo" maxlength="40" placeholder="کد تخفیف (اختیاری)" value="' . e((string)($old['promo'] ?? '')) . '" spellcheck="false" autocapitalize="characters"></section>'
            . '<label class="agree"><input type="checkbox" name="agree" value="1" required' . (!empty($old['agree']) ? ' checked' : '') . '> <span><a href="' . e(self::siteHome() . 'pro.html') . '" target="_blank" rel="noopener">شرایط خرید اشتراک</a> و <a href="' . e(self::siteHome() . 'refund.html') . '" target="_blank" rel="noopener">قوانین بازپرداخت</a> را خوانده‌ام و می‌پذیرم.</span></label>'
            . '<div class="actions"><button class="btn gold" type="submit">' . self::icon('lock') . 'ادامه و پرداخت امن</button><a class="btn ghost" href="' . e(self::siteHome()) . '">بازگشت به سایت</a></div></form>';
        return self::page('خرید اشتراک', $body, false);
    }
}
