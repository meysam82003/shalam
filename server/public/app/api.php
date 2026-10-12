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
    public static function reset(): void { self::$auth = null; }

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
        if (self::$auth === null) throw new ApiError('unauthorized', 'برای این کار وارد حساب شوید.', 401);
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
        return in_array($s, Plans::STORES, true) ? $s : 'bazaar';
    }

    /** Everything the app needs after login or on refresh. */
    private static function account(array $u): array
    {
        $store = self::$auth[1]['store'] ?? (in_array($_GET['store'] ?? '', Plans::STORES, true) ? $_GET['store'] : null);
        $cert = Entitlements::certificate($u, $store);
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
                    'support_text' => Settings::get('support_text', ''),
                    'privacy_url' => App::baseUrl() . '/index.php?p=privacy',
                ];
            }

            case 'v1/auth/register': {
                self::post($method);
                [$u, $key] = Users::register((string)($in['username'] ?? ''), (string)($in['password'] ?? ''), isset($in['email']) ? (string)$in['email'] : null);
                $token = Sessions::create((int)$u['id'], $in);
                return ['token' => $token, 'recovery_key' => $key] + self::account($u);
            }

            case 'v1/auth/login': {
                self::post($method);
                $u = Users::login((string)($in['username'] ?? ''), (string)($in['password'] ?? ''));
                $token = Sessions::create((int)$u['id'], $in);
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
                $token = Sessions::create((int)$u['id'], $in);
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
                return Entitlements::certificate(self::user(), self::$auth[1]['store'] ?? null);

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
                return Purchases::intent(self::user(), (string)($in['plan'] ?? ''), self::store($in));
            }

            case 'v1/purchases/verify': {
                self::post($method);
                $u = self::user();
                $r = Purchases::verify($u, $in);
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

    private static function post(string $method): void
    {
        if ($method !== 'POST' && $method !== 'PUT') throw new ApiError('method', 'روش درخواست نادرست است.', 405);
    }
}
