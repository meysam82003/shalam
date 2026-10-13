<?php
/**
 * Divan Emtiaz server — payment methods registry.
 *
 * Every way of paying is an adapter with one contract. Which app version (channel) may show it is a hard
 * rule of the adapter itself: the Cafe Bazaar and Myket versions sell digital goods only through their store's
 * own billing (store rules), so web gateways exist only for the version downloaded from the site («direct»).
 * The panel can switch a method off, order it or limit it, but never show it where its adapter does not allow it.
 *
 * A web payment is decided only by the server: the order (price from the server) is created here, the gateway
 * is asked again with the stored amount, and one atomic status change grants the plan once.
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

/** What a gateway says about one order. */
final class PayCheck
{
    public const PAID = 'paid';
    public const PENDING = 'pending';
    public const FAILED = 'failed';
    public const UNAVAILABLE = 'unavailable';

    public string $state;
    public string $message;
    public ?string $refId;

    public function __construct(string $state, string $message = '', ?string $refId = null)
    {
        $this->state = $state; $this->message = $message; $this->refId = $refId;
    }
}

interface PayAdapter
{
    public function id(): string;
    public function title(): string;
    public function description(): string;
    /** «store» (the app's built-in store billing) or «web» (a page opened in the browser). */
    public function kind(): string;
    /** App versions this method may ever be shown in (hard rule, not a panel setting). */
    public function channels(): array;
    /** Settings the panel asks for: key => [label, secret?]. */
    public function fields(): array;
    public function configured(): bool;
    /** Connection test: [css class, message]. */
    public function test(): array;
    /** Starts a payment: ['ref' => gateway reference, 'url' => page to open]. */
    public function create(array $order, array $plan, string $callback): array;
    /** Asks the gateway again about an order (never trusts the browser's return). */
    public function check(array $order): PayCheck;
    /** Hosts the app may open for this method (mirrored in the app; anything else is refused). */
    public function payHosts(): array;
    public function supportsRefund(): bool;
}

/** The store billing built into the Bazaar and Myket apps (the purchase itself runs through Purchases::verify). */
final class StorePayAdapter implements PayAdapter
{
    private string $store;
    public function __construct(string $store) { $this->store = $store; }
    public function id(): string { return $this->store; }
    public function title(): string { return $this->store === 'myket' ? 'پرداخت درون‌برنامه‌ای مایکت' : 'پرداخت درون‌برنامه‌ای کافه‌بازار'; }
    public function description(): string { return 'پرداخت رسمی همان فروشگاهی که برنامه از آن نصب شده.'; }
    public function kind(): string { return 'store'; }
    public function channels(): array { return [$this->store]; }
    public function fields(): array { return []; }
    public function configured(): bool { return (bool)(Settings::get($this->store, [])['enabled'] ?? true); }
    public function test(): array { return PaymentBridge::testStore($this->store); }
    public function create(array $order, array $plan, string $callback): array { throw new ApiError('store_method', 'خرید فروشگاهی از داخل برنامه انجام می‌شود.'); }
    public function check(array $order): PayCheck { return new PayCheck(PayCheck::UNAVAILABLE, 'store'); }
    public function payHosts(): array { return []; }
    public function supportsRefund(): bool { return false; }
}

/**
 * ZarinPal payment gateway v4 (docs: zarinpal.com/docs/paymentGateway). Amounts are sent in rials with
 * currency IRR explicitly. Verify is called with the amount stored on the order; codes 100 and 101 mean paid.
 * ZarinPal accepts API calls from Iranian addresses, so by default the calls go through the gateway in Iran.
 */
final class ZarinpalAdapter implements PayAdapter
{
    public function id(): string { return 'zarinpal'; }
    public function title(): string { return 'درگاه بانکی زرین‌پال'; }
    public function description(): string { return 'پرداخت با همهٔ کارت‌های شتاب در صفحهٔ امن زرین‌پال.'; }
    public function kind(): string { return 'web'; }
    public function channels(): array { return ['direct']; }
    public function fields(): array { return ['merchant_id' => ['شناسهٔ پذیرنده (Merchant ID)', true]]; }
    private function merchant(): string { return Settings::secret('pay_zarinpal_merchant_id'); }
    private function sandbox(): bool { return (bool)(PayRegistry::cfg('zarinpal')['sandbox'] ?? false); }
    private function host(): string { return $this->sandbox() ? 'https://sandbox.zarinpal.com' : 'https://payment.zarinpal.com'; }
    public function configured(): bool
    {
        if (!preg_match('/^[0-9a-fA-F-]{36}$/', $this->merchant())) return false;
        return (PayRegistry::cfg('zarinpal')['route'] ?? 'bridge') === 'direct' || PaymentBridge::ready();
    }
    public function payHosts(): array { return ['payment.zarinpal.com', 'sandbox.zarinpal.com', 'www.zarinpal.com']; }
    public function supportsRefund(): bool { return false; }

    /** One call along the chosen route; returns the decoded JSON and the HTTP status. */
    private function call(string $what, array $body): array
    {
        if ((PayRegistry::cfg('zarinpal')['route'] ?? 'bridge') === 'bridge') {
            $r = PaymentBridge::call('zarinpal.' . $what, $body + ['sandbox' => $this->sandbox()]);
            if ($r['kind'] === 'store') return [(int)$r['status'], is_array($r['body'] ?? null) ? $r['body'] : null, ''];
            return [0, null, (string)($r['error'] ?? $r['kind'])];
        }
        $r = Http::request('POST', $this->host() . '/pg/v4/payment/' . $what . '.json', ['Content-Type: application/json', 'Accept: application/json'], json_encode($body), 20);
        $j = json_decode($r['body'], true);
        return [(int)$r['status'], is_array($j) ? $j : null, (string)$r['error']];
    }

    public function test(): array
    {
        if (!$this->configured()) return ['warn', 'شناسهٔ پذیرنده یا واسط ایران ثبت نشده است.'];
        // A made-up authority: a «not found» answer proves the address, the route and the merchant id are accepted.
        [$st, $j, $err] = $this->call('verify', ['merchant_id' => $this->merchant(), 'amount' => 10000, 'authority' => 'A' . str_repeat('0', 35)]);
        if ($st === 0) return ['bad', 'به زرین‌پال نرسید: ' . $err];
        $code = (int)($j['errors']['code'] ?? $j['data']['code'] ?? 0);
        if (in_array($code, [-9, -10, -11], true)) return ['bad', 'زرین‌پال شناسهٔ پذیرنده را نپذیرفت (کد ' . $code . ').'];
        return ['ok', 'ارتباط با زرین‌پال برقرار است (پاسخ آزمایشی: کد ' . $code . ').'];
    }

    public function create(array $order, array $plan, string $callback): array
    {
        [$st, $j, $err] = $this->call('request', [
            'merchant_id' => $this->merchant(), 'amount' => (int)$order['amount_rial'], 'currency' => 'IRR',
            'callback_url' => $callback, 'description' => 'دیوان امتیاز — ' . $plan['title'],
            'metadata' => ['order_id' => $order['code']],
        ]);
        $code = (int)($j['data']['code'] ?? 0);
        $authority = (string)($j['data']['authority'] ?? '');
        if ($st === 0) throw new ApiError('gateway_down', 'درگاه پرداخت در دسترس نیست؛ کمی بعد دوباره تلاش کنید.', 503);
        if ($code !== 100 || !preg_match('/^[A-Za-z0-9]{10,64}$/', $authority)) throw new ApiError('gateway_refused', 'درگاه پرداخت درخواست را نپذیرفت (کد ' . (int)($j['errors']['code'] ?? $code) . ').', 502);
        return ['ref' => $authority, 'url' => $this->host() . '/pg/StartPay/' . $authority];
    }

    public function check(array $order): PayCheck
    {
        [$st, $j, $err] = $this->call('verify', ['merchant_id' => $this->merchant(), 'amount' => (int)$order['amount_rial'], 'authority' => (string)$order['gateway_ref']]);
        if ($st === 0 || $st >= 500 || $st === 429) return new PayCheck(PayCheck::UNAVAILABLE, 'زرین‌پال پاسخ نداد: ' . ($err ?: 'HTTP ' . $st));
        $code = (int)($j['data']['code'] ?? 0);
        if ($code === 100 || $code === 101) return new PayCheck(PayCheck::PAID, '', isset($j['data']['ref_id']) ? (string)$j['data']['ref_id'] : null);
        $e = (int)($j['errors']['code'] ?? 0);
        // -51: payment not successful; -50: amount differs from the order; -54/-55: unknown or expired authority.
        if (in_array($e, [-50, -51, -54, -55], true)) return new PayCheck(PayCheck::FAILED, 'زرین‌پال پرداخت را تأیید نکرد (کد ' . $e . ').');
        return new PayCheck(PayCheck::PENDING, 'پاسخ نامشخص زرین‌پال (کد ' . $e . ').');
    }
}

final class PayRegistry
{
    /** Order of display when the panel sets none. */
    public static function adapters(): array
    {
        return ['bazaar' => new StorePayAdapter('bazaar'), 'myket' => new StorePayAdapter('myket'), 'zarinpal' => new ZarinpalAdapter()];
    }

    public static function get(string $id): ?PayAdapter { return self::adapters()[$id] ?? null; }

    /** Panel settings of one method: enabled, sort, min, max (toman), sandbox, route, kill. */
    public static function cfg(string $id): array
    {
        $d = ['enabled' => in_array($id, ['bazaar', 'myket'], true), 'sort' => 0, 'min' => null, 'max' => null, 'sandbox' => false, 'route' => 'bridge', 'kill' => false];
        return (array)Settings::get('pay_' . $id, []) + $d;
    }

    /**
     * Where a method stands: ready, disabled (switched off), killed (emergency stop), needs_config,
     * not_allowed (this app version may never show it).
     */
    public static function status(string $id, ?string $channel = null): string
    {
        $a = self::get($id);
        if ($a === null) return 'not_allowed';
        if ($channel !== null && !in_array($channel, $a->channels(), true)) return 'not_allowed';
        $c = self::cfg($id);
        if ($c['kill']) return 'killed';
        if (!$c['enabled']) return 'disabled';
        if (!$a->configured()) return 'needs_config';
        return 'ready';
    }

    /** Methods the app may show now for this version and plan price (nothing about the others). */
    public static function methodsFor(string $channel, ?array $plan = null): array
    {
        $out = [];
        foreach (self::adapters() as $id => $a) {
            if (self::status($id, $channel) !== 'ready') continue;
            $c = self::cfg($id);
            if ($plan !== null) {
                $price = self::price($plan, $channel);
                if (($c['min'] !== null && $price < (int)$c['min']) || ($c['max'] !== null && $price > (int)$c['max'])) continue;
            }
            $out[] = ['id' => $id, 'kind' => $a->kind(), 'title' => $a->title(), 'description' => $a->description(), 'sort' => (int)$c['sort'], 'hosts' => $a->payHosts()];
        }
        usort($out, fn($x, $y) => $x['sort'] <=> $y['sort']);
        return $out;
    }

    /** Price of a plan in a channel (toman). The site version uses the plan's base price. */
    public static function price(array $plan, string $channel): int
    {
        return $channel === 'direct' ? (int)$plan['base_price_toman'] : Plans::storePrice($plan, $channel);
    }

    public static function note(string $id, bool $ok, string $msg = ''): void
    {
        $s = (array)Settings::get('pay_status_' . $id, []);
        $s = $ok ? ['last_ok_at' => time()] + $s : ['last_error_at' => time(), 'last_error' => mb_substr($msg, 0, 300)] + $s;
        Settings::set('pay_status_' . $id, $s);
    }
}

/** Orders of web payments: created here, settled only after the gateway confirms the stored amount. */
final class PayOrders
{
    public const CHANNELS = ['bazaar', 'myket', 'direct'];

    public static function byCode(string $code): ?array
    {
        if (!preg_match('/^[a-z0-9]{20}$/', $code)) return null;
        return Db::one('SELECT * FROM {pay_orders} WHERE code = ?', [$code]);
    }

    public static function create(array $user, string $method, string $planCode, string $channel): array
    {
        if (!in_array($channel, self::CHANNELS, true)) throw new ApiError('bad_channel', 'نسخهٔ برنامه نامعتبر است.');
        if (!RateLimit::hit('payorder:' . $user['id'], 20, 3600)) throw new ApiError('rate_limited', 'درخواست‌های پرداخت زیاد است؛ کمی بعد.', 429);
        $a = PayRegistry::get($method);
        if ($a === null || $a->kind() !== 'web' || PayRegistry::status($method, $channel) !== 'ready') throw new ApiError('method_unavailable', 'امکان خرید موقتاً در دسترس نیست.', 409);
        $plan = Plans::byCode($planCode);
        if ($plan === null || !$plan['active']) throw new ApiError('bad_plan', 'این پلن در دسترس نیست.', 404);
        if (!in_array($method, array_column(PayRegistry::methodsFor($channel, $plan), 'id'), true)) throw new ApiError('method_unavailable', 'امکان خرید موقتاً در دسترس نیست.', 409);
        $sum = Entitlements::summary((int)$user['id']);
        if ($sum['lifetime']) throw new ApiError('already_lifetime', 'شما اشتراک مادام‌العمر دارید؛ خرید دوباره لازم نیست.', 409);
        $toman = PayRegistry::price($plan, $channel);
        if ($toman < 1000) throw new ApiError('bad_price', 'قیمت این پلن ثبت نشده است.', 409);
        $code = substr(strtolower(preg_replace('/[^a-z0-9]/i', '', Crypto::token(24))), 0, 20);
        $now = Clock::sql();
        $id = Db::insert('pay_orders', [
            'code' => $code, 'user_id' => $user['id'], 'plan_id' => $plan['id'], 'method' => $method, 'channel' => $channel,
            'amount_toman' => $toman, 'amount_rial' => $toman * 10, 'status' => 'created',
            'expires_at' => Clock::sql(Clock::now() + 3600), 'created_at' => $now, 'updated_at' => $now,
        ]);
        $order = Db::one('SELECT * FROM {pay_orders} WHERE id = ?', [$id]);
        try {
            $r = $a->create($order, $plan, App::baseUrl() . '/pay/return.php?o=' . $code);
        } catch (ApiError $e) {
            Db::update('pay_orders', ['status' => 'failed', 'last_error' => mb_substr($e->getMessage(), 0, 300), 'updated_at' => Clock::sql()], 'id = ?', [$id]);
            PayRegistry::note($method, false, $e->getMessage());
            throw $e;
        }
        $host = strtolower((string)parse_url($r['url'], PHP_URL_HOST));
        if (stripos($r['url'], 'https://') !== 0 || !in_array($host, $a->payHosts(), true)) throw new ApiError('gateway_url', 'نشانی پرداخت نامعتبر است.', 502);
        Db::update('pay_orders', ['status' => 'pending', 'gateway_ref' => $r['ref'], 'updated_at' => Clock::sql()], 'id = ?', [$id]);
        PayRegistry::note($method, true);
        Audit::log('user', (int)$user['id'], 'pay_order_created', 'pay_order', $id, '', null, ['method' => $method, 'plan' => $plan['code'], 'amount' => $toman]);
        return ['order' => $code, 'url' => $r['url'], 'amount' => $toman, 'method' => $method];
    }

    /**
     * Asks the gateway about an order and, if paid, grants the plan exactly once. Safe to call from the
     * browser return, the app's status check and the cron, at the same time or repeatedly.
     */
    public static function settle(array $order): array
    {
        if (in_array($order['status'], ['paid', 'canceled'], true) || empty($order['gateway_ref'])) return $order;
        $a = PayRegistry::get((string)$order['method']);
        if ($a === null) return $order;
        $c = $a->check($order);
        $now = Clock::sql();
        if ($c->state === PayCheck::PAID) {
            Db::tx(function () use ($order, $c, $now) {
                // One atomic change from «not paid» to «paid»: a second caller changes nothing.
                $n = Db::run("UPDATE {pay_orders} SET status = 'paid', ref_id = ?, paid_at = ?, updated_at = ? WHERE id = ? AND status IN ('created','pending','failed','expired')",
                    [$c->refId, $now, $now, $order['id']])->rowCount();
                if ($n !== 1) return;
                $plan = Plans::byId((int)$order['plan_id']);
                $hash = Crypto::hash('direct|' . $order['method'] . '|' . $order['gateway_ref']);
                $pid = Db::insert('purchases', [
                    'user_id' => $order['user_id'], 'store' => 'direct', 'sku' => $order['method'] . ':' . $plan['code'], 'plan_id' => $plan['id'],
                    'product_type' => $plan['duration_days'] === null ? 'permanent' : 'consumable', 'order_id' => $c->refId ?: $order['code'],
                    'token_hash' => $hash, 'token_enc' => Crypto::encrypt((string)$order['gateway_ref']), 'payload' => $order['code'],
                    'amount_toman' => (int)$order['amount_toman'], 'purchase_time' => $now, 'status' => 'verified', 'verify_method' => 'gateway_api',
                    'verify_attempts' => 1, 'last_verified_at' => $now, 'next_check_at' => null, 'auto_renewing' => 0, 'consumed' => 1,
                    'note' => 'پرداخت وب: ' . $order['method'], 'created_at' => $now, 'updated_at' => $now,
                ]);
                Db::update('pay_orders', ['purchase_id' => $pid], 'id = ?', [$order['id']]);
                Entitlements::grantForPurchase((int)$order['user_id'], $plan, $pid);
                Audit::log('system', null, 'pay_order_paid', 'pay_order', $order['id'], '', null, ['purchase' => $pid, 'ref' => $c->refId]);
            });
            PayRegistry::note((string)$order['method'], true);
        } elseif ($c->state === PayCheck::FAILED) {
            Db::run("UPDATE {pay_orders} SET status = 'failed', last_error = ?, attempts = attempts + 1, updated_at = ? WHERE id = ? AND status IN ('created','pending')", [mb_substr($c->message, 0, 300), $now, $order['id']]);
        } else {
            Db::run('UPDATE {pay_orders} SET attempts = attempts + 1, last_error = ?, updated_at = ? WHERE id = ?', [mb_substr($c->message, 0, 300), $now, $order['id']]);
            if ($c->state === PayCheck::UNAVAILABLE) PayRegistry::note((string)$order['method'], false, $c->message);
        }
        return Db::one('SELECT * FROM {pay_orders} WHERE id = ?', [$order['id']]);
    }

    /** Cron: orders whose buyer never came back are asked about for a day, then expire. */
    public static function sweep(int $limit = 20): int
    {
        $n = 0;
        foreach (Db::all("SELECT * FROM {pay_orders} WHERE status = 'pending' AND created_at <= ? ORDER BY id LIMIT " . (int)$limit, [Clock::sql(Clock::now() - 120)]) as $o) {
            $o = self::settle($o);
            if ($o['status'] === 'pending' && Clock::ts($o['created_at']) < Clock::now() - 86400) Db::update('pay_orders', ['status' => 'expired', 'updated_at' => Clock::sql()], 'id = ?', [$o['id']]);
            $n++;
        }
        return $n;
    }

    public static function publicView(array $o): array
    {
        return ['order' => $o['code'], 'status' => $o['status'], 'method' => $o['method'], 'amount' => (int)$o['amount_toman'], 'ref_id' => $o['ref_id']];
    }
}
