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
    /** Whether the gateway itself accepts this amount (toman); panel limits come on top. */
    public function accepts(int $toman): bool;
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
    public function accepts(int $toman): bool { return true; }
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
    public function accepts(int $toman): bool { return $toman >= 1000; }

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

/** A web gateway whose API address and payment-page domains can be changed from the panel. */
interface PayEndpointAdapter
{
    /** Documented API address, used while the panel field is empty. */
    public function defaultBase(): string;

    /** Documented domains of the payment page, used while the panel field is empty. */
    public function defaultHosts(): array;
}

/**
 * Panel overrides of a gateway's address: «api_base» (HTTPS, a domain name, no query) and «hosts» (the domains the
 * buyer's payment page may be on). Both are checked on save and again on use; a bad value falls back to the default.
 */
final class PayEndpoint
{
    public static function validBase(string $u): bool
    {
        if (Http::$allowLocalHttpForTests && preg_match('#^http://127\.0\.0\.1:\d+(/[A-Za-z0-9._~-]+)*$#', $u)) return true;
        return strlen($u) <= 200 && (bool)preg_match('#^https://([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}(/[A-Za-z0-9._~-]+)*$#i', $u) && strpos($u, '..') === false;
    }

    public static function validHost(string $h): bool
    {
        return strlen($h) <= 120 && (bool)preg_match('/^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}$/', $h);
    }

    /** «a.ir, b.ir» → ['a.ir', 'b.ir'], or null when any entry is not a domain name. */
    public static function parseHosts(string $s): ?array
    {
        $out = [];
        foreach (preg_split('/[\s,،]+/u', strtolower(trim($s)), -1, PREG_SPLIT_NO_EMPTY) as $h) {
            if (!self::validHost($h)) return null;
            $out[] = $h;
        }
        return array_values(array_unique($out));
    }

    public static function base(PayAdapter $a): string
    {
        $v = rtrim(trim((string)(PayRegistry::cfg($a->id())['api_base'] ?? '')), '/');
        return $v !== '' && self::validBase($v) ? $v : $a->defaultBase();
    }

    public static function hosts(PayAdapter $a): array
    {
        $v = self::parseHosts((string)(PayRegistry::cfg($a->id())['hosts'] ?? ''));
        return $v ? $v : $a->defaultHosts();
    }
}

/**
 * Blupal (blupal.top, docs: blupal.top/documentation): card-to-card invoices confirmed by Blupal. The buyer pays the
 * exact «final amount» shown on Blupal's page (amount + a few rials that identify the payment). The server creates
 * the invoice with the order's own amount and decides only by asking Blupal again (GET invoice): status PAID, the
 * same invoice, the same amount and the same mode (a test key never confirms a live order and the reverse).
 * The webhook is not signed, so it only triggers that same check.
 */
final class BlupalAdapter implements PayAdapter, PayEndpointAdapter
{
    public function id(): string { return 'blupal'; }
    public function title(): string { return 'کارت به کارت (بلوپال)'; }
    public function description(): string { return 'واریز کارت به کارت با شماره کارت و مبلغ دقیقی که صفحهٔ بلوپال نشان می‌دهد؛ تأیید خودکار.'; }
    public function kind(): string { return 'web'; }
    public function channels(): array { return ['direct']; }
    public function fields(): array { return ['api_key' => ['کلید API بلوپال (blu_live_… یا blu_test_…)', true]]; }
    public function defaultBase(): string { return 'https://blupal.top/api/v1'; }
    public function defaultHosts(): array { return ['blupal.top']; }
    public function payHosts(): array { return PayEndpoint::hosts($this); }
    public function supportsRefund(): bool { return false; }
    /** Blupal accepts 100,000 to 500,000,000 rials. */
    public function accepts(int $toman): bool { return $toman * 10 >= 100000 && $toman * 10 <= 500000000; }

    private function key(): string { return trim(Settings::secret('pay_blupal_api_key')); }
    private function testKey(): bool { return strpos($this->key(), 'blu_test_') === 0; }
    private function base(): string { return PayEndpoint::base($this); }

    /** The key's mode must match the panel's sandbox switch, so a test key can never sell real subscriptions. */
    public function configured(): bool
    {
        if (!preg_match('/^blu_(live|test)_[A-Za-z0-9_\-]{6,}$/', $this->key())) return false;
        return $this->testKey() === (bool)(PayRegistry::cfg('blupal')['sandbox'] ?? false);
    }

    private function mode(): string { return $this->testKey() ? 'sandbox' : 'live'; }

    private function call(string $method, string $path, ?array $body = null): array
    {
        $headers = ['X-API-Key: ' . $this->key(), 'Accept: application/json'];
        if ($body !== null) $headers[] = 'Content-Type: application/json';
        $r = Http::request($method, $this->base() . $path, $headers, $body === null ? null : json_encode($body), 20);
        $j = json_decode($r['body'], true);
        return [(int)$r['status'], is_array($j) ? $j : null, (string)$r['error']];
    }

    public function test(): array
    {
        if (!preg_match('/^blu_(live|test)_/', $this->key())) return ['warn', 'کلید API بلوپال ثبت نشده است.'];
        if (!$this->configured()) return ['warn', $this->testKey() ? 'کلید آزمایشی (blu_test) است؛ برای استفاده «حالت آزمایشی» را روشن کنید.' : 'کلید واقعی (blu_live) است؛ «حالت آزمایشی» را خاموش کنید.'];
        // An invoice that does not exist: «not found» proves the address and the key; «unauthorized» means a wrong key.
        [$st, $j, $err] = $this->call('GET', '/invoices/999999999');
        if ($st === 0) return ['bad', 'به بلوپال نرسید: ' . $err];
        $code = (string)($j['error'] ?? '');
        if ($st === 401 || $code === 'unauthorized') return ['bad', 'بلوپال کلید API را نپذیرفت.'];
        if ($st === 403 || $code === 'mode_mismatch') return ['ok', 'ارتباط با بلوپال برقرار است (' . ($this->testKey() ? 'آزمایشی' : 'واقعی') . ').'];
        if ($st === 404 || $code === 'not_found' || $code === 'invalid_invoice_id') return ['ok', 'ارتباط با بلوپال برقرار است (' . ($this->testKey() ? 'آزمایشی' : 'واقعی') . ').'];
        return ['bad', 'پاسخ نامشخص بلوپال (HTTP ' . $st . ($code !== '' ? '، ' . $code : '') . ').'];
    }

    public function create(array $order, array $plan, string $callback): array
    {
        $amount = (int)$order['amount_rial'];
        [$st, $j, $err] = $this->call('POST', '/invoices/create', ['amount' => $amount]);
        if ($st === 0 || $st >= 500) throw new ApiError('gateway_down', 'درگاه پرداخت در دسترس نیست؛ کمی بعد دوباره تلاش کنید.', 503);
        $id = $j['invoice_id'] ?? null;
        $link = (string)($j['payment_link'] ?? '');
        $ok = ($j['success'] ?? false) === true && is_int($id) && $id > 0 && (int)($j['amount'] ?? -1) === $amount
            && ($j['mode'] ?? '') === $this->mode() && strtoupper((string)($j['status'] ?? '')) === 'PENDING';
        if (!$ok) throw new ApiError('gateway_refused', 'درگاه پرداخت درخواست را نپذیرفت' . (isset($j['error']) ? ' (' . preg_replace('/[^a-z_]/', '', (string)$j['error']) . ')' : '') . '.', 502);
        return ['ref' => (string)$id, 'url' => $link];
    }

    public function check(array $order): PayCheck
    {
        $ref = (string)$order['gateway_ref'];
        if (!preg_match('/^[1-9][0-9]{0,18}$/', $ref)) return new PayCheck(PayCheck::FAILED, 'شناسهٔ فاکتور بلوپال نامعتبر است.');
        [$st, $j, $err] = $this->call('GET', '/invoices/' . $ref);
        if ($st === 0 || $st >= 500 || $st === 429 || $st === 401) return new PayCheck(PayCheck::UNAVAILABLE, 'بلوپال پاسخ نداد: ' . ($err ?: 'HTTP ' . $st));
        if (!is_array($j) || ($j['success'] ?? false) !== true) {
            $code = (string)($j['error'] ?? '');
            return new PayCheck(PayCheck::PENDING, 'پاسخ بلوپال: ' . ($code !== '' ? preg_replace('/[^a-z_]/', '', $code) : 'HTTP ' . $st));
        }
        if ((string)($j['invoice_id'] ?? '') !== $ref) return new PayCheck(PayCheck::PENDING, 'فاکتور دیگری برگشت.');
        if (($j['mode'] ?? '') !== $this->mode()) return new PayCheck(PayCheck::FAILED, 'حالت فاکتور با کلید فعلی یکی نیست.');
        $status = strtoupper((string)($j['status'] ?? ''));
        if ($status === 'PAID') {
            if ((int)($j['amount'] ?? -1) !== (int)$order['amount_rial']) return new PayCheck(PayCheck::FAILED, 'مبلغ فاکتور با سفارش یکی نیست.');
            $tx = isset($j['transaction_id']) ? (string)$j['transaction_id'] : null;
            return new PayCheck(PayCheck::PAID, '', $tx !== null && $tx !== '' ? 'BLU-' . preg_replace('/[^0-9A-Za-z_-]/', '', $tx) : 'BLU-INV-' . $ref);
        }
        if ($status === 'EXPIRED' || $status === 'CANCELED' || $status === 'CANCELLED') return new PayCheck(PayCheck::FAILED, 'فاکتور بلوپال ' . ($status === 'EXPIRED' ? 'منقضی' : 'لغو') . ' شد.');
        return new PayCheck(PayCheck::PENDING, 'در انتظار واریز.');
    }
}

/**
 * Aban Gateway (abangateway.ir, docs: github.com/AbanGateway/docs): card-to-card or Pol transfer of the exact
 * «payable» amount shown on Aban's page. The invoice is made with the order's own amount and order code; the order
 * is paid only when Aban, asked again, reports this invoice paid for the same amount, order and mode, and the
 * one-shot verify call succeeds (or reports it was verified before). Aban's servers are in Iran, so the calls go
 * through the Iran bridge by default. The webhook is signed (HMAC-SHA256 of the raw body) and only triggers a check.
 */
final class AbanAdapter implements PayAdapter, PayEndpointAdapter
{
    public function id(): string { return 'aban'; }
    public function title(): string { return 'کارت به کارت (آبان گیت‌وی)'; }
    public function description(): string { return 'واریز کارت به کارت یا انتقال پل با مبلغ دقیقی که صفحهٔ آبان نشان می‌دهد؛ تأیید خودکار.'; }
    public function kind(): string { return 'web'; }
    public function channels(): array { return ['direct']; }
    public function fields(): array
    {
        return ['token' => ['توکن API آبان (ag_live_… یا ag_test_…)', true], 'webhook_secret' => ['کلید مخفی وبهوک آبان (پنل آبان: تنظیمات ← وبهوک)', true]];
    }
    public function defaultBase(): string { return 'https://api.abangateway.ir/api/v1'; }
    public function defaultHosts(): array { return ['abangateway.ir']; }
    public function payHosts(): array { return PayEndpoint::hosts($this); }
    public function supportsRefund(): bool { return false; }
    public function accepts(int $toman): bool { return $toman >= 1000 && $toman <= 200000000; }

    private function token(): string { return trim(Settings::secret('pay_aban_token')); }
    private function testToken(): bool { return strpos($this->token(), 'ag_test_') === 0; }
    private function sandbox(): bool { return (bool)(PayRegistry::cfg('aban')['sandbox'] ?? false); }
    private function route(): string { return (PayRegistry::cfg('aban')['route'] ?? 'bridge') === 'direct' ? 'direct' : 'bridge'; }

    /** A test token only with the sandbox switch on, a live token only with it off; the bridge must be set up for its route. */
    public function configured(): bool
    {
        if (!preg_match('/^ag_(live|test)_[A-Za-z0-9]{16,128}$/', $this->token()) || $this->testToken() !== $this->sandbox()) return false;
        return $this->route() === 'direct' || PaymentBridge::ready();
    }

    /** One call along the chosen route: [HTTP status (0 = not reached), decoded JSON, error text]. */
    private function call(string $method, string $path, ?array $body = null): array
    {
        if ($this->route() === 'bridge') {
            $r = PaymentBridge::call('aban.call', ['base' => PayEndpoint::base($this), 'api_token' => $this->token(), 'method' => $method, 'path' => $path] + ($body !== null ? ['body' => $body] : []));
            if ($r['kind'] === 'store') return [(int)$r['status'], is_array($r['body'] ?? null) ? $r['body'] : null, ''];
            if ($r['kind'] === 'bad_request') return [0, null, 'واسط ایران درخواست آبان را نپذیرفت (' . (string)($r['error'] ?? '') . ')؛ اگر واسط قدیمی است، نسخهٔ تازهٔ واسط را نصب کنید.'];
            return [0, null, (string)($r['error'] ?? $r['kind'])];
        }
        $headers = ['Authorization: Bearer ' . $this->token(), 'Accept: application/json'];
        if ($body !== null) $headers[] = 'Content-Type: application/json';
        $r = Http::request($method, PayEndpoint::base($this) . $path, $headers, $body === null ? ($method === 'POST' ? '{}' : null) : json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 20);
        $j = json_decode($r['body'], true);
        return [(int)$r['status'], is_array($j) ? $j : null, (string)$r['error']];
    }

    private static function code(?array $j): string { return preg_replace('/[^a-z_]/', '', (string)($j['error']['code'] ?? '')); }

    public function test(): array
    {
        if (!preg_match('/^ag_(live|test)_/', $this->token())) return ['warn', 'توکن API آبان ثبت نشده است.'];
        if ($this->testToken() !== $this->sandbox()) return ['warn', $this->testToken() ? 'توکن آزمایشی (ag_test) است؛ برای استفاده «حالت آزمایشی» را روشن کنید.' : 'توکن واقعی (ag_live) است؛ «حالت آزمایشی» را خاموش کنید.'];
        if (!$this->configured()) return ['warn', $this->route() === 'bridge' ? 'واسط پرداخت ایران تنظیم نشده است (یا مسیر را «مستقیم» کنید).' : 'توکن آبان معتبر به نظر نمی‌رسد.'];
        // An invoice that does not exist: «not found» proves the address, the route and the token.
        [$st, $j, $err] = $this->call('GET', '/invoices/inv_divanprobe000000000');
        if ($st === 0) return ['bad', 'به آبان نرسید: ' . $err];
        if ($st === 401) return ['bad', 'آبان توکن API را نپذیرفت.'];
        if ($st === 404 || $st === 422 || self::code($j) === 'invoice_not_found') return ['ok', 'ارتباط با آبان برقرار است (' . ($this->testToken() ? 'آزمایشی' : 'واقعی') . '، ' . ($this->route() === 'bridge' ? 'از راه واسط ایران' : 'مستقیم') . ').'];
        return ['bad', 'پاسخ نامشخص آبان (HTTP ' . $st . (self::code($j) !== '' ? '، ' . self::code($j) : '') . ').'];
    }

    public function create(array $order, array $plan, string $callback): array
    {
        $amount = (int)$order['amount_rial'];
        [$st, $j, $err] = $this->call('POST', '/invoices', [
            'amount_rial' => $amount, 'order_id' => (string)$order['code'],
            'callback_url' => App::baseUrl() . '/pay/webhook.php?m=aban', 'return_url' => $callback,
            'description' => 'دیوان امتیاز — ' . $plan['title'], 'expiry_minutes' => 60,
        ]);
        if ($st === 0 || $st >= 500 || $st === 429) throw new ApiError('gateway_down', 'درگاه پرداخت در دسترس نیست؛ کمی بعد دوباره تلاش کنید.', 503);
        $id = (string)($j['invoice_id'] ?? '');
        $ok = ($st === 200 || $st === 201) && preg_match('/^[A-Za-z0-9_-]{4,80}$/', $id) && (int)($j['amount_rial'] ?? -1) === $amount
            && ($j['status'] ?? '') === 'pending' && (bool)($j['is_test'] ?? false) === $this->testToken()
            && (!isset($j['order_id']) || (string)$j['order_id'] === (string)$order['code']);
        if (!$ok) throw new ApiError('gateway_refused', 'درگاه پرداخت درخواست را نپذیرفت' . (self::code($j) !== '' ? ' (' . self::code($j) . ')' : ' (HTTP ' . $st . ')') . '.', 502);
        return ['ref' => $id, 'url' => (string)($j['payment_url'] ?? '')];
    }

    public function check(array $order): PayCheck
    {
        $ref = (string)$order['gateway_ref'];
        if (!preg_match('/^[A-Za-z0-9_-]{4,80}$/', $ref)) return new PayCheck(PayCheck::FAILED, 'شناسهٔ فاکتور آبان نامعتبر است.');
        [$st, $j, $err] = $this->call('GET', '/invoices/' . $ref);
        if ($st === 0 || $st >= 500 || $st === 429 || $st === 401 || $st === 403) return new PayCheck(PayCheck::UNAVAILABLE, 'آبان پاسخ نداد: ' . ($err ?: 'HTTP ' . $st));
        if ($st === 404) return new PayCheck(PayCheck::FAILED, 'فاکتور در آبان پیدا نشد.');
        if ($st !== 200 || !is_array($j) || (string)($j['invoice_id'] ?? '') !== $ref) return new PayCheck(PayCheck::PENDING, 'پاسخ نامشخص آبان (HTTP ' . $st . ').');
        if ((bool)($j['is_test'] ?? false) !== $this->testToken()) return new PayCheck(PayCheck::FAILED, 'حالت فاکتور (آزمایشی/واقعی) با توکن فعلی یکی نیست.');
        if (isset($j['order_id']) && (string)$j['order_id'] !== (string)$order['code']) return new PayCheck(PayCheck::FAILED, 'فاکتور آبان مال سفارش دیگری است.');
        if ((int)($j['amount_rial'] ?? -1) !== (int)$order['amount_rial']) return new PayCheck(PayCheck::FAILED, 'مبلغ فاکتور با سفارش یکی نیست.');
        $status = (string)($j['status'] ?? '');
        if ($status === 'expired' || $status === 'cancelled' || $status === 'canceled') return new PayCheck(PayCheck::FAILED, 'فاکتور آبان ' . ($status === 'expired' ? 'منقضی' : 'لغو') . ' شد.');
        if ($status !== 'paid') return new PayCheck(PayCheck::PENDING, 'در انتظار واریز.');
        // Paid: the one-shot verify (409 «already verified» means an earlier check of ours verified it).
        [$vs, $v, $verr] = $this->call('POST', '/invoices/' . $ref . '/verify');
        if ($vs === 0 || $vs >= 500 || $vs === 429) return new PayCheck(PayCheck::UNAVAILABLE, 'تأیید آبان پاسخ نداد: ' . ($verr ?: 'HTTP ' . $vs));
        if ($vs === 409 && self::code($v) === 'already_verified') return new PayCheck(PayCheck::PAID, '', 'ABAN-' . $ref);
        if ($vs === 200 && ($v['verified'] ?? false) === true) {
            if (isset($v['amount_rial']) && (int)$v['amount_rial'] !== (int)$order['amount_rial']) return new PayCheck(PayCheck::FAILED, 'مبلغ تأییدشده با سفارش یکی نیست.');
            if (isset($v['invoice_id']) && (string)$v['invoice_id'] !== $ref) return new PayCheck(PayCheck::PENDING, 'تأیید فاکتور دیگری برگشت.');
            return new PayCheck(PayCheck::PAID, '', 'ABAN-' . $ref);
        }
        if ($vs === 410 || self::code($v) === 'invoice_expired') return new PayCheck(PayCheck::FAILED, 'فاکتور آبان منقضی شد.');
        return new PayCheck(PayCheck::PENDING, 'تأیید آبان هنوز ممکن نیست (' . (self::code($v) ?: 'HTTP ' . $vs) . ').');
    }
}

final class PayRegistry
{
    /** Order of display when the panel sets none. */
    public static function adapters(): array
    {
        return ['bazaar' => new StorePayAdapter('bazaar'), 'myket' => new StorePayAdapter('myket'), 'zarinpal' => new ZarinpalAdapter(), 'blupal' => new BlupalAdapter(), 'aban' => new AbanAdapter()];
    }

    public static function get(string $id): ?PayAdapter { return self::adapters()[$id] ?? null; }

    /** Panel settings of one method: enabled, sort, min, max (toman), sandbox, route, kill, api_base and hosts (web gateways). */
    public static function cfg(string $id): array
    {
        $d = ['enabled' => in_array($id, ['bazaar', 'myket'], true), 'sort' => 0, 'min' => null, 'max' => null, 'sandbox' => false, 'route' => 'bridge', 'kill' => false, 'api_base' => '', 'hosts' => ''];
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
                if (!$a->accepts($price)) continue;
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

/** Webhooks of the web gateways: they only point at an invoice; settling always asks the gateway again. */
final class PayWebhooks
{
    /** @return array{0:int,1:array} HTTP status and JSON body. */
    public static function handle(string $method, string $raw, array $headers, string $ip): array
    {
        if (!RateLimit::hit('webhook:' . $ip, 300, 3600)) return [429, ['received' => false]];
        if ($method === 'aban') {
            // Signed by Aban with the shop's webhook secret over the raw body; without a secret set, it still only triggers a check.
            $secret = trim(Settings::secret('pay_aban_webhook_secret'));
            $sent = strtolower(trim((string)($headers['signature'] ?? '')));
            if ($secret !== '' && !hash_equals(hash_hmac('sha256', $raw, $secret), $sent)) return [401, ['received' => false, 'error' => 'bad_signature']];
        }
        $j = json_decode($raw, true);
        if (!is_array($j)) return [400, ['received' => false, 'error' => 'invalid_payload']];
        switch ($method) {
            case 'blupal':
                $ref = $j['invoice_id'] ?? null;
                if (!is_int($ref) && !(is_string($ref) && ctype_digit($ref))) return [400, ['received' => false, 'error' => 'invalid_payload']];
                return self::settleByRef('blupal', (string)$ref);
            case 'aban':
                $ref = $j['invoice_id'] ?? null;
                if (!is_string($ref) || !preg_match('/^[A-Za-z0-9_-]{4,80}$/', $ref)) return [400, ['received' => false, 'error' => 'invalid_payload']];
                return self::settleByRef('aban', $ref);
            default:
                return [404, ['received' => false]];
        }
    }

    private static function settleByRef(string $method, string $ref): array
    {
        $o = Db::one('SELECT * FROM {pay_orders} WHERE method = ? AND gateway_ref = ?', [$method, $ref]);
        if ($o === null) return [200, ['received' => true]];
        if (in_array($o['status'], ['pending', 'failed', 'expired', 'created'], true) && RateLimit::hit('webhook_order:' . $o['id'], 20, 3600)) {
            try { PayOrders::settle($o); } catch (Throwable $e) { /* the app and the cron ask again */ }
        }
        return [200, ['received' => true]];
    }
}
