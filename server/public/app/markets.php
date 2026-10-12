<?php
/**
 * Divan Emtiaz server — store verification (Cafe Bazaar and Myket) and the purchase ledger.
 *
 * Every purchase is decided on the server: the app sends the store's receipt, the server checks the
 * store's RSA signature, asks the store's developer API, and records the purchase against the account.
 * One receipt can belong to one account only; a receipt is processed once.
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

/** Result of asking a store about a receipt. */
final class StoreVerdict
{
    public const VALID = 'valid';
    public const INVALID = 'invalid';
    public const REFUNDED = 'refunded';
    public const UNAVAILABLE = 'unavailable';

    public string $state;
    public string $message;
    public ?int $purchaseTime;
    public ?int $expiresAt;
    public bool $autoRenewing;
    public ?bool $consumed;
    public array $raw;

    public function __construct(string $state, string $message = '', ?int $purchaseTime = null, ?int $expiresAt = null, bool $autoRenewing = false, ?bool $consumed = null, array $raw = [])
    {
        $this->state = $state; $this->message = $message; $this->purchaseTime = $purchaseTime; $this->expiresAt = $expiresAt;
        $this->autoRenewing = $autoRenewing; $this->consumed = $consumed; $this->raw = $raw;
    }
}

interface StoreVerifier
{
    public function configured(): bool;
    public function verify(string $sku, string $token, string $productType): StoreVerdict;
}

/** Tests replace the stores with fakes. */
final class Stores
{
    /** @var array<string, StoreVerifier> */
    public static array $override = [];

    public static function get(string $store): StoreVerifier
    {
        if (isset(self::$override[$store])) return self::$override[$store];
        if ($store === 'bazaar') return new BazaarVerifier();
        if ($store === 'myket') return new MyketVerifier();
        throw new ApiError('bad_store', 'فروشگاه نامعتبر است.');
    }

    /** Checks the store's RSA signature of the purchase data (the key is copied from the store panel). */
    public static function signatureValid(string $store, string $originalJson, string $signature): ?bool
    {
        $key = trim((string)(Settings::get($store, [])['public_key'] ?? ''));
        if ($key === '' || $originalJson === '' || $signature === '') return null;
        $pem = "-----BEGIN PUBLIC KEY-----\n" . chunk_split(preg_replace('/\s+/', '', $key), 64, "\n") . "-----END PUBLIC KEY-----\n";
        $pub = openssl_pkey_get_public($pem);
        if ($pub === false) return null;
        $sig = base64_decode($signature, true);
        if ($sig === false) return false;
        return openssl_verify($originalJson, $sig, $pub, OPENSSL_ALGO_SHA1) === 1;
    }
}

// ---------------------------------------------------------------- Myket
/**
 * Myket developer API (server to server, header X-Access-Token from the developer panel):
 *   POST https://developer.myket.ir/api/partners/applications/{package}/purchases/products/{sku}/verify  {"tokenId": "..."}
 *   → {"kind","purchaseTime","developerPayload","purchaseState" (0 = paid),"consumptionState"}
 * Myket has no subscriptions; timed plans are consumable products that the server turns into periods.
 */
final class MyketVerifier implements StoreVerifier
{
    private const BASE = 'https://developer.myket.ir/api/partners/applications/';

    private function token(): string { return Settings::secret('myket_access_token'); }
    private function package(): string { return (string)(Settings::get('myket', [])['package'] ?? 'com.meysam.divanemtiaz'); }

    public function configured(): bool { return $this->token() !== ''; }

    public function verify(string $sku, string $token, string $productType): StoreVerdict
    {
        if (!$this->configured()) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'توکن صحت‌سنجی مایکت در پنل ثبت نشده است.');
        $url = self::BASE . rawurlencode($this->package()) . '/purchases/products/' . rawurlencode($sku) . '/verify';
        $r = Http::request('POST', $url, ['X-Access-Token: ' . $this->token(), 'Content-Type: application/json', 'Accept: application/json'], json_encode(['tokenId' => $token]));
        if ($r['status'] === 0 || $r['status'] >= 500 || $r['status'] === 429) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'پاسخ مایکت دریافت نشد: ' . ($r['error'] ?: 'HTTP ' . $r['status']));
        $j = json_decode($r['body'], true);
        if ($r['status'] === 401 || $r['status'] === 403) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'توکن مایکت نامعتبر است.', null, null, false, null, is_array($j) ? $j : []);
        if ($r['status'] === 404) return new StoreVerdict(StoreVerdict::INVALID, 'مایکت این خرید را نمی‌شناسد.', null, null, false, null, is_array($j) ? $j : []);
        if ($r['status'] !== 200 || !is_array($j)) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'پاسخ نامعتبر از مایکت (HTTP ' . $r['status'] . ')');
        $state = (int)($j['purchaseState'] ?? -1);
        $time = isset($j['purchaseTime']) ? intdiv((int)$j['purchaseTime'], 1000) : null;
        $consumed = isset($j['consumptionState']) ? ((int)$j['consumptionState'] === 1) : null;
        if ($state === 0) return new StoreVerdict(StoreVerdict::VALID, '', $time, null, false, $consumed, $j);
        return new StoreVerdict(StoreVerdict::INVALID, 'مایکت خرید را پرداخت‌شده نمی‌داند (purchaseState=' . $state . ').', $time, null, false, $consumed, $j);
    }

    /** Lists the products in Myket (price in rials) to compare with the panel's prices. */
    public function products(): array
    {
        if (!$this->configured()) throw new ApiError('not_configured', 'توکن مایکت ثبت نشده است.');
        $r = Http::request('GET', self::BASE . rawurlencode($this->package()) . '/products?limit=200&offset=0', ['X-Access-Token: ' . $this->token(), 'Accept: application/json']);
        $j = json_decode($r['body'], true);
        if ($r['status'] !== 200 || !is_array($j)) throw new ApiError('store_error', 'دریافت محصولات مایکت ممکن نشد (HTTP ' . $r['status'] . ' ' . $r['error'] . ').');
        return $j['skus'] ?? [];
    }

    /** Changes the price of a consumable product in Myket (documented for consumables only). */
    public function setPrice(string $sku, int $toman): void
    {
        if (!$this->configured()) throw new ApiError('not_configured', 'توکن مایکت ثبت نشده است.');
        $r = Http::request('PUT', self::BASE . rawurlencode($this->package()) . '/products/' . rawurlencode($sku), ['X-Access-Token: ' . $this->token(), 'Content-Type: application/json'], json_encode(['price' => $toman * 10]));
        if ($r['status'] !== 200) throw new ApiError('store_error', 'تغییر قیمت در مایکت انجام نشد (HTTP ' . $r['status'] . ' ' . $r['error'] . ' ' . mb_substr($r['body'], 0, 200) . ').');
    }
}

// ---------------------------------------------------------------- Cafe Bazaar
/**
 * Cafe Bazaar developer API (Pardakht), server to server. Configuration in the panel:
 * the API secret of the developer panel (header CAFEBAZAAR-PISHKHAN-API-SECRET) or the older
 * OAuth client (client id, client secret, refresh token → access token).
 */
final class BazaarVerifier implements StoreVerifier
{
    /**
     * «تخفیف پویا» (dynamic discount): a one-time HS256 token, signed with the key from the Bazaar panel,
     * that lowers the price of one purchase below the panel price (Bazaar 13.3.0 or newer).
     */
    public static function dynamicPriceToken(string $sku, int $toman): ?string
    {
        $key = Settings::secret('bazaar_discount_key');
        if ($key === '') return null;
        $header = Crypto::b64u(json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
        $claims = Crypto::b64u(json_encode([
            'price' => $toman * 10,
            'package_name' => (string)(Settings::get('bazaar', [])['package'] ?? 'com.meysam.divanemtiaz'),
            'sku' => $sku,
            'exp' => Clock::now() + 900,
            'nonce' => bin2hex(random_bytes(8)),
        ]));
        return $header . '.' . $claims . '.' . Crypto::b64u(hash_hmac('sha256', $header . '.' . $claims, $key, true));
    }

    private const API = 'https://pardakht.cafebazaar.ir/devapi/v2/';

    private function cfg(): array { return Settings::get('bazaar', []); }
    private function package(): string { return (string)($this->cfg()['package'] ?? 'com.meysam.divanemtiaz'); }

    public function configured(): bool
    {
        return Settings::secret('bazaar_api_secret') !== '' || Settings::secret('bazaar_refresh_token') !== '';
    }

    /** OAuth access token for the older API, refreshed and cached. */
    private function accessToken(): string
    {
        $cached = Settings::get('bazaar_access_cache', []);
        if (!empty($cached['token']) && (int)($cached['until'] ?? 0) > Clock::now() + 60) return (string)Crypto::decrypt($cached['token']);
        $r = Http::request('POST', self::API . 'auth/token/', ['Content-Type: application/x-www-form-urlencoded'], http_build_query([
            'grant_type' => 'refresh_token',
            'client_id' => (string)($this->cfg()['client_id'] ?? ''),
            'client_secret' => Settings::secret('bazaar_client_secret'),
            'refresh_token' => Settings::secret('bazaar_refresh_token'),
        ]));
        $j = json_decode($r['body'], true);
        if ($r['status'] !== 200 || empty($j['access_token'])) return '';
        Settings::set('bazaar_access_cache', ['token' => Crypto::encrypt((string)$j['access_token']), 'until' => Clock::now() + (int)($j['expires_in'] ?? 3600)]);
        return (string)$j['access_token'];
    }

    public function verify(string $sku, string $token, string $productType): StoreVerdict
    {
        if (!$this->configured()) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'کلید API بازار در پنل ثبت نشده است.');
        $secret = Settings::secret('bazaar_api_secret');
        if ($productType === 'subscription') {
            $path = 'api/applications/' . rawurlencode($this->package()) . '/subscriptions/' . rawurlencode($sku) . '/purchases/' . rawurlencode($token);
        } else {
            $path = 'api/validate/' . rawurlencode($this->package()) . '/inapp/' . rawurlencode($sku) . '/purchases/' . rawurlencode($token) . '/';
        }
        $headers = ['Accept: application/json'];
        $url = self::API . $path;
        if ($secret !== '') $headers[] = 'CAFEBAZAAR-PISHKHAN-API-SECRET: ' . $secret;
        else {
            $access = $this->accessToken();
            if ($access === '') return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'دریافت توکن دسترسی بازار ناموفق بود.');
            $url .= '?access_token=' . rawurlencode($access);
        }
        $r = Http::request('GET', $url, $headers);
        if ($r['status'] === 0 || $r['status'] >= 500 || $r['status'] === 429) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'پاسخ بازار دریافت نشد: ' . ($r['error'] ?: 'HTTP ' . $r['status']));
        $j = json_decode($r['body'], true);
        if ($r['status'] === 401 || $r['status'] === 403) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'کلید بازار نامعتبر یا منقضی است.', null, null, false, null, is_array($j) ? $j : []);
        if ($r['status'] === 404) return new StoreVerdict(StoreVerdict::INVALID, 'بازار این خرید را نمی‌شناسد.', null, null, false, null, is_array($j) ? $j : []);
        if ($r['status'] !== 200 || !is_array($j)) return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'پاسخ نامعتبر از بازار (HTTP ' . $r['status'] . ')');
        if ($productType === 'subscription') {
            $until = isset($j['validUntilTimestampMsec']) ? intdiv((int)$j['validUntilTimestampMsec'], 1000) : null;
            $start = isset($j['initiationTimestampMsec']) ? intdiv((int)$j['initiationTimestampMsec'], 1000) : null;
            if ($until === null) return new StoreVerdict(StoreVerdict::INVALID, 'پاسخ اشتراک بازار تاریخ اعتبار ندارد.', $start, null, false, null, $j);
            return new StoreVerdict(StoreVerdict::VALID, '', $start, $until, (bool)($j['autoRenewing'] ?? false), null, $j);
        }
        $state = (int)($j['purchaseState'] ?? -1);
        $time = isset($j['purchaseTime']) ? intdiv((int)$j['purchaseTime'], 1000) : null;
        $consumed = isset($j['consumptionState']) ? ((int)$j['consumptionState'] === 0) : null;
        if ($state === 0) return new StoreVerdict(StoreVerdict::VALID, '', $time, null, false, $consumed, $j);
        if ($state === 1) return new StoreVerdict(StoreVerdict::REFUNDED, 'بازار خرید را بازپرداخت‌شده اعلام کرده است.', $time, null, false, $consumed, $j);
        return new StoreVerdict(StoreVerdict::INVALID, 'بازار خرید را معتبر نمی‌داند (purchaseState=' . $state . ').', $time, null, false, $consumed, $j);
    }
}

// ---------------------------------------------------------------- purchase ledger
final class Purchases
{
    public const OK_STATES = ['verified', 'signature_verified'];

    public static function forUser(int $userId): array { return Db::all('SELECT * FROM {purchases} WHERE user_id = ? ORDER BY id DESC', [$userId]); }

    /** A purchase intent binds the next store purchase to this account (sent as the developer payload). */
    public static function intent(array $user, string $planCode, string $store, string $promoCode = ''): array
    {
        if (!in_array($store, Plans::STORES, true)) throw new ApiError('bad_store', 'فروشگاه نامعتبر است.');
        $plan = Plans::byCode($planCode);
        if ($plan === null || !$plan['active']) throw new ApiError('bad_plan', 'این پلن در دسترس نیست.', 404);
        $offer = Plans::offer($plan, $store);
        if (!$offer['purchasable']) {
            $msg = $offer['state'] === 'price_sync_pending' ? 'قیمت این پلن در حال به‌روزرسانی در فروشگاه است؛ کمی بعد دوباره تلاش کنید.' : 'این پلن فعلاً قابل خرید نیست.';
            throw new ApiError('not_purchasable', $msg, 409, ['state' => $offer['state']]);
        }
        $sum = Entitlements::summary((int)$user['id']);
        if ($sum['lifetime']) throw new ApiError('already_lifetime', 'شما اشتراک مادام‌العمر دارید؛ خرید دوباره لازم نیست.', 409);
        if ($plan['duration_days'] === null && (int)Db::value("SELECT COUNT(*) FROM {purchases} p JOIN {plans} pl ON pl.id = p.plan_id WHERE p.user_id = ? AND pl.duration_days IS NULL AND p.status IN ('verified','signature_verified','pending')", [$user['id']]) > 0) {
            throw new ApiError('already_owned', 'این خرید برای حساب شما ثبت شده یا در حال تأیید است.', 409);
        }
        if ($plan['purchase_limit'] !== null && (int)Db::value("SELECT COUNT(*) FROM {purchases} WHERE user_id = ? AND plan_id = ? AND status IN ('verified','signature_verified')", [$user['id'], $plan['id']]) >= (int)$plan['purchase_limit']) {
            throw new ApiError('limit', 'سقف خرید این پلن برای حساب شما پر شده است.', 409);
        }
        $price = $offer['final_price'];
        $dynamic = $offer['dynamic_price'];
        $promoId = null;
        if (trim($promoCode) !== '') {
            $p = Promo::find($promoCode, $user);
            if ($p['kind'] !== 'discount') throw new ApiError('promo_is_gift', 'این کد هدیه است؛ از «کد تخفیف یا هدیه» فعالش کنید.', 409);
            if ($p['plan_code'] !== null && $p['plan_code'] !== $plan['code']) throw new ApiError('promo_other_plan', 'این کد برای پلن دیگری است.', 409);
            if (!Promo::discountUsable($store) || $offer['product_type'] === 'subscription') throw new ApiError('promo_store', 'کد تخفیف فقط در نسخهٔ کافه‌بازار قابل استفاده است.', 409);
            $price = Promo::discountedPrice($price, (int)$p['percent']);
            $dynamic = true;
            $promoId = (int)$p['id'];
        }
        $payload = 'dv' . bin2hex(random_bytes(14));
        Db::insert('purchase_intents', ['user_id' => $user['id'], 'plan_id' => $plan['id'], 'store' => $store, 'payload' => $payload, 'price_toman' => $price, 'promo_id' => $promoId, 'created_at' => Clock::sql()]);
        return [
            'payload' => $payload, 'sku' => $offer['sku'], 'product_type' => $offer['product_type'], 'price' => $price,
            'dynamic_price_token' => $dynamic ? BazaarVerifier::dynamicPriceToken($offer['sku'], $price) : null,
        ];
    }

    /**
     * Records and checks one receipt. Returns the stored purchase row and whether the app should consume
     * the product in the store (only for timed plans, and only once the period is safely recorded).
     */
    public static function verify(array $user, array $in, bool $restore = false): array
    {
        $store = (string)($in['store'] ?? '');
        $sku = trim((string)($in['sku'] ?? ''));
        $token = trim((string)($in['token'] ?? ''));
        if (!in_array($store, Plans::STORES, true) || $sku === '' || $token === '' || strlen($token) > 600) throw new ApiError('bad_receipt', 'اطلاعات رسید ناقص است.');
        if (!RateLimit::hit('verify:' . $user['id'], 40, 3600)) throw new ApiError('rate_limited', 'درخواست‌های تأیید زیاد است؛ کمی بعد دوباره تلاش کنید.', 429);
        $plan = Plans::bySku($store, $sku);
        if ($plan === null) throw new ApiError('unknown_sku', 'این محصول شناخته نشد.', 404);
        $type = (string)$plan["type_$store"];
        $hash = Crypto::hash($store . '|' . $token);
        $originalJson = (string)($in['original_json'] ?? '');
        $signature = (string)($in['signature'] ?? '');
        $payload = (string)($in['payload'] ?? '');

        $existing = Db::one('SELECT * FROM {purchases} WHERE store = ? AND token_hash = ?', [$store, $hash]);
        if ($existing !== null && (int)$existing['user_id'] !== (int)$user['id']) {
            Audit::security('receipt_reuse', (int)$user['id'], $user['username'], "store=$store sku=$sku purchase=" . $existing['id']);
            throw new ApiError('owned_by_other', 'این خرید به حساب دیگری متصل است. اگر خرید متعلق به شماست، از بخش پشتیبانی درخواست بررسی ثبت کنید.', 409);
        }
        if ($existing !== null && in_array($existing['status'], self::OK_STATES, true)) {
            return ['purchase' => $existing, 'consume' => self::shouldConsume($existing, $plan)];
        }
        if ($existing !== null && in_array($existing['status'], ['refunded', 'revoked'], true)) {
            throw new ApiError('refunded', 'این خرید بازپرداخت یا باطل شده است.', 409);
        }

        // The receipt must describe the product, token and app it claims.
        $sigOk = Stores::signatureValid($store, $originalJson, $signature);
        if ($originalJson !== '') {
            $oj = json_decode($originalJson, true);
            if (!is_array($oj)) throw new ApiError('bad_receipt', 'رسید خرید خوانا نیست.');
            $ojSku = (string)($oj['productId'] ?? $oj['sku'] ?? '');
            $ojToken = (string)($oj['purchaseToken'] ?? $oj['token'] ?? '');
            $ojPkg = (string)($oj['packageName'] ?? '');
            $pkg = (string)(Settings::get($store, [])['package'] ?? 'com.meysam.divanemtiaz');
            if (($ojSku !== '' && $ojSku !== $sku) || ($ojToken !== '' && $ojToken !== $token) || ($ojPkg !== '' && $ojPkg !== $pkg)) {
                Audit::security('receipt_mismatch', (int)$user['id'], $user['username'], "store=$store sku=$sku");
                throw new ApiError('bad_receipt', 'رسید با این محصول یا برنامه مطابقت ندارد.');
            }
            if ($payload === '') $payload = (string)($oj['developerPayload'] ?? $oj['payload'] ?? '');
        }
        if ($sigOk === false) {
            Audit::security('receipt_bad_signature', (int)$user['id'], $user['username'], "store=$store sku=$sku");
            throw new ApiError('bad_signature', 'امضای رسید خرید معتبر نیست.', 400);
        }

        // An intent made by another account cannot be used to claim a receipt.
        $intent = $payload !== '' ? Db::one('SELECT * FROM {purchase_intents} WHERE payload = ?', [$payload]) : null;
        if ($intent !== null && (int)$intent['user_id'] !== (int)$user['id']) {
            Audit::security('payload_mismatch', (int)$user['id'], $user['username'], "store=$store sku=$sku");
            throw new ApiError('payload_mismatch', 'این خرید با حساب دیگری آغاز شده است.', 409);
        }
        if ($intent === null && !$restore) {
            // Purchases started outside this flow (older versions, restore) are accepted, but noted.
            Audit::security('receipt_without_intent', (int)$user['id'], $user['username'], "store=$store sku=$sku");
        }

        $verdict = Stores::get($store)->verify($sku, $token, $type);
        $now = Clock::sql();
        $status = 'pending';
        $method = null;
        if ($verdict->state === StoreVerdict::VALID) { $status = 'verified'; $method = 'store_api'; }
        elseif ($verdict->state === StoreVerdict::REFUNDED) { $status = 'refunded'; $method = 'store_api'; }
        elseif ($verdict->state === StoreVerdict::INVALID) { $status = 'rejected'; $method = 'store_api'; }
        elseif ($sigOk === true) { $status = 'signature_verified'; $method = 'store_signature'; }

        $row = [
            'user_id' => $user['id'], 'store' => $store, 'sku' => $sku, 'plan_id' => $plan['id'], 'product_type' => $type,
            'order_id' => mb_substr((string)($in['order_id'] ?? ''), 0, 128) ?: null, 'token_hash' => $hash, 'token_enc' => Crypto::encrypt($token),
            'payload' => $payload ?: null, 'amount_toman' => $intent !== null ? (int)$intent['price_toman'] : Plans::offer($plan, $store)['final_price'],
            'purchase_time' => $verdict->purchaseTime !== null ? Clock::sql($verdict->purchaseTime) : (isset($in['purchase_time']) ? Clock::sql(intdiv((int)$in['purchase_time'], 1000)) : null),
            'status' => $status, 'verify_method' => $method, 'verify_attempts' => 1,
            'last_verified_at' => $verdict->state === StoreVerdict::UNAVAILABLE ? null : $now,
            'next_check_at' => $status === 'pending' || $status === 'signature_verified' ? Clock::sql(Clock::now() + 300) : Clock::sql(Clock::now() + 7 * 86400),
            'auto_renewing' => $verdict->autoRenewing ? 1 : 0,
            'store_expires_at' => $verdict->expiresAt !== null ? Clock::sql($verdict->expiresAt) : null,
            'consumed' => $verdict->consumed ? 1 : 0,
            'raw_enc' => Crypto::encrypt(json_encode(['original' => $originalJson, 'signature' => $signature, 'store' => $verdict->raw, 'message' => $verdict->message], JSON_UNESCAPED_UNICODE)),
            'note' => $verdict->message ?: null, 'created_at' => $now, 'updated_at' => $now,
        ];

        $saved = Db::tx(function () use ($existing, $row, $intent, $plan, $user, $status, $verdict) {
            if ($existing !== null) {
                unset($row['created_at']);
                $row['verify_attempts'] = (int)$existing['verify_attempts'] + 1;
                Db::update('purchases', $row, 'id = ?', [$existing['id']]);
                $id = (int)$existing['id'];
            } else {
                try {
                    $id = Db::insert('purchases', $row);
                } catch (PDOException $e) {
                    // Another request stored the same receipt a moment ago.
                    $other = Db::one('SELECT * FROM {purchases} WHERE store = ? AND token_hash = ?', [$row['store'], $row['token_hash']]);
                    if ($other === null || (int)$other['user_id'] !== (int)$user['id']) throw new ApiError('owned_by_other', 'این خرید به حساب دیگری متصل است.', 409);
                    return $other;
                }
            }
            if ($intent !== null && $intent['used_at'] === null) Db::update('purchase_intents', ['used_at' => Clock::sql()], 'id = ?', [$intent['id']]);
            if ($intent !== null && !empty($intent['promo_id']) && in_array($status, self::OK_STATES, true)) Promo::used((int)$intent['promo_id'], (int)$user['id'], $id);
            if (in_array($status, self::OK_STATES, true)) {
                if ($verdict->expiresAt !== null) self::grantStoreSubscription((int)$user['id'], $plan, $id, $verdict);
                else Entitlements::grantForPurchase((int)$user['id'], $plan, $id);
            }
            return Db::one('SELECT * FROM {purchases} WHERE id = ?', [$id]);
        });

        Audit::log('user', (int)$user['id'], 'purchase_' . $saved['status'], 'purchase', $saved['id'], $verdict->message, null, ['store' => $store, 'sku' => $sku, 'status' => $saved['status']]);
        if ($saved['status'] === 'rejected') throw new ApiError('rejected', 'فروشگاه این خرید را تأیید نکرد. ' . $verdict->message, 402);
        if ($saved['status'] === 'refunded') throw new ApiError('refunded', 'این خرید بازپرداخت شده است.', 409);
        return ['purchase' => $saved, 'consume' => self::shouldConsume($saved, $plan)];
    }

    /** Store subscriptions (Bazaar) carry their own end date. */
    private static function grantStoreSubscription(int $userId, array $plan, int $purchaseId, StoreVerdict $v): void
    {
        $existing = Db::one('SELECT id FROM {entitlements} WHERE purchase_id = ?', [$purchaseId]);
        $row = ['starts_at' => Clock::sql($v->purchaseTime ?? Clock::now()), 'expires_at' => Clock::sql((int)$v->expiresAt), 'status' => 'active', 'updated_at' => Clock::sql()];
        if ($existing !== null) { Db::update('entitlements', $row, 'id = ?', [$existing['id']]); return; }
        Db::insert('entitlements', $row + ['user_id' => $userId, 'source' => 'purchase', 'purchase_id' => $purchaseId, 'plan_code' => $plan['code'], 'created_at' => Clock::sql()]);
    }

    /** Timed passes are consumed (so they can be bought again) only after the server stored the period. */
    private static function shouldConsume(array $purchase, array $plan): bool
    {
        return $purchase['product_type'] === 'consumable' && in_array($purchase['status'], self::OK_STATES, true);
    }

    /**
     * Rechecks purchases that wait for the store, those confirmed only by signature, and (weekly)
     * confirmed ones, to catch refunds. Safe to call often; each run handles a few purchases.
     */
    public static function recheck(int $limit = 10): array
    {
        $done = [];
        foreach (Db::all("SELECT * FROM {purchases} WHERE next_check_at IS NOT NULL AND next_check_at <= ? AND status IN ('pending','signature_verified','verified') ORDER BY next_check_at LIMIT " . (int)$limit, [Clock::sql()]) as $p) {
            $token = Crypto::decrypt($p['token_enc']);
            if ($token === null) continue;
            $v = Stores::get($p['store'])->verify($p['sku'], $token, $p['product_type']);
            $attempts = (int)$p['verify_attempts'] + 1;
            $upd = ['verify_attempts' => $attempts, 'updated_at' => Clock::sql()];
            $plan = Plans::byId((int)$p['plan_id']);
            if ($v->state === StoreVerdict::VALID) {
                $upd += ['status' => 'verified', 'verify_method' => 'store_api', 'last_verified_at' => Clock::sql(), 'next_check_at' => Clock::sql(Clock::now() + 7 * 86400), 'note' => null];
                if ($v->expiresAt !== null) $upd['store_expires_at'] = Clock::sql($v->expiresAt);
                Db::update('purchases', $upd, 'id = ?', [$p['id']]);
                if ($plan !== null) {
                    if ($v->expiresAt !== null) self::grantStoreSubscription((int)$p['user_id'], $plan, (int)$p['id'], $v);
                    else Entitlements::grantForPurchase((int)$p['user_id'], $plan, (int)$p['id']);
                }
            } elseif ($v->state === StoreVerdict::REFUNDED || ($v->state === StoreVerdict::INVALID && $p['status'] !== 'pending')) {
                $new = $v->state === StoreVerdict::REFUNDED ? 'refunded' : 'revoked';
                Db::update('purchases', $upd + ['status' => $new, 'last_verified_at' => Clock::sql(), 'next_check_at' => null, 'note' => $v->message], 'id = ?', [$p['id']]);
                Entitlements::revokeForPurchase((int)$p['id'], $new, $v->message);
                Audit::log('system', null, 'purchase_' . $new, 'purchase', $p['id'], $v->message);
            } elseif ($v->state === StoreVerdict::INVALID) {
                Db::update('purchases', $upd + ['status' => 'rejected', 'last_verified_at' => Clock::sql(), 'next_check_at' => null, 'note' => $v->message], 'id = ?', [$p['id']]);
            } else {
                // Store not reachable: try again later with a growing delay; nothing granted is taken back.
                $delay = min(6 * 3600, 300 * (2 ** min(6, $attempts)));
                Db::update('purchases', $upd + ['next_check_at' => Clock::sql(Clock::now() + $delay), 'note' => $v->message], 'id = ?', [$p['id']]);
            }
            $done[] = ['id' => (int)$p['id'], 'state' => $v->state];
        }
        return $done;
    }

    public static function publicView(array $p): array
    {
        $plan = $p['plan_id'] ? Plans::byId((int)$p['plan_id']) : null;
        return [
            'id' => (int)$p['id'], 'store' => $p['store'], 'sku' => $p['sku'], 'plan' => $plan['code'] ?? null, 'plan_title' => $plan['title'] ?? $p['sku'],
            'order_id' => $p['order_id'], 'amount' => $p['amount_toman'] === null ? null : (int)$p['amount_toman'],
            'status' => $p['status'], 'purchase_time' => Clock::ts($p['purchase_time']) ?? Clock::ts($p['created_at']),
            'created_at' => Clock::ts($p['created_at']),
        ];
    }
}
