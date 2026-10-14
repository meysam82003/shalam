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
    public static function remember(string $code): void
    {
        $path = rtrim((string)parse_url(App::baseUrl(), PHP_URL_PATH), '/') . '/pay/';
        setcookie(self::COOKIE, $code, ['expires' => time() + 7200, 'path' => $path, 'secure' => true, 'httponly' => true, 'samesite' => 'Lax']);
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
        if (!self::cfg()['show_ads']) return null;
        if ((int)(Db::value('SELECT active FROM {placements} WHERE k = ?', [$placement]) ?? 0) !== 1) return null;
        $now = Clock::sql();
        foreach (Db::all('SELECT * FROM {ads} WHERE active = 1 AND (starts_at IS NULL OR starts_at <= ?) AND (ends_at IS NULL OR ends_at > ?) ORDER BY priority DESC, id', [$now, $now]) as $a) {
            if (!in_array($placement, explode(',', (string)$a['placements']), true)) continue;
            if ($a['total_cap'] !== null && (int)$a['impressions_total'] >= (int)$a['total_cap']) continue;
            $media = Ads::mediaUrl($a['media_id'] === null ? null : (int)$a['media_id']);
            $target = (string)$a['target_url'];
            if ($count) Ads::record([['event' => 'impression', 'source' => 'direct', 'placement' => $placement, 'ad_id' => (int)$a['id']]]);
            return ['title' => (string)$a['title'], 'body' => (string)$a['body'], 'cta' => (string)($a['cta_text'] ?? ''),
                'image' => $media !== null && $media['kind'] === 'image' ? $media['url'] : null,
                'url' => preg_match('#^https://#i', $target) && safe_target_url($target) ? $target : null];
        }
        return null;
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
            . '</main>' . ($script !== '' ? '<script nonce="' . self::nonce() . '">' . $script . '</script>' : '') . '</body></html>';
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
            . '<div class="meta"><div><span>پلن</span><b>' . e($plan['title'] ?? '') . '</b></div><div><span>مدت</span><b>' . e($plan ? self::duration($plan) : '') . '</b></div><div><span>روش پرداخت</span><b>' . e(preg_replace('/\s*\(.*\)$/u', '', $methodTitle)) . '</b></div></div></section>';
        if (!$open) {
            $body .= '<div class="note">' . self::icon('info') . '<div>' . ($order['status'] === 'paid' ? 'این سفارش پرداخت شده است.' : 'مهلت این سفارش تمام شده؛ از برنامه دوباره «خرید» را بزنید. اگر مبلغی کم شده، بانک طبق قانون ظرف ۷۲ ساعت آن را برمی‌گرداند.') . '</div></div>';
        } elseif (in_array((string)$order['method'], self::CARD_TO_CARD, true) && trim($c['card_note']) !== '') {
            $body .= '<div class="note">' . self::icon('info') . '<div>' . e($c['card_note']) . '</div></div>';
        }
        $notes = array_values(array_filter(array_map('trim', preg_split('/\r?\n/', (string)$c['before_notes']))));
        if ($notes) $body .= '<section class="card"><h2>پیش از پرداخت بدانید</h2><ul class="ticks">' . implode('', array_map(fn($n) => '<li>' . e($n) . '</li>', $notes)) . '</ul></section>';
        $body .= self::adBlock(self::ad('pay_before', !$demo));
        $back = 'divan://pay/' . $order['code'];
        $body .= '<div class="actions">'
            . ($open ? '<a class="btn gold" href="' . e((string)$order['pay_url']) . '" rel="noreferrer">' . self::icon('lock') . 'ادامه و پرداخت امن</a>' : '<a class="btn gold" href="' . e($back) . '">بازگشت به دیوان امتیاز</a>')
            . ($open ? '<a class="btn ghost" href="' . e($back) . '">انصراف و بازگشت</a>' : '')
            . '</div><p class="foot">شمارهٔ سفارش: <span class="ltr">' . e($order['code']) . '</span></p>';
        return self::page('پرداخت', $body, $demo);
    }

    // ------------------------------------------------------------ after payment
    public static function renderReturn(?array $order, bool $demo = false, string $demoState = 'paid'): string
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
        $body = '<section class="card hero"><div class="medal ' . $cls . '">' . self::icon($icon) . '</div><h1 class="' . $cls . '">' . e($title) . '</h1><p class="sub">' . e($text) . '</p>';
        if ($state === 'pending') $body .= '<div class="wait-line"><span class="dots"><i></i><i></i><i></i></span>بررسی خودکار…</div>';
        if ($order !== null) {
            $plan = $order['_plan'] ?? Plans::byId((int)$order['plan_id']);
            $rows = ['پلن' => e($plan['title'] ?? ''), 'مبلغ' => e(money((int)$order['amount_toman']))];
            if (!empty($order['ref_id'])) $rows['کد پیگیری'] = '<span class="ltr">' . e($order['ref_id']) . '</span>';
            if ($state === 'paid') {
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
        if ($state === 'paid' && trim($c['after_note']) !== '') $body .= '<div class="note teal">' . self::icon('ok') . '<div>' . e($c['after_note']) . '</div></div>';
        $body .= self::adBlock(self::ad('pay_after', !$demo));
        $back = $order !== null ? 'divan://pay/' . $order['code'] : 'divan://pro';
        $body .= '<div class="actions"><a class="btn gold" href="' . e($back) . '">بازگشت به دیوان امتیاز</a></div>';
        // Pending: asks our server (which asks the gateway) every few seconds and reloads once the result is known.
        $script = '';
        $attr = '';
        if ($state === 'pending' && !$demo && $order !== null) {
            $attr = ' data-order="' . e($order['code']) . '"';
            $script = "(function(){var n=0,o=document.body.getAttribute('data-order');function t(){if(++n>45)return;fetch('state.php?o='+encodeURIComponent(o),{cache:'no-store',credentials:'same-origin'}).then(function(r){return r.json()}).then(function(j){if(j&&j.status&&j.status!=='pending'&&j.status!=='created'){location.replace('return.php?o='+encodeURIComponent(o))}else{setTimeout(t,4000)}})['catch'](function(){setTimeout(t,7000)})}setTimeout(t,3000)})();";
        }
        return self::page($title, $body, $demo, $attr, $script);
    }
}
