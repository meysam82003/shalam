<?php
/**
 * Divan Emtiaz server — JSON API for the Android app (version 1).
 * Base: {site}/api/index.php?r=v1/... (also {site}/api/v1/... when URL rewriting works).
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

final class Api
{
    private static ?array $auth = null;

    /** Forgets the resolved login (each HTTP request is a new process; tests call handle() repeatedly). */
    public static function reset(): void { self::$auth = null; Sessions::$endedBecause = null; }

    public static function route(): string
    {
        $r = (string)($_GET['r'] ?? '');
        if ($r === '' && !empty($_SERVER['PATH_INFO'])) $r = (string)$_SERVER['PATH_INFO'];
        if ($r === '') {
            $uri = parse_url((string)($_SERVER['REQUEST_URI'] ?? ''), PHP_URL_PATH) ?: '';
            $pos = strpos($uri, '/api/');
            if ($pos !== false) $r = substr($uri, $pos + 5);
        }
        return trim(preg_replace('#^index\.php/?#', '', trim($r, '/')), '/');
    }

    public static function dispatch(): void
    {
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Headers: Authorization, X-Divan-Token, Content-Type');
        header('Access-Control-Allow-Methods: GET, POST, PUT, OPTIONS');
        if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') { http_response_code(204); return; }
        try {
            if (!App::installed()) throw new ApiError('not_installed', 'سرور هنوز نصب نشده است.', 503);
            Migrations::ensure();
            $data = self::handle(self::route(), $_SERVER['REQUEST_METHOD'] ?? 'GET');
            Http::json(['ok' => true, 'data' => $data, 'server_time' => Clock::now()]);
        } catch (ApiError $e) {
            Http::json(['ok' => false, 'error' => ['code' => $e->errorCode, 'message' => $e->getMessage()] + $e->extra, 'server_time' => Clock::now()], $e->status);
        } catch (Throwable $e) {
            error_log('[divan] ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
            Http::json(['ok' => false, 'error' => ['code' => 'server', 'message' => 'خطای سرور؛ لطفاً بعداً دوباره تلاش کنید.'], 'server_time' => Clock::now()], 500);
        }
    }

    private static function user(): array
    {
        if (self::$auth === null) self::$auth = Sessions::current();
        if (self::$auth === null) throw new ApiError('unauthorized', Sessions::$endedBecause === 'replaced' ? 'حساب شما روی دستگاه دیگری باز شد و از این دستگاه خارج شدید. برای ادامه دوباره وارد شوید.' : 'برای این کار وارد حساب شوید.', 401);
        return self::$auth[0];
    }

    private static function optionalUser(): ?array
    {
        if (self::$auth === null) self::$auth = Sessions::current();
        return self::$auth[0] ?? null;
    }

    private static function store(array $in = []): string
    {
        $s = (string)($in['store'] ?? $_GET['store'] ?? '');
        return in_array($s, Plans::STORES, true) || $s === 'direct' ? $s : 'bazaar';
    }

    /** Everything the app needs after login or on refresh. */
    private static function account(array $u): array
    {
        $store = self::$auth[1]['store'] ?? (in_array($_GET['store'] ?? '', Plans::STORES, true) ? $_GET['store'] : null);
        $cert = Entitlements::certificate($u, $store, self::$auth[1] ?? null);
        return [
            'user' => Users::publicView($u),
            'entitlement' => $cert['summary'],
            'certificate' => $cert['certificate'],
            'purchases' => array_map([Purchases::class, 'publicView'], Purchases::forUser((int)$u['id'])),
        ];
    }

    public static function handle(string $route, string $method): array
    {
        $in = $method === 'GET' ? $_GET : Http::body();
        // Keep pending store checks moving even without cron.
        if (random_int(1, 20) === 1) { try { Purchases::recheck(3); } catch (Throwable $e) { error_log('[divan] recheck ' . $e->getMessage()); } }

        switch ($route) {
            case 'v1/ping':
                return ['service' => 'divan-emtiaz', 'version' => 1];

            case 'v1/keys':
                return ['kid' => Crypto::keyId(), 'algorithm' => 'RSA-SHA256', 'public_key' => Crypto::publicKeyPem()];

            case 'v1/config': {
                $u = self::optionalUser();
                $store = self::store($in);
                $pro = $u !== null && Entitlements::summary((int)$u['id'], $store)['pro'];
                return [
                    'plans' => Plans::offers($store),
                    'features' => Features::forUser($u === null ? null : (int)$u['id'], $pro),
                    'ads' => Ads::configFor($store, $pro),
                    'cross_store' => (bool)Settings::get('cross_store', true),
                    // The store's public RSA key from the panel (public by design): the app checks each purchase's
                    // signature with it too, so a key changed in the panel reaches installed apps without a new APK.
                    'store_key' => in_array($store, Plans::STORES, true) ? trim((string)(Settings::get($store, [])['public_key'] ?? '')) : '',
                    'support_text' => Settings::get('support_text', ''),
                    'privacy_url' => App::baseUrl() . '/index.php?p=privacy',
                ];
            }

            case 'v1/pay/methods': {
                // Only what this app version may show now; never a list of switched-off methods.
                $store = self::store($in);
                $plan = isset($in['plan']) ? Plans::byCode((string)$in['plan']) : null;
                return ['methods' => array_map(fn($m) => array_diff_key($m, ['sort' => 1]), PayRegistry::methodsFor($store, $plan))];
            }

            case 'v1/pay/order': {
                self::post($method);
                $u = self::user();
                return PayOrders::create($u, (string)($in['method'] ?? ''), (string)($in['plan'] ?? ''), self::store($in), ($in['page'] ?? '') === 'divan');
            }

            case 'v1/pay/status': {
                $u = self::user();
                $o = PayOrders::byCode((string)($in['order'] ?? ''));
                if ($o === null || (int)$o['user_id'] !== (int)$u['id']) throw new ApiError('not_found', 'سفارش پیدا نشد.', 404);
                if (in_array($o['status'], ['pending', 'failed'], true) && RateLimit::hit('paystatus:' . $o['id'], 30, 3600)) $o = PayOrders::settle($o);
                return ['order' => PayOrders::publicView($o)] + ($o['status'] === 'paid' ? self::account(Users::byId((int)$u['id'])) : []);
            }

            case 'v1/notices': {
                $u = self::optionalUser();
                $store = in_array((string)($in['store'] ?? ''), array_keys(Notices::STORES), true) ? (string)$in['store'] : 'bazaar';
                if (!RateLimit::hit('notices:' . Http::ip(), 120, 3600)) throw new ApiError('rate_limited', 'درخواست زیاد است.', 429);
                $pro = $u !== null && Entitlements::summary((int)$u['id'], $store === 'direct' ? null : $store)['pro'];
                return ['notices' => Notices::due((string)($in['device'] ?? ''), $store, $pro, ($in['pro_optin'] ?? '') === '1')];
            }

            case 'v1/notices/event': {
                self::post($method);
                if (!RateLimit::hit('notice_event:' . Http::ip(), 240, 3600)) throw new ApiError('rate_limited', 'درخواست زیاد است.', 429);
                Notices::event((int)($in['id'] ?? 0), (string)($in['device'] ?? ''), (string)($in['event'] ?? ''));
                return ['ok' => true];
            }

            case 'v1/auth/register': {
                self::post($method);
                [$u, $key] = Users::register((string)($in['username'] ?? ''), (string)($in['password'] ?? ''), isset($in['email']) ? (string)$in['email'] : null);
                $token = self::open($u, $in);
                return ['token' => $token, 'recovery_key' => $key] + self::account($u);
            }

            case 'v1/auth/login': {
                self::post($method);
                $u = Users::login((string)($in['username'] ?? ''), (string)($in['password'] ?? ''));
                $token = self::open($u, $in);
                return ['token' => $token] + self::account($u);
            }

            case 'v1/auth/logout': {
                self::post($method);
                $u = self::user();
                Sessions::revoke((int)$u['id'], (int)self::$auth[1]['id']);
                return ['logged_out' => true];
            }

            case 'v1/auth/recover': {
                self::post($method);
                [$u, $key] = Users::recover((string)($in['username'] ?? ''), (string)($in['recovery_key'] ?? ''), (string)($in['new_password'] ?? ''));
                $token = self::open($u, $in);
                return ['token' => $token, 'recovery_key' => $key] + self::account($u);
            }

            case 'v1/auth/recovery-key': {
                self::post($method);
                $u = self::user();
                return ['recovery_key' => Users::newRecoveryKey($u, (string)($in['password'] ?? ''))];
            }

            case 'v1/auth/password': {
                self::post($method);
                $u = self::user();
                Users::changePassword($u, (string)($in['old_password'] ?? ''), (string)($in['new_password'] ?? ''), (int)self::$auth[1]['id']);
                return ['changed' => true];
            }

            case 'v1/me':
                return self::account(self::user()) + ['sessions' => Sessions::list((int)self::user()['id']), 'current_session' => (int)self::$auth[1]['id']];

            case 'v1/entitlement':
                return Entitlements::certificate(self::user(), self::$auth[1]['store'] ?? null, self::$auth[1]);

            case 'v1/devices/transfer': {
                self::post($method);
                $u = self::user();
                Devices::transfer($u, self::$auth[1], (string)($in['password'] ?? ''));
                return self::account(Users::byId((int)$u['id']));
            }

            case 'v1/sessions/revoke': {
                self::post($method);
                $u = self::user();
                if (!empty($in['all_others'])) Db::update('sessions', ['revoked_at' => Clock::sql()], 'user_id = ? AND id <> ? AND revoked_at IS NULL', [$u['id'], self::$auth[1]['id']]);
                else Sessions::revoke((int)$u['id'], (int)($in['id'] ?? 0));
                Audit::security('sessions_revoked', (int)$u['id'], $u['username']);
                return ['sessions' => Sessions::list((int)$u['id'])];
            }

            case 'v1/purchases/intent': {
                self::post($method);
                return Purchases::intent(self::user(), (string)($in['plan'] ?? ''), self::store($in), (string)($in['promo'] ?? ''));
            }

            case 'v1/purchases/verify': {
                self::post($method);
                $u = self::user();
                $r = Purchases::verify($u, $in);
                // The device a purchase is made on always holds Pro (taking the place of the one used longest ago if full).
                if (in_array($r['purchase']['status'], Purchases::OK_STATES, true) && !empty(self::$auth[1]['device_id']) && Devices::enabled()) {
                    Devices::bind((int)$u['id'], self::$auth[1]['device_id'], self::$auth[1]['device_name'], 'purchase');
                }
                return ['status' => $r['purchase']['status'], 'consume' => $r['consume'], 'purchase' => Purchases::publicView($r['purchase'])] + self::account($u);
            }

            case 'v1/purchases/restore': {
                self::post($method);
                $u = self::user();
                $results = [];
                foreach (array_slice((array)($in['purchases'] ?? []), 0, 20) as $p) {
                    if (!is_array($p)) continue;
                    try {
                        $r = Purchases::verify($u, $p + ['store' => self::store($in)], true);
                        $results[] = ['sku' => $p['sku'] ?? '', 'status' => $r['purchase']['status'], 'consume' => $r['consume']];
                    } catch (ApiError $e) {
                        $results[] = ['sku' => $p['sku'] ?? '', 'status' => 'error', 'code' => $e->errorCode, 'message' => $e->getMessage()];
                    }
                }
                return ['results' => $results] + self::account($u);
            }

            case 'v1/promo/check': {
                self::post($method);
                $u = self::user();
                $code = (string)($in['code'] ?? '');
                // Activation codes of site purchases go through the same «code» box of the app (any version, no update needed).
                if (PayOrders::looksLikeActivation($code)) return PayOrders::checkActivation($code, $u, self::store($in))['info'];
                return Promo::describe(Promo::find($code, $u), self::store($in));
            }

            case 'v1/promo/redeem': {
                self::post($method);
                $u = self::user();
                $code = (string)($in['code'] ?? '');
                if (PayOrders::looksLikeActivation($code)) return ['promo' => PayOrders::redeemActivation($code, $u, self::store($in))] + self::account(Users::byId((int)$u['id']));
                $p = Promo::redeemGift($code, $u);
                return ['promo' => Promo::describe($p, self::store($in))] + self::account(Users::byId((int)$u['id']));
            }

            case 'v1/ads/events': {
                self::post($method);
                if (!RateLimit::hit('adev:' . Http::ip(), 120, 3600)) throw new ApiError('rate_limited', 'too many', 429);
                return ['accepted' => Ads::record((array)($in['events'] ?? []))];
            }

            case 'v1/support': {
                self::post($method);
                $u = self::optionalUser();
                return ['id' => Support::create($u === null ? null : (int)$u['id'], $in + ['username' => $u['username'] ?? ($in['username'] ?? '')])];
            }

            case 'v1/userdata':
                $u = self::user();
                if ($method === 'GET') return ['backup' => UserData::get((int)$u['id'])];
                return ['size' => UserData::put((int)$u['id'], (string)($in['data'] ?? ''))];
        }
        throw new ApiError('not_found', 'مسیر یافت نشد.', 404);
    }

    /** New session for this device (the oldest devices beyond the panel's limit are signed out); it becomes the request's session. */
    private static function open(array $u, array $in): string
    {
        $token = Sessions::signIn((int)$u['id'], $in);
        // An Android build reports when its APK is not signed with the official key (a modified copy): logged for the owner.
        if (($in['integrity'] ?? true) === false) Audit::security('unofficial_app', (int)$u['id'], (string)$u['username'], mb_substr((string)($in['device'] ?? ''), 0, 80) . ' ' . mb_substr((string)($in['app_version'] ?? ''), 0, 20));
        self::$auth = [$u, Db::one('SELECT * FROM {sessions} WHERE token_hash = ?', [Crypto::hash($token)])];
        return $token;
    }

    private static function post(string $method): void
    {
        if ($method !== 'POST' && $method !== 'PUT') throw new ApiError('method', 'روش درخواست نادرست است.', 405);
    }
}
