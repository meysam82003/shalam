<?php
/**
 * Divan Emtiaz server — domain services: accounts and sessions, plans and campaigns,
 * entitlements and signed offline certificates, feature flags, ads, media, support and backups.
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

// ================================================================ accounts
final class Users
{
    public const SESSION_DAYS = 90;

    public static function normalize(string $username): string { return strtolower(trim($username)); }

    public static function validateUsername(string $u): void
    {
        if (!preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,31}$/', $u)) {
            throw new ApiError('bad_username', 'نام کاربری باید ۳ تا ۳۲ حرف انگلیسی، رقم، نقطه یا زیرخط باشد و با حرف شروع شود.');
        }
    }

    public static function validatePassword(string $p, string $username = ''): void
    {
        if (mb_strlen($p) < 8) throw new ApiError('weak_password', 'رمز عبور باید دست‌کم ۸ نویسه باشد.');
        if (!preg_match('/[A-Za-z\x{0600}-\x{06FF}]/u', $p) || !preg_match('/[0-9۰-۹]/u', $p)) {
            throw new ApiError('weak_password', 'رمز عبور باید هم حرف و هم عدد داشته باشد.');
        }
        if ($username !== '' && stripos($p, $username) !== false) throw new ApiError('weak_password', 'رمز عبور نباید شامل نام کاربری باشد.');
        if (mb_strlen($p) > 128) throw new ApiError('weak_password', 'رمز عبور بیش از اندازه بلند است.');
    }

    public static function byId(int $id): ?array { return Db::one('SELECT * FROM {users} WHERE id = ?', [$id]); }
    public static function byName(string $u): ?array { return Db::one('SELECT * FROM {users} WHERE username_norm = ?', [self::normalize($u)]); }

    /** Creates an account; returns [user, recoveryKey]. The recovery key is shown once and stored only as a hash. */
    public static function register(string $username, string $password, ?string $email = null): array
    {
        $username = trim($username);
        self::validateUsername($username);
        self::validatePassword($password, $username);
        if (!RateLimit::hit('register:' . Http::ip(), 10, 3600)) throw new ApiError('rate_limited', 'تعداد ثبت‌نام از این شبکه زیاد است؛ کمی بعد دوباره تلاش کنید.', 429);
        if (self::byName($username) !== null) throw new ApiError('username_taken', 'این نام کاربری قبلاً گرفته شده است.', 409);
        if ($email !== null && $email !== '' && !filter_var($email, FILTER_VALIDATE_EMAIL)) throw new ApiError('bad_email', 'ایمیل معتبر نیست.');
        $key = Crypto::recoveryKey();
        $now = Clock::sql();
        $id = Db::insert('users', [
            'username' => $username, 'username_norm' => self::normalize($username),
            'password_hash' => Crypto::passwordHash($password),
            'recovery_hash' => Crypto::passwordHash(Crypto::normalizeRecovery($key)), 'recovery_created_at' => $now,
            'email' => $email ?: null, 'status' => 'active', 'failed_logins' => 0,
            'created_at' => $now, 'updated_at' => $now,
        ]);
        Audit::security('register', $id, $username);
        return [self::byId($id), $key];
    }

    public static function login(string $username, string $password): array
    {
        $ip = Http::ip();
        if (!RateLimit::hit('login-ip:' . $ip, 30, 600)) throw new ApiError('rate_limited', 'تلاش‌های ورود زیاد است؛ چند دقیقه بعد دوباره تلاش کنید.', 429);
        if (!RateLimit::hit('login-user:' . self::normalize($username), 12, 900)) {
            Audit::security('login_throttled', null, $username);
            throw new ApiError('rate_limited', 'تلاش‌های ورود به این حساب زیاد است؛ ۱۵ دقیقه بعد دوباره تلاش کنید.', 429);
        }
        $u = self::byName($username);
        if ($u === null) {
            password_verify($password, '$2y$10$usesomesillystringforsaltuQ6qkvb5Zz9u0b0pJ6g8n2a7Y2Q3m'); // equal timing
            throw new ApiError('bad_credentials', 'نام کاربری یا رمز عبور درست نیست.', 401);
        }
        if ($u['locked_until'] !== null && Clock::ts($u['locked_until']) > Clock::now()) throw new ApiError('locked', 'این حساب به‌دلیل تلاش‌های ناموفق موقتاً قفل شده است.', 423);
        if ($u['status'] !== 'active') throw new ApiError('disabled', 'این حساب غیرفعال است؛ با پشتیبانی تماس بگیرید.', 403);
        if (!password_verify($password, $u['password_hash'])) {
            $failed = (int)$u['failed_logins'] + 1;
            Db::update('users', ['failed_logins' => $failed, 'locked_until' => $failed >= 10 ? Clock::sql(Clock::now() + 900) : null], 'id = ?', [$u['id']]);
            Audit::security('login_failed', (int)$u['id'], $u['username']);
            throw new ApiError('bad_credentials', 'نام کاربری یا رمز عبور درست نیست.', 401);
        }
        if (password_needs_rehash($u['password_hash'], defined('PASSWORD_ARGON2ID') ? PASSWORD_ARGON2ID : PASSWORD_DEFAULT)) {
            Db::update('users', ['password_hash' => Crypto::passwordHash($password)], 'id = ?', [$u['id']]);
        }
        Db::update('users', ['failed_logins' => 0, 'locked_until' => null, 'last_login_at' => Clock::sql()], 'id = ?', [$u['id']]);
        return self::byId((int)$u['id']);
    }

    /** Resets the password with the recovery key; the key is replaced and every session is closed. */
    public static function recover(string $username, string $recoveryKey, string $newPassword): array
    {
        if (!RateLimit::hit('recover-ip:' . Http::ip(), 10, 3600) || !RateLimit::hit('recover-user:' . self::normalize($username), 5, 3600)) {
            Audit::security('recover_throttled', null, $username);
            throw new ApiError('rate_limited', 'تلاش‌های بازیابی زیاد است؛ یک ساعت بعد دوباره تلاش کنید.', 429);
        }
        $u = self::byName($username);
        if ($u === null || $u['recovery_hash'] === null || !password_verify(Crypto::normalizeRecovery($recoveryKey), $u['recovery_hash'])) {
            Audit::security('recover_failed', $u === null ? null : (int)$u['id'], $username);
            throw new ApiError('bad_recovery', 'نام کاربری یا کلید بازیابی درست نیست.', 401);
        }
        self::validatePassword($newPassword, $u['username']);
        $key = Crypto::recoveryKey();
        Db::update('users', [
            'password_hash' => Crypto::passwordHash($newPassword),
            'recovery_hash' => Crypto::passwordHash(Crypto::normalizeRecovery($key)), 'recovery_created_at' => Clock::sql(),
            'failed_logins' => 0, 'locked_until' => null, 'updated_at' => Clock::sql(),
        ], 'id = ?', [$u['id']]);
        Db::update('sessions', ['revoked_at' => Clock::sql()], 'user_id = ? AND revoked_at IS NULL', [$u['id']]);
        Audit::security('recovered', (int)$u['id'], $u['username']);
        return [self::byId((int)$u['id']), $key];
    }

    public static function newRecoveryKey(array $u, string $password): string
    {
        if (!password_verify($password, $u['password_hash'])) throw new ApiError('bad_credentials', 'رمز عبور درست نیست.', 401);
        $key = Crypto::recoveryKey();
        Db::update('users', ['recovery_hash' => Crypto::passwordHash(Crypto::normalizeRecovery($key)), 'recovery_created_at' => Clock::sql(), 'updated_at' => Clock::sql()], 'id = ?', [$u['id']]);
        Audit::security('recovery_key_rotated', (int)$u['id'], $u['username']);
        return $key;
    }

    public static function changePassword(array $u, string $old, string $new, int $keepSession): void
    {
        if (!password_verify($old, $u['password_hash'])) throw new ApiError('bad_credentials', 'رمز عبور فعلی درست نیست.', 401);
        self::validatePassword($new, $u['username']);
        Db::update('users', ['password_hash' => Crypto::passwordHash($new), 'updated_at' => Clock::sql()], 'id = ?', [$u['id']]);
        Db::update('sessions', ['revoked_at' => Clock::sql()], 'user_id = ? AND id <> ? AND revoked_at IS NULL', [$u['id'], $keepSession]);
        Audit::security('password_changed', (int)$u['id'], $u['username']);
    }

    public static function publicView(array $u): array
    {
        return [
            'id' => (int)$u['id'], 'username' => $u['username'], 'email' => $u['email'],
            'created_at' => Clock::ts($u['created_at']), 'recovery_created_at' => Clock::ts($u['recovery_created_at']),
        ];
    }
}

final class Sessions
{
    public static function create(int $userId, array $device): string
    {
        $token = Crypto::token(32);
        $now = Clock::now();
        Db::insert('sessions', [
            'user_id' => $userId, 'token_hash' => Crypto::hash($token),
            'device_name' => mb_substr((string)($device['device'] ?? ''), 0, 80) ?: null,
            'platform' => mb_substr((string)($device['platform'] ?? 'android'), 0, 20),
            'app_version' => mb_substr((string)($device['app_version'] ?? ''), 0, 20) ?: null,
            'store' => in_array($device['store'] ?? '', ['bazaar', 'myket', 'direct', 'web'], true) ? $device['store'] : null,
            'device_id' => Devices::clean($device['device_id'] ?? null),
            'ip' => Http::ip(), 'created_at' => Clock::sql($now), 'last_seen_at' => Clock::sql($now),
            'expires_at' => Clock::sql($now + Users::SESSION_DAYS * 86400),
        ]);
        return $token;
    }

    /**
     * Signing in on a device: the account stays open on at most the panel's number of devices («pro_devices_limit»,
     * default 1). Beyond it, the devices used longest ago are signed out (their app signs out at its next sync) and the
     * new device holds Pro. Each version keeps its own payment method for buying and renewing.
     */
    public static function signIn(int $userId, array $device): string
    {
        $token = self::create($userId, $device);
        if (!Devices::enabled()) return $token;
        $s = Db::one('SELECT * FROM {sessions} WHERE token_hash = ?', [Crypto::hash($token)]);
        $limit = Devices::limit();
        if ($limit > 0) {
            // One entry per device (a session without a device id counts on its own), newest first; this one first of all.
            $seen = [];
            $keep = [$s['device_id'] ?? ('s' . $s['id']) => true];
            foreach (Db::all('SELECT * FROM {sessions} WHERE user_id = ? AND id <> ? AND revoked_at IS NULL AND expires_at > ? ORDER BY last_seen_at DESC, id DESC', [$userId, $s['id'], Clock::sql()]) as $o) {
                $key = $o['device_id'] ?? ('s' . $o['id']);
                if (isset($keep[$key]) || (!isset($seen[$key]) && count($keep) < $limit)) { $keep[$key] = true; continue; }
                $seen[$key] = true;
                Db::update('sessions', ['revoked_at' => Clock::sql(), 'revoke_reason' => 'replaced'], 'id = ?', [$o['id']]);
                if ($o['device_id'] !== null) Db::run('DELETE FROM {pro_devices} WHERE user_id = ? AND device = ?', [$userId, $o['device_id']]);
                Audit::log('system', null, 'signed_out_elsewhere', 'user', $userId, 'replaced', null, ['device' => $o['device_name'], 'by' => $s['device_name']]);
            }
        }
        if ($s['device_id'] !== null) Devices::bind($userId, $s['device_id'], $s['device_name'], 'sign_in');
        return $token;
    }

    /** Why the request's token no longer works (for the message the app shows), or null. */
    public static ?string $endedBecause = null;

    /** Resolves the bearer token of the request; returns [user, session] or null. */
    public static function current(): ?array
    {
        $h = Http::header('Authorization');
        $token = str_starts_with($h, 'Bearer ') ? substr($h, 7) : Http::header('X-Divan-Token');
        $token = trim($token);
        if ($token === '' || strlen($token) > 100) return null;
        $s = Db::one('SELECT * FROM {sessions} WHERE token_hash = ?', [Crypto::hash($token)]);
        if ($s !== null && $s['revoked_at'] !== null) self::$endedBecause = $s['revoke_reason'] ?? null;
        if ($s === null || $s['revoked_at'] !== null || Clock::ts($s['expires_at']) < Clock::now()) return null;
        $u = Users::byId((int)$s['user_id']);
        if ($u === null || $u['status'] !== 'active') return null;
        if (Clock::ts($s['last_seen_at']) < Clock::now() - 300) {
            Db::update('sessions', ['last_seen_at' => Clock::sql(), 'expires_at' => Clock::sql(Clock::now() + Users::SESSION_DAYS * 86400), 'ip' => Http::ip()], 'id = ?', [$s['id']]);
        }
        return [$u, $s];
    }

    public static function list(int $userId): array
    {
        return array_map(fn($s) => [
            'id' => (int)$s['id'], 'device' => $s['device_name'], 'platform' => $s['platform'], 'store' => $s['store'],
            'app_version' => $s['app_version'], 'created_at' => Clock::ts($s['created_at']), 'last_seen_at' => Clock::ts($s['last_seen_at']),
        ], Db::all('SELECT * FROM {sessions} WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ? ORDER BY last_seen_at DESC', [$userId, Clock::sql()]));
    }

    public static function revoke(int $userId, int $sessionId): void
    {
        Db::update('sessions', ['revoked_at' => Clock::sql()], 'id = ? AND user_id = ?', [$sessionId, $userId]);
    }
}

// ================================================================ plans, prices and campaigns
final class Plans
{
    public const STORES = ['bazaar', 'myket'];

    public static function all(): array { return Db::all('SELECT * FROM {plans} ORDER BY sort, id'); }
    public static function byCode(string $code): ?array { return Db::one('SELECT * FROM {plans} WHERE code = ?', [$code]); }
    public static function byId(int $id): ?array { return Db::one('SELECT * FROM {plans} WHERE id = ?', [$id]); }

    public static function bySku(string $store, string $sku): ?array
    {
        if (!in_array($store, self::STORES, true)) return null;
        return Db::one("SELECT * FROM {plans} WHERE sku_$store = ?", [$sku]);
    }

    /** Running campaign of the plan for the store (store-specific wins over "all"). */
    public static function campaign(array $plan, string $store): ?array
    {
        $now = Clock::sql();
        return Db::one(
            "SELECT * FROM {campaigns} WHERE plan_id = ? AND active = 1 AND (store = ? OR store = 'all')
             AND (starts_at IS NULL OR starts_at <= ?) AND (ends_at IS NULL OR ends_at > ?)
             ORDER BY (store = ?) DESC, id DESC LIMIT 1",
            [$plan['id'], $store, $now, $now, $store]
        );
    }

    /** The price the store charges for this plan (set in the panel to match the Bazaar or Myket developer panel). */
    public static function storePrice(array $plan, string $store): int
    {
        $p = $store === 'direct' ? 0 : (int)($plan["store_price_$store"] ?? 0);
        return $p > 0 ? $p : (int)$plan['base_price_toman'];
    }

    public static function onStore(array $plan, string $store): bool { return $store === 'direct' || (int)($plan["on_$store"] ?? 1) === 1; }

    /**
     * What the app shows and the store charges. Each store has its own price; an optional list price is shown
     * struck through. A timed campaign lowers one Bazaar purchase with a signed dynamic-price token.
     */
    public static function offer(array $plan, string $store): array
    {
        $price = self::storePrice($plan, $store);
        $list = (int)($plan["list_price_$store"] ?? 0);
        $direct = $store === 'direct';
        // The site version has no store product: it sells through the web methods of the payment registry.
        $sku = $direct ? 'web' : (string)($plan["sku_$store"] ?? '');
        $type = $direct ? ($plan['duration_days'] === null ? 'permanent' : 'consumable') : (string)($plan["type_$store"] ?? 'consumable');
        $storeOn = $direct ? PayRegistry::methodsFor('direct') !== [] : (bool)(Settings::get($store, [])['enabled'] ?? true);
        $camp = self::campaign($plan, $store);
        $final = $price;
        $dynamic = false;
        if ($camp !== null && self::campaignApplies($camp, $plan, $store)) { $final = (int)$camp['price_toman']; $dynamic = true; }
        else $camp = null;
        $base = $dynamic ? $price : max($price, $list);
        $state = !$plan['active'] ? 'inactive' : (!self::onStore($plan, $store) ? 'off_in_store' : (!$storeOn ? 'store_disabled' : ($sku === '' ? 'no_sku' : 'ok')));
        $discount = ($final < $base && $base > 0) ? (int)round(($base - $final) * 100 / $base) : 0;
        return [
            'code' => $plan['code'],
            'title' => $plan['title'],
            'description' => $plan['description'],
            'duration_days' => $plan['duration_days'] === null ? null : (int)$plan['duration_days'],
            'lifetime' => $plan['duration_days'] === null,
            'base_price' => $base,
            'final_price' => $final,
            'store_price' => $price,
            'discount_percent' => $discount,
            'badge_text' => $camp !== null && $camp['label'] ? $camp['label'] : $plan['badge_text'],
            'badge_color' => $camp !== null && $camp['label_color'] ? $camp['label_color'] : $plan['badge_color'],
            'campaign_ends_at' => $camp !== null ? Clock::ts($camp['ends_at']) : null,
            'renewal_note' => $type === 'subscription' ? 'تمدید خودکار ماهانه از طریق ' . ($store === 'myket' ? 'مایکت' : 'کافه‌بازار') . '؛ لغو در هر زمان از همان فروشگاه.' : $plan['renewal_note'],
            'sku' => $sku,
            'product_type' => $type,
            'purchasable' => $state === 'ok',
            'state' => $state,
            'dynamic_price' => $dynamic,
            'sort' => (int)$plan['sort'],
        ];
    }

    /** A campaign changes what is charged only where the store can honour it: Bazaar's signed dynamic price. */
    public static function campaignApplies(array $camp, array $plan, string $store): bool
    {
        return $store === 'bazaar' && (int)$camp['price_toman'] < self::storePrice($plan, 'bazaar')
            && ($plan['type_bazaar'] ?? '') !== 'subscription' && Settings::secret('bazaar_discount_key') !== '';
    }

    public static function offers(string $store): array
    {
        $out = [];
        foreach (self::all() as $p) if ($p['active'] && self::onStore($p, $store)) $out[] = self::offer($p, $store);
        return $out;
    }
}

// ================================================================ entitlements and the offline certificate
final class Entitlements
{
    /** Active Pro entitlements at server time. */
    public static function active(int $userId): array
    {
        $now = Clock::sql();
        return Db::all("SELECT * FROM {entitlements} WHERE user_id = ? AND status = 'active' AND starts_at <= ? AND (expires_at IS NULL OR expires_at > ?) ORDER BY starts_at", [$userId, $now, $now]);
    }

    public static function all(int $userId): array { return Db::all('SELECT * FROM {entitlements} WHERE user_id = ? ORDER BY id DESC', [$userId]); }

    /** Summary: pro, lifetime, current plan and the end of the whole chain of stacked periods. */
    public static function summary(int $userId, ?string $store = null): array
    {
        // With «cross_store» off, a purchase counts only in the store it was made in (gifts count everywhere).
        $filter = '';
        $params = [$userId, Clock::sql()];
        if ($store !== null && !Settings::get('cross_store', true)) {
            $filter = ' AND (e.purchase_id IS NULL OR p.store = ?)';
            $params[] = $store;
        }
        $rows = Db::all("SELECT e.* FROM {entitlements} e LEFT JOIN {purchases} p ON p.id = e.purchase_id WHERE e.user_id = ? AND e.status = 'active' AND (e.expires_at IS NULL OR e.expires_at > ?)$filter ORDER BY e.starts_at", $params);
        $now = Clock::now();
        $lifetime = false; $end = null; $plan = null; $pro = false; $started = null;
        foreach ($rows as $r) {
            if ($r['expires_at'] === null) { $lifetime = true; $pro = true; $plan = $r['plan_code']; continue; }
            $s = Clock::ts($r['starts_at']); $x = Clock::ts($r['expires_at']);
            if ($s <= $now && $x > $now) { $pro = true; $plan = $plan ?? $r['plan_code']; $started = $started ?? $s; }
            if ($end === null || $x > $end) $end = $x;
        }
        $grant = Db::one("SELECT * FROM {user_grants} WHERE user_id = ? AND feature_k = 'pro' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?) ORDER BY expires_at IS NULL DESC, expires_at DESC LIMIT 1", [$userId, Clock::sql()]);
        if ($grant !== null) {
            $pro = true;
            if ($grant['expires_at'] === null) $lifetime = true;
            else { $gx = Clock::ts($grant['expires_at']); if ($end === null || $gx > $end) $end = $gx; }
            $plan = $plan ?? 'gift';
        }
        return [
            'pro' => $pro,
            'lifetime' => $lifetime,
            'plan' => $plan,
            'expires_at' => $lifetime ? null : $end,
            'days_left' => $lifetime || $end === null ? null : max(0, (int)ceil(($end - $now) / 86400)),
        ];
    }

    /**
     * Adds a timed period after the end of the user's current periods, so buying again never
     * loses the remaining days; a lifetime purchase has no end.
     */
    public static function grantForPurchase(int $userId, array $plan, int $purchaseId, string $source = 'purchase'): int
    {
        $existing = Db::one('SELECT id FROM {entitlements} WHERE purchase_id = ?', [$purchaseId]);
        if ($existing !== null) return (int)$existing['id'];
        $now = Clock::now();
        if ($plan['duration_days'] === null) {
            $start = $now; $end = null;
        } else {
            $last = Db::value("SELECT MAX(expires_at) FROM {entitlements} WHERE user_id = ? AND status = 'active' AND expires_at IS NOT NULL", [$userId]);
            $start = max($now, (int)(Clock::ts($last) ?? 0));
            $end = $start + (int)$plan['duration_days'] * 86400;
        }
        return Db::insert('entitlements', [
            'user_id' => $userId, 'source' => $source, 'purchase_id' => $purchaseId, 'plan_code' => $plan['code'],
            'starts_at' => Clock::sql($start), 'expires_at' => $end === null ? null : Clock::sql($end),
            'status' => 'active', 'created_at' => Clock::sql(), 'updated_at' => Clock::sql(),
        ]);
    }

    /** Ends the entitlement of a refunded or revoked purchase; later periods move up to fill the gap. */
    public static function revokeForPurchase(int $purchaseId, string $status, string $reason): void
    {
        $e = Db::one('SELECT * FROM {entitlements} WHERE purchase_id = ?', [$purchaseId]);
        if ($e === null || $e['status'] !== 'active') return;
        Db::update('entitlements', ['status' => $status, 'reason' => $reason, 'updated_at' => Clock::sql()], 'id = ?', [$e['id']]);
        if ($e['expires_at'] === null) return;
        $removed = Clock::ts($e['expires_at']) - max(Clock::ts($e['starts_at']), Clock::now());
        if ($removed <= 0) return;
        foreach (Db::all("SELECT * FROM {entitlements} WHERE user_id = ? AND status = 'active' AND expires_at IS NOT NULL AND starts_at >= ?", [$e['user_id'], $e['expires_at']]) as $later) {
            Db::update('entitlements', [
                'starts_at' => Clock::sql(Clock::ts($later['starts_at']) - $removed),
                'expires_at' => Clock::sql(Clock::ts($later['expires_at']) - $removed), 'updated_at' => Clock::sql(),
            ], 'id = ?', [$later['id']]);
        }
    }

    /**
     * Signed certificate the app checks offline. The private key never leaves the server; the app holds
     * only the public key. Timed access is limited to the paid end (+ a short grace when a store
     * subscription renews) and to a configurable offline window; lifetime access lasts a long window
     * and is refreshed on every sync, so a refund still reaches the device later.
     */
    /** «Offline until» of a lifetime certificate: never in practice (1 Jan 2100); older apps read it as a date. */
    public const LIFETIME_OFFLINE = 4102444800;

    public static function certificate(array $user, ?string $store = null, ?array $session = null): array
    {
        $sum = self::summary((int)$user['id'], $store);
        // One phone per subscription: Pro is signed only for the phone the subscription is bound to.
        $device = $session['device_id'] ?? null;
        $conflict = null;
        if ($sum['pro'] && Devices::enabled()) {
            $bound = Devices::boundTo($user, $device, $session['device_name'] ?? null);
            if (!$bound['here']) {
                $conflict = ['name' => $bound['name'], 'since' => $bound['since']];
                $sum['pro_elsewhere'] = true;
            }
        }
        $sum['this_device'] = $conflict === null;
        $sum['device_conflict'] = $conflict;
        $now = Clock::now();
        $offlineDays = (int)Settings::get('offline_days', 21);
        $graceDays = (int)Settings::get('renew_grace_days', 3);
        // Grace only when the period that ends last belongs to a store subscription that renews itself;
        // a pass that was bought once ends exactly at its end date.
        $renewing = false;
        if ($sum['expires_at'] !== null) {
            $last = Db::one("SELECT p.auto_renewing FROM {entitlements} e JOIN {purchases} p ON p.id = e.purchase_id WHERE e.user_id = ? AND e.status = 'active' AND e.expires_at IS NOT NULL ORDER BY e.expires_at DESC LIMIT 1", [$user['id']]);
            $renewing = $last !== null && (int)$last['auto_renewing'] === 1;
        }
        $grace = $sum['expires_at'] === null ? null : $sum['expires_at'] + ($renewing ? $graceDays * 86400 : 0);
        if ($sum['lifetime'] && $sum['pro']) {
            // Lifetime ownership has no end and needs no periodic check-in; the certificate stays bound to its phone.
            $offlineUntil = self::LIFETIME_OFFLINE;
        } else {
            $offlineUntil = $sum['pro'] ? min((int)$grace, $now + $offlineDays * 86400) : $now + $offlineDays * 86400;
            // A subscription that can move between phones is checked online more often.
            if (Devices::enabled() && $sum['pro']) $offlineUntil = min($offlineUntil, $now + min($offlineDays, (int)Settings::get('device_offline_days', 7)) * 86400);
        }
        $payload = [
            'v' => 1,
            'kid' => Crypto::keyId(),
            'uid' => (int)$user['id'],
            'user' => $user['username'],
            'pro' => $sum['pro'] && $conflict === null,
            'lifetime' => $sum['lifetime'],
            'plan' => $sum['plan'],
            'exp' => $sum['expires_at'],
            'grace' => $grace,
            'iat' => $now,
            'offline_until' => $offlineUntil,
            'features' => Features::forUser((int)$user['id'], $sum['pro'] && $conflict === null),
            'store' => $store,
            'policy' => 1,
            'device' => $device,
            'device_conflict' => $conflict,
        ];
        $body = Crypto::b64u(json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
        return ['certificate' => $body . '.' . Crypto::b64u(Crypto::sign($body)), 'summary' => $sum, 'server_time' => $now];
    }
}

// ================================================================ the devices of a subscription
/**
 * One account works on every version (Bazaar, Myket, the site's APK, web, Windows, iPhone); each version keeps its own
 * payment method for buying and renewing, but Pro follows the account. The account is open on at most the panel's number
 * of devices at once («pro_devices_limit», default 1, 0 = no limit): signing in on a new device signs the oldest one out
 * (Sessions::signIn). A device that has a session but no Pro yet may still take it over with the password («انتقال»).
 */
final class Devices
{
    public static function enabled(): bool { return (bool)Settings::get('single_device', true); }

    public static function limit(): int { return max(0, (int)Settings::get('pro_devices_limit', 1)); }

    /** The app sends a SHA-256 of its stable device identifier; nothing else is accepted. */
    public static function clean($id): ?string
    {
        $id = strtolower(trim((string)$id));
        return preg_match('/^[0-9a-f]{32,64}$/', $id) ? $id : null;
    }

    public static function list(int $userId): array
    {
        return Db::all('SELECT * FROM {pro_devices} WHERE user_id = ? ORDER BY seen_at DESC', [$userId]);
    }

    /** Adds this device while there is room; tells whether this device holds Pro. */
    public static function boundTo(array $user, ?string $device, ?string $name): array
    {
        $uid = (int)$user['id'];
        if ($device === null) {
            $last = Db::one('SELECT name, bound_at FROM {pro_devices} WHERE user_id = ? ORDER BY seen_at DESC LIMIT 1', [$uid]);
            return ['here' => false, 'name' => $last['name'] ?? null, 'since' => $last ? Clock::ts($last['bound_at']) : null];
        }
        $row = Db::one('SELECT * FROM {pro_devices} WHERE user_id = ? AND device = ?', [$uid, $device]);
        if ($row !== null) {
            if (Clock::ts($row['seen_at']) < Clock::now() - 3600) Db::update('pro_devices', ['seen_at' => Clock::sql()] + ($name ? ['name' => mb_substr($name, 0, 80)] : []), 'id = ?', [$row['id']]);
            return ['here' => true, 'name' => $row['name'], 'since' => Clock::ts($row['bound_at'])];
        }
        $limit = self::limit();
        $count = (int)Db::value('SELECT COUNT(*) FROM {pro_devices} WHERE user_id = ?', [$uid]);
        if ($limit === 0 || $count < $limit) {
            self::bind($uid, $device, $name, 'first_use');
            return ['here' => true, 'name' => $name, 'since' => Clock::now()];
        }
        $last = Db::one('SELECT name, bound_at FROM {pro_devices} WHERE user_id = ? ORDER BY seen_at DESC LIMIT 1', [$uid]);
        return ['here' => false, 'name' => $last['name'] ?? null, 'since' => $last ? Clock::ts($last['bound_at']) : null];
    }

    /** Adds a device to the account; beyond the limit, the device used longest ago gives its place. */
    public static function bind(int $userId, string $device, ?string $name, string $why): void
    {
        $now = Clock::sql();
        $name = mb_substr((string)$name, 0, 80) ?: null;
        Db::run('INSERT INTO {pro_devices} (user_id, device, name, bound_at, seen_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE seen_at = VALUES(seen_at), name = COALESCE(VALUES(name), name)',
            [$userId, $device, $name, $now, $now]);
        $limit = self::limit();
        if ($limit > 0) {
            $extra = Db::all('SELECT id FROM {pro_devices} WHERE user_id = ? AND device <> ? ORDER BY seen_at DESC, id DESC LIMIT 1000 OFFSET ' . ($limit - 1), [$userId, $device]);
            foreach ($extra as $x) Db::run('DELETE FROM {pro_devices} WHERE id = ?', [$x['id']]);
        }
        // The latest device, for the panel's user page and older reports.
        Db::update('users', ['pro_device' => $device, 'pro_device_name' => $name, 'pro_device_at' => $now], 'id = ?', [$userId]);
        Audit::log('system', null, 'device_bound', 'user', $userId, $why, null, ['device' => substr($device, 0, 12), 'name' => $name]);
    }

    /** Frees every device of an account (panel); the next devices that sign in are added again. */
    public static function release(int $userId): void
    {
        Db::run('DELETE FROM {pro_devices} WHERE user_id = ?', [$userId]);
        Db::update('users', ['pro_device' => null, 'pro_device_name' => null, 'pro_device_at' => null], 'id = ?', [$userId]);
    }

    /** «انتقال اشتراک به این دستگاه» when the limit is full: needs the password; limited per 30 days. */
    public static function transfer(array $user, array $session, string $password): void
    {
        if (!password_verify($password, $user['password_hash'])) throw new ApiError('bad_credentials', 'رمز عبور درست نیست.', 401);
        $device = $session['device_id'] ?? null;
        if ($device === null) throw new ApiError('no_device', 'شناسهٔ این دستگاه دریافت نشد؛ برنامه را به‌روز کنید.');
        $u = Users::byId((int)$user['id']);
        if (Db::value('SELECT id FROM {pro_devices} WHERE user_id = ? AND device = ?', [$u['id'], $device]) !== null) return;
        $since = Clock::ts($u['device_moves_since']);
        $moves = ($since !== null && $since > Clock::now() - 30 * 86400) ? (int)$u['device_moves'] : 0;
        $limit = (int)Settings::get('device_moves_limit', 3);
        if ($moves >= $limit) {
            Audit::security('device_transfer_blocked', (int)$u['id'], $u['username'], "moves=$moves");
            throw new ApiError('transfer_limit', 'سقف انتقال اشتراک (' . $limit . ' بار در ۳۰ روز) پر شده است. برای انتقال بیشتر با پشتیبانی تماس بگیرید.', 429);
        }
        Db::update('users', ['device_moves' => $moves + 1, 'device_moves_since' => $moves === 0 ? Clock::sql() : $u['device_moves_since']], 'id = ?', [$u['id']]);
        self::bind((int)$u['id'], $device, $session['device_name'] ?? null, 'transfer');
        Audit::security('device_transfer', (int)$u['id'], $u['username'], 'to ' . ($session['device_name'] ?? '?'));
    }
}

// ================================================================ discount and gift codes
final class Promo
{
    public const KINDS = ['gift_pro' => 'هدیهٔ Pro', 'gift_feature' => 'هدیهٔ یک قابلیت', 'discount' => 'تخفیف خرید'];

    public static function normalize(string $code): string { return strtoupper(preg_replace('/[^A-Za-z0-9]/', '', $code)); }

    public static function generate(int $len = 8): string
    {
        $a = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        $c = '';
        for ($i = 0; $i < $len; $i++) $c .= $a[random_int(0, strlen($a) - 1)];
        return $c;
    }

    /** The code row if this user may use it now, else an error saying why. */
    public static function find(string $code, array $user): array
    {
        if (!RateLimit::hit('promo:' . $user['id'], 20, 3600)) throw new ApiError('rate_limited', 'تلاش زیاد است؛ کمی بعد دوباره امتحان کنید.', 429);
        $p = Db::one('SELECT * FROM {promo_codes} WHERE code = ?', [self::normalize($code)]);
        if ($p === null || !$p['active']) {
            Audit::security('promo_invalid', (int)$user['id'], $user['username'], mb_substr($code, 0, 40));
            throw new ApiError('promo_invalid', 'این کد معتبر نیست.', 404);
        }
        $now = Clock::now();
        if ($p['starts_at'] !== null && Clock::ts($p['starts_at']) > $now) throw new ApiError('promo_not_started', 'زمان استفاده از این کد هنوز نرسیده است.', 409);
        if ($p['ends_at'] !== null && Clock::ts($p['ends_at']) <= $now) throw new ApiError('promo_expired', 'مهلت این کد تمام شده است.', 409);
        if (Db::value('SELECT id FROM {promo_redemptions} WHERE code_id = ? AND user_id = ?', [$p['id'], $user['id']]) !== null) throw new ApiError('promo_used', 'شما قبلاً از این کد استفاده کرده‌اید.', 409);
        if ($p['max_uses'] !== null && (int)$p['uses'] >= (int)$p['max_uses']) throw new ApiError('promo_used_up', 'ظرفیت این کد پر شده است.', 409);
        $users = array_filter(explode(',', (string)$p['users']));
        if ($users && !in_array((string)$user['id'], $users, true)) throw new ApiError('promo_not_yours', 'این کد برای حساب شما نیست.', 403);
        return $p;
    }

    /** What the code gives, in words and numbers, for the app. */
    public static function describe(array $p, string $store): array
    {
        $out = ['code' => $p['code'], 'kind' => $p['kind'], 'percent' => $p['percent'] === null ? null : (int)$p['percent'], 'plan' => $p['plan_code'], 'days' => $p['days'] === null ? null : (int)$p['days']];
        if ($p['kind'] === 'gift_pro') $out['title'] = $p['days'] === null ? 'هدیه: Pro مادام‌العمر' : 'هدیه: ' . (int)$p['days'] . ' روز Pro';
        elseif ($p['kind'] === 'gift_feature') $out['title'] = 'هدیه: ' . (Db::value('SELECT title FROM {features} WHERE k = ?', [$p['feature_k']]) ?? $p['feature_k']) . ($p['days'] ? ' برای ' . (int)$p['days'] . ' روز' : '');
        else {
            $out['title'] = (int)$p['percent'] . '٪ تخفیف' . ($p['plan_code'] ? ' روی ' . (Plans::byCode($p['plan_code'])['title'] ?? $p['plan_code']) : ' روی همهٔ پلن‌ها');
            $out['usable_here'] = self::discountUsable($store);
            if (!$out['usable_here']) $out['note'] = 'کد تخفیف فقط در نسخهٔ کافه‌بازار قابل استفاده است.';
        }
        return $out;
    }

    /** A lower price for one purchase is possible only through Bazaar's signed dynamic price. */
    public static function discountUsable(string $store): bool
    {
        return $store === 'bazaar' && Settings::secret('bazaar_discount_key') !== '';
    }

    /** A discount code for a purchase on the site (web gateways charge the lowered price directly). */
    public static function forSite(string $code, ?array $user, string $planCode): array
    {
        if (!RateLimit::hit('promo-site:' . Http::ip(), 20, 3600)) throw new ApiError('rate_limited', 'تلاش زیاد است؛ کمی بعد دوباره امتحان کنید.', 429);
        $p = Db::one('SELECT * FROM {promo_codes} WHERE code = ?', [self::normalize($code)]);
        if ($p === null || !$p['active']) throw new ApiError('promo_invalid', 'این کد تخفیف معتبر نیست.', 404);
        if ($p['kind'] !== 'discount') throw new ApiError('promo_is_gift', 'این کد هدیه است، نه تخفیف؛ آن را در برنامه، بخش «دیوان Pro ← کد تخفیف یا هدیه» وارد کنید.', 409);
        $now = Clock::now();
        if ($p['starts_at'] !== null && Clock::ts($p['starts_at']) > $now) throw new ApiError('promo_not_started', 'زمان استفاده از این کد هنوز نرسیده است.', 409);
        if ($p['ends_at'] !== null && Clock::ts($p['ends_at']) <= $now) throw new ApiError('promo_expired', 'مهلت این کد تمام شده است.', 409);
        if ($p['max_uses'] !== null && (int)$p['uses'] >= (int)$p['max_uses']) throw new ApiError('promo_used_up', 'ظرفیت این کد پر شده است.', 409);
        if ($p['plan_code'] && $p['plan_code'] !== $planCode) throw new ApiError('promo_plan', 'این کد فقط برای «' . (Plans::byCode($p['plan_code'])['title'] ?? $p['plan_code']) . '» است.', 409);
        $users = array_filter(explode(',', (string)$p['users']));
        if ($users && ($user === null || !in_array((string)$user['id'], $users, true))) throw new ApiError('promo_not_yours', 'این کد برای حساب مشخصی است؛ نام کاربری همان حساب را بنویسید.', 403);
        if ($user !== null && Db::value('SELECT id FROM {promo_redemptions} WHERE code_id = ? AND user_id = ?', [$p['id'], $user['id']]) !== null) throw new ApiError('promo_used', 'این حساب قبلاً از این کد استفاده کرده است.', 409);
        return $p;
    }

    public static function discountedPrice(int $price, int $percent): int
    {
        $v = (int)(round($price * (100 - max(1, min(90, $percent))) / 100 / 100) * 100);
        return max(1000, min($price, $v));
    }

    /** Gift codes are used right away. */
    public static function redeemGift(string $code, array $user): array
    {
        $p = self::find($code, $user);
        if ($p['kind'] === 'discount') throw new ApiError('promo_is_discount', 'این کد تخفیف است؛ هنگام خرید پلن وارد شود.', 409);
        Db::tx(function () use ($p, $user) {
            Db::insert('promo_redemptions', ['code_id' => $p['id'], 'user_id' => $user['id'], 'created_at' => Clock::sql()]);
            Db::run('UPDATE {promo_codes} SET uses = uses + 1 WHERE id = ?', [$p['id']]);
            Db::insert('user_grants', [
                'user_id' => $user['id'], 'feature_k' => $p['kind'] === 'gift_pro' ? 'pro' : $p['feature_k'],
                'expires_at' => $p['days'] === null ? null : Clock::sql(Clock::now() + (int)$p['days'] * 86400),
                'reason' => 'کد ' . $p['code'], 'admin_id' => $p['admin_id'], 'created_at' => Clock::sql(),
            ]);
        });
        Audit::log('user', (int)$user['id'], 'promo_redeemed', 'promo', $p['id'], $p['code']);
        return $p;
    }

    /** Called when a discounted purchase is confirmed. */
    public static function used(int $promoId, int $userId, int $purchaseId): void
    {
        try {
            Db::insert('promo_redemptions', ['code_id' => $promoId, 'user_id' => $userId, 'purchase_id' => $purchaseId, 'created_at' => Clock::sql()]);
            Db::run('UPDATE {promo_codes} SET uses = uses + 1 WHERE id = ?', [$promoId]);
        } catch (PDOException $e) { /* already recorded */ }
    }
}

// ================================================================ feature flags
final class Features
{
    public const MODES = ['free', 'pro', 'limited', 'disabled', 'gift'];

    /** Modules whose single items (scenario, card, mode, style) can be Pro on their own. */
    public const ITEM_MODULES = ['mafia_scenarios', 'mafia_last_cards', 'panto_modes', 'themes'];

    /** Every module of the app; all free by default so nobody loses what they have today. */
    public const SECTIONS = ['games' => 'بازی‌های امتیازی', 'mafia' => 'راوی مافیا', 'party' => 'پانتومیم و جاسوس', 'general' => 'ابزارها و ظاهر'];

    /** Every module of the app; all free by default so nobody loses what they have today. [key, title, description, section] */
    public static function defaults(): array
    {
        return [
            ['shalam', 'داوری شلم', 'ثبت دست، جمع امتیاز و پایان بازی', 'games'],
            ['menfi', 'داوری منفی', 'ثبت عددها و حالت رخ‌داده', 'games'],
            ['hezartaii', 'هزارتایی', 'رقابت انفرادی تا هدف', 'games'],
            ['dolo', 'دو لو گشنیز', 'حکم، حداقل و حذف', 'games'],
            ['mafia', 'راوی مافیا (کل بخش)', 'گردانندگی شب و روز', 'mafia'],
            ['mafia_scenarios', 'سناریوهای مافیا', 'هر سناریو را جدا می‌توان Pro کرد', 'mafia'],
            ['mafia_last_cards', 'کارت حرکت آخر', 'کل کارت‌ها یا هر کارت جدا', 'mafia'],
            ['mafia_custom', 'ساخت و ویرایش سناریو و نقش', 'سناریوی دلخواه، نقش تازه و ویرایش قوانین', 'mafia'],
            ['mafia_groups', 'گروه‌های بازی مافیا', 'ذخیرهٔ بازیکنان و نقش‌ها', 'mafia'],
            ['mafia_report', 'گزارش بازی مافیا', 'گزارش کامل پس از بازی', 'mafia'],
            ['mafia_audio', 'صدا و موسیقی دلخواه راوی', 'فایل صوتی از گوشی و ضبط صدای خود راوی', 'mafia'],
            ['panto', 'پانتومیم (کل بخش)', 'داوری مسابقهٔ تیمی', 'party'],
            ['panto_modes', 'نوع مسابقهٔ پانتومیم', 'هر نوع مسابقه را جدا می‌توان Pro کرد', 'party'],
            ['panto_words', 'کلمات من در پانتومیم', 'افزودن واژه‌های شخصی', 'party'],
            ['spy', 'جاسوس (کل بخش)', 'پخش نقش و زمان‌سنج', 'party'],
            ['spy_words', 'کلمات من در جاسوس', 'افزودن کلمه‌های شخصی', 'party'],
            ['leagues', 'لیگ‌ها', 'لیگ دوره‌ای و حذفی؛ محدودیت = تعداد لیگ', 'general'],
            ['rankings', 'رنک‌بندی', 'رتبه و رکورد', 'general'],
            ['profiles', 'کارنامهٔ افراد و تیم‌ها', 'آمار و کارنامهٔ هر نفر', 'general'],
            ['history', 'تاریخچه', 'ادامه و مرور بازی‌ها', 'general'],
            ['charts', 'نمودار و آمار کامل بازی', 'نمودار نتیجه و آمار شلم', 'general'],
            ['photos', 'عکس از گالری', 'عکس افراد، تیم‌ها و لیگ‌ها؛ محدودیت = تعداد عکس', 'general'],
            ['themes', 'سبک‌های ظاهری', 'هر سبک را جدا می‌توان Pro کرد', 'general'],
            ['backup', 'پشتیبان‌گیری محلی', 'ذخیره و بازگردانی فایل', 'general'],
            ['cloud_backup', 'پشتیبان ابری', 'نگهداری پشتیبان بازی‌ها روی سرور', 'general'],
            ['share', 'اشتراک تصویر', 'اشتراک تصویر بازی و نتیجه', 'general'],
            ['deck_calc', 'ماشین‌حساب ورق', 'پخش و ورق اضافه', 'general'],
        ];
    }

    public static function all(): array { return Db::all('SELECT * FROM {features} ORDER BY sort, k'); }

    /** Mode, limit and whether this user may use each module. */
    public static function forUser(?int $userId, bool $pro): array
    {
        $grants = [];
        if ($userId !== null) {
            foreach (Db::all('SELECT feature_k FROM {user_grants} WHERE user_id = ? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)', [$userId, Clock::sql()]) as $g) $grants[$g['feature_k']] = true;
        }
        $out = [];
        foreach (self::all() as $f) {
            $mode = $f['mode'];
            $granted = isset($grants[$f['k']]);
            switch ($mode) {
                case 'free': $allowed = true; break;
                case 'pro': $allowed = $pro || $granted; break;
                case 'limited': $allowed = true; break;
                case 'gift': $allowed = $granted; break;
                default: $allowed = false;
            }
            $listed = array_values(array_filter(array_map('trim', explode(',', (string)($f['pro_items'] ?? '')))));
            $allItems = false;
            if (in_array($f['k'], self::ITEM_MODULES, true)) {
                // Ticked items: only those are Pro and the section stays open. Nothing ticked: the mode covers every item.
                if ($listed && $mode !== 'disabled') $allowed = true;
                elseif (!$allowed) $allItems = true;
            }
            $out[$f['k']] = [
                'mode' => $mode,
                'allowed' => $allowed,
                'granted' => $granted,
                'limit' => ($mode === 'limited' && !$pro && !$granted && $f['limit_value'] !== null) ? (int)$f['limit_value'] : null,
                'pro_items' => ($pro || $granted) ? [] : $listed,
                'items' => $listed,
                'all_items' => $allItems,
            ];
        }
        return $out;
    }
}

// ================================================================ ads
final class Ads
{
    public const FORMATS = [
        'banner_small' => 'بنر کوچک', 'banner_wide' => 'بنر افقی', 'corner' => 'کادر گوشه', 'card' => 'کارت وسط صفحه',
        'image' => 'بنر تصویری', 'text' => 'تبلیغ متنی', 'video' => 'ویدئو', 'native' => 'همسان با طراحی اپ',
        'interstitial' => 'بین دو فعالیت', 'rewarded' => 'ویدئوی جایزه‌ای', 'sponsor' => 'اسپانسری',
    ];
    public const EVENTS = ['request', 'fill', 'impression', 'click', 'close', 'reward', 'error'];

    /** Pages where a fixed bar may appear (never game boards, timers, mafia night/vote or score entry). */
    public const ZONES = [
        'home' => 'خانه', 'history' => 'تاریخچه', 'result' => 'نتیجهٔ بازی پایان‌یافته', 'ranking' => 'رنک‌بندی', 'settings' => 'تنظیمات',
        'leagues' => 'فهرست لیگ‌ها', 'players' => 'افراد و تیم‌ها', 'hub' => 'صفحهٔ اصلی مافیا، پانتومیم و جاسوس', 'about' => 'درباره',
    ];

    /** In-app destinations an ad button can open. */
    public const APP_TARGETS = [
        'divan://pro' => 'صفحهٔ خرید Pro', 'divan://plan/m1' => 'خرید پلن یک‌ماهه', 'divan://plan/m3' => 'خرید پلن سه‌ماهه',
        'divan://plan/m6' => 'خرید پلن شش‌ماهه', 'divan://plan/life' => 'خرید پلن مادام‌العمر', 'divan://subscription' => 'اشتراک من',
        'divan://rewarded' => 'ویدئوی جایزه‌ای (ساعتی بدون تبلیغ)', 'divan://screen/mafia' => 'بخش مافیا', 'divan://screen/panto' => 'بخش پانتومیم',
        'divan://screen/spy' => 'بخش جاسوس', 'divan://screen/leagues' => 'لیگ‌ها', 'divan://screen/themes' => 'سبک‌های ظاهری',
    ];

    /**
     * Safe slots only: never on game boards, timers, mafia night/vote, role reveal or result entry.
     * [key, title, screen, position, direct formats, network formats]
     */
    public static function defaultPlacements(): array
    {
        return [
            ['home_inline', 'خانه — میان بخش‌ها', 'home', 'inline', 'card,image,text,native,video,sponsor,banner_wide', 'banner,native'],
            ['home_bottom', 'خانه — پایین صفحه', 'home', 'bottom', 'banner_small,banner_wide,text', 'banner'],
            ['home_corner', 'خانه — کادر گوشه', 'home', 'corner', 'corner', ''],
            ['history_inline', 'تاریخچه — میان فهرست', 'history', 'inline', 'card,image,text,native,banner_wide', 'banner,native'],
            ['hub_inline', 'صفحهٔ بازی‌ها (مافیا، پانتومیم، جاسوس)', 'hub', 'inline', 'card,image,text,native,sponsor', 'banner,native'],
            ['result_bottom', 'صفحهٔ نتیجهٔ پایان‌یافته — پایین', 'result', 'bottom', 'card,image,text,native,video,sponsor', 'banner,native'],
            ['ranking_inline', 'رنک‌بندی — پایین فهرست', 'ranking', 'inline', 'card,image,text,native', 'banner,native'],
            ['settings_bottom', 'تنظیمات — پایین صفحه', 'settings', 'bottom', 'banner_small,banner_wide,text', 'banner'],
            ['between_games', 'بین دو فعالیت (پس از بستن نتیجه)', 'transition', 'interstitial', 'interstitial,video', 'interstitial'],
            ['rewarded_offer', 'ویدئوی جایزه‌ای (اختیاری)', 'offer', 'rewarded', 'rewarded', 'rewarded'],
            ['sticky_bottom', 'نوار ثابت پایین صفحه', 'any', 'sticky', 'banner_small,banner_wide,text,image,native', 'banner'],
            ['sticky_top', 'نوار ثابت بالای صفحه', 'any', 'sticky', 'banner_small,banner_wide,text,image,native', 'banner'],
            // Divan's own pages around a web payment (site version, web, Windows, iPhone).
            ['pay_before', 'صفحهٔ پیش از پرداخت (نسخهٔ سایت)', 'pay', 'inline', 'card,image,text,sponsor', ''],
            ['pay_after', 'صفحهٔ پس از پرداخت (نسخهٔ سایت)', 'pay', 'inline', 'card,image,text,sponsor', ''],
            // The introduction site (httpuser87890.ir); shown only while «تبلیغات سایت» is on in «مدیریت سایت».
            ['site_top', 'سایت — زیر معرفی بالای صفحهٔ اصلی', 'site', 'inline', 'card,image,text,sponsor,banner_wide', ''],
            ['site_middle', 'سایت — میان بخش‌های صفحهٔ اصلی', 'site', 'inline', 'card,image,text,sponsor,banner_wide', ''],
            ['site_bottom', 'سایت — پیش از بخش دریافت', 'site', 'inline', 'card,image,text,sponsor,banner_wide', ''],
            ['site_pages', 'سایت — بالای صفحه‌های دیگر (Pro، دریافت، …)', 'site', 'inline', 'card,image,text,sponsor,banner_wide', ''],
        ];
    }

    public static function placements(): array { return Db::all('SELECT * FROM {placements} ORDER BY sort, k'); }

    /** One direct ad for a web page (Divan's payment pages, the site): text, image and an https link only. */
    public static function pick(string $placement, bool $count = true): ?array
    {
        if ((int)(Db::value('SELECT active FROM {placements} WHERE k = ?', [$placement]) ?? 0) !== 1) return null;
        $now = Clock::sql();
        foreach (Db::all('SELECT * FROM {ads} WHERE active = 1 AND (starts_at IS NULL OR starts_at <= ?) AND (ends_at IS NULL OR ends_at > ?) ORDER BY priority DESC, id', [$now, $now]) as $a) {
            if (!in_array($placement, explode(',', (string)$a['placements']), true)) continue;
            if ($a['total_cap'] !== null && (int)$a['impressions_total'] >= (int)$a['total_cap']) continue;
            $media = self::mediaUrl($a['media_id'] === null ? null : (int)$a['media_id']);
            $target = (string)$a['target_url'];
            if ($count) self::record([['event' => 'impression', 'source' => 'direct', 'placement' => $placement, 'ad_id' => (int)$a['id']]]);
            return ['title' => (string)$a['title'], 'body' => (string)$a['body'], 'cta' => (string)($a['cta_text'] ?? ''),
                'image' => $media !== null && $media['kind'] === 'image' ? $media['url'] : null,
                'url' => preg_match('#^https://#i', $target) && safe_target_url($target) ? $target : null];
        }
        return null;
    }

    public static function mediaUrl(?int $mediaId): ?array
    {
        if ($mediaId === null) return null;
        $m = Db::one('SELECT * FROM {media} WHERE id = ?', [$mediaId]);
        if ($m === null) return null;
        return ['url' => App::baseUrl() . '/media/' . $m['path'], 'kind' => $m['kind'], 'mime' => $m['mime'], 'width' => $m['width'], 'height' => $m['height'], 'size' => (int)$m['size'], 'sha256' => $m['sha256']];
    }

    /** Ads configuration for one app; Pro users receive nothing to load at all. */
    public static function configFor(string $store, bool $pro): array
    {
        if ($pro || !Settings::get('ads_enabled', false)) return ['enabled' => false];
        $now = Clock::sql();
        $ads = [];
        foreach (Db::all("SELECT * FROM {ads} WHERE active = 1 AND (starts_at IS NULL OR starts_at <= ?) AND (ends_at IS NULL OR ends_at > ?) ORDER BY priority DESC, id", [$now, $now]) as $a) {
            if (!in_array($store, explode(',', $a['stores']), true)) continue;
            if ($a['total_cap'] !== null && (int)$a['impressions_total'] >= (int)$a['total_cap']) continue;
            $ads[] = [
                'id' => (int)$a['id'], 'format' => $a['format'], 'title' => $a['title'], 'body' => $a['body'],
                'media' => self::mediaUrl($a['media_id'] === null ? null : (int)$a['media_id']),
                'target_url' => $a['target_url'], 'cta' => $a['cta_text'], 'style' => json_decode((string)$a['style_json'], true) ?: new stdClass(),
                'closable' => (bool)$a['closable'], 'daily_cap' => $a['daily_cap'] === null ? null : (int)$a['daily_cap'],
                'min_interval_sec' => (int)$a['min_interval_sec'], 'priority' => (int)$a['priority'],
                'placements' => array_values(array_filter(explode(',', $a['placements']))),
                'ends_at' => Clock::ts($a['ends_at']),
            ];
        }
        $tapsell = Settings::get('tapsell', []);
        return [
            'enabled' => true,
            'priority' => Settings::get('ads_priority', 'direct_first'),
            'interstitial_min_minutes' => (int)Settings::get('ads_interstitial_min_minutes', 10),
            'interstitial_daily_cap' => (int)Settings::get('ads_daily_interstitial_cap', 3),
            'rewarded_ad_free_minutes' => (int)Settings::get('rewarded_ad_free_minutes', 60),
            'placements' => array_map(fn($p) => [
                'key' => $p['k'], 'screen' => $p['screen'], 'position' => $p['position'], 'active' => (bool)$p['active'],
                'screens' => array_values(array_filter(explode(',', (string)($p['screens'] ?? '')))),
                'formats' => array_values(array_filter(explode(',', $p['formats']))),
                'network_formats' => array_values(array_filter(explode(',', (string)$p['network_formats']))),
            ], self::placements()),
            'ads' => $ads,
            'network' => [
                'provider' => 'tapsell',
                'enabled' => (bool)($tapsell['enabled'] ?? false),
                'test' => (bool)($tapsell['test'] ?? false),
                'zones' => $tapsell['zones'] ?? new stdClass(),
            ],
        ];
    }

    /** Adds a batch of counters sent by the app (aggregated per day, ad, source and slot). */
    public static function record(array $events): int
    {
        $n = 0;
        $placements = array_column(self::placements(), 'k');
        foreach (array_slice($events, 0, 300) as $ev) {
            if (!is_array($ev)) continue;
            $event = (string)($ev['event'] ?? '');
            $source = ($ev['source'] ?? '') === 'network' ? 'network' : 'direct';
            $placement = (string)($ev['placement'] ?? '');
            $count = max(1, min(500, (int)($ev['count'] ?? 1)));
            $day = (string)($ev['day'] ?? gmdate('Y-m-d', Clock::now()));
            if (!in_array($event, self::EVENTS, true) || !in_array($placement, $placements, true) || !preg_match('/^\d{4}-\d{2}-\d{2}$/', $day)) continue;
            if (strtotime($day . ' UTC') < Clock::now() - 14 * 86400 || strtotime($day . ' UTC') > Clock::now() + 86400) continue;
            $adId = $source === 'direct' ? max(0, (int)($ev['ad_id'] ?? 0)) : 0;
            $col = ['request' => 'requests', 'fill' => 'fills', 'impression' => 'impressions', 'click' => 'clicks', 'close' => 'closes', 'reward' => 'rewards', 'error' => 'errors'][$event];
            Db::run("INSERT INTO {ad_stats} (day, ad_id, source, placement, $col) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE $col = $col + VALUES($col)", [$day, $adId, $source, $placement, $count]);
            if ($event === 'impression' && $adId > 0) Db::run('UPDATE {ads} SET impressions_total = impressions_total + ? WHERE id = ?', [$count, $adId]);
            $n++;
        }
        return $n;
    }
}

// ================================================================ media uploads
final class Media
{
    public const IMAGE_MAX = 2 * 1024 * 1024;
    public const VIDEO_MAX = 15 * 1024 * 1024;
    private const TYPES = [
        'image/jpeg' => ['image', 'jpg'], 'image/png' => ['image', 'png'], 'image/webp' => ['image', 'webp'],
        'image/gif' => ['gif', 'gif'], 'video/mp4' => ['video', 'mp4'],
    ];

    /** Stores an uploaded file after checking its real type, size and image dimensions. */
    public static function store(array $file): int
    {
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || !is_uploaded_file($file['tmp_name'] ?? '')) {
            if (!(defined('DIVAN_TEST') && is_file($file['tmp_name'] ?? ''))) throw new ApiError('upload', 'فایل بارگذاری نشد.');
        }
        $tmp = $file['tmp_name'];
        $mime = (new finfo(FILEINFO_MIME_TYPE))->file($tmp) ?: '';
        if (!isset(self::TYPES[$mime])) throw new ApiError('upload_type', 'فقط JPG، PNG، WebP، GIF و MP4 پذیرفته می‌شود.');
        [$kind, $ext] = self::TYPES[$mime];
        $size = (int)filesize($tmp);
        $max = $kind === 'video' ? self::VIDEO_MAX : self::IMAGE_MAX;
        if ($size <= 0 || $size > $max) throw new ApiError('upload_size', 'حجم فایل بیش از حد مجاز است (تصویر ۲ و ویدئو ۱۵ مگابایت).');
        $w = $h = null;
        if ($kind !== 'video') {
            $info = @getimagesize($tmp);
            if ($info === false) throw new ApiError('upload_image', 'تصویر معتبر نیست.');
            [$w, $h] = $info;
            if ($w > 4096 || $h > 4096) throw new ApiError('upload_image', 'ابعاد تصویر بیش از ۴۰۹۶ پیکسل است.');
        } else {
            $head = (string)file_get_contents($tmp, false, null, 0, 64);
            if (strpos($head, 'ftyp') === false) throw new ApiError('upload_video', 'ویدئوی MP4 معتبر نیست.');
        }
        $dir = gmdate('Y/m');
        $abs = App::$root . '/media/' . $dir;
        if (!is_dir($abs) && !mkdir($abs, 0755, true)) throw new ApiError('upload_dir', 'پوشهٔ رسانه قابل نوشتن نیست.', 500);
        $name = bin2hex(random_bytes(12)) . '.' . $ext;
        $dest = $abs . '/' . $name;
        $moved = defined('DIVAN_TEST') ? copy($tmp, $dest) : move_uploaded_file($tmp, $dest);
        if (!$moved) throw new ApiError('upload_move', 'ذخیرهٔ فایل ممکن نشد.', 500);
        @chmod($dest, 0644);
        return Db::insert('media', [
            'kind' => $kind, 'path' => $dir . '/' . $name, 'mime' => $mime, 'size' => $size, 'width' => $w, 'height' => $h,
            'sha256' => hash_file('sha256', $dest), 'original_name' => mb_substr(basename((string)($file['name'] ?? '')), 0, 190), 'created_at' => Clock::sql(),
        ]);
    }

    public static function delete(int $id): void
    {
        $m = Db::one('SELECT * FROM {media} WHERE id = ?', [$id]);
        if ($m === null) return;
        if ((int)Db::value('SELECT COUNT(*) FROM {ads} WHERE media_id = ?', [$id]) > 0) throw new ApiError('in_use', 'این فایل در یک تبلیغ استفاده می‌شود.');
        @unlink(App::$root . '/media/' . $m['path']);
        Db::run('DELETE FROM {media} WHERE id = ?', [$id]);
    }
}

// ================================================================ support
final class Support
{
    public const KINDS = ['recovery' => 'بازیابی حساب', 'purchase' => 'خرید و اشتراک', 'refund' => 'بازپرداخت', 'other' => 'سایر'];

    public static function create(?int $userId, array $in): int
    {
        if (!RateLimit::hit('support:' . Http::ip(), 6, 3600)) throw new ApiError('rate_limited', 'درخواست‌های زیادی ثبت شده؛ کمی بعد دوباره تلاش کنید.', 429);
        $msg = trim((string)($in['message'] ?? ''));
        if (mb_strlen($msg) < 10) throw new ApiError('short', 'شرح درخواست را کامل‌تر بنویسید.');
        $kind = array_key_exists($in['kind'] ?? '', self::KINDS) ? $in['kind'] : 'other';
        return Db::insert('support_requests', [
            'user_id' => $userId, 'username' => mb_substr((string)($in['username'] ?? ''), 0, 64) ?: null,
            'contact' => mb_substr((string)($in['contact'] ?? ''), 0, 120) ?: null, 'kind' => $kind,
            'store' => in_array($in['store'] ?? '', Plans::STORES, true) ? $in['store'] : null,
            'order_id' => mb_substr((string)($in['order_id'] ?? ''), 0, 128) ?: null,
            'message' => mb_substr($msg, 0, 4000), 'status' => 'open', 'created_at' => Clock::sql(), 'updated_at' => Clock::sql(),
        ]);
    }

    public static function action(?int $requestId, ?int $userId, int $adminId, string $action, string $reason, array $details = []): void
    {
        if (trim($reason) === '') throw new ApiError('reason', 'برای این اقدام، دلیل را بنویسید.');
        Db::insert('support_actions', [
            'request_id' => $requestId, 'user_id' => $userId, 'admin_id' => $adminId, 'action' => $action,
            'reason' => mb_substr($reason, 0, 500), 'details' => $details ? json_encode($details, JSON_UNESCAPED_UNICODE) : null, 'created_at' => Clock::sql(),
        ]);
    }
}

// ================================================================ cloud copy of the app's own game backup (optional)
final class UserData
{
    public const MAX = 5 * 1024 * 1024;

    public static function put(int $userId, string $json): int
    {
        if (strlen($json) > self::MAX) throw new ApiError('too_large', 'پشتیبان بزرگ‌تر از ۵ مگابایت است.', 413);
        if (json_decode($json) === null) throw new ApiError('bad_json', 'پشتیبان معتبر نیست.');
        Db::run('REPLACE INTO {user_backups} (user_id, data_enc, size, updated_at) VALUES (?, ?, ?, ?)', [$userId, Crypto::encrypt(gzcompress($json, 6)), strlen($json), Clock::sql()]);
        return strlen($json);
    }

    public static function get(int $userId): ?array
    {
        $r = Db::one('SELECT * FROM {user_backups} WHERE user_id = ?', [$userId]);
        if ($r === null) return null;
        $raw = Crypto::decrypt($r['data_enc']);
        return $raw === null ? null : ['data' => gzuncompress($raw), 'updated_at' => Clock::ts($r['updated_at']), 'size' => (int)$r['size']];
    }
}

// ================================================================ promotional notifications
/**
 * Campaigns shown in the phone's notification bar. Delivery is by the app asking now and then (a few
 * times a day, when the phone allows it): not instant push. Only players who said yes get them; Pro
 * players never get them unless they turned them on themselves.
 */
final class Notices
{
    public const AUDIENCES = ['free' => 'فقط کاربران رایگان', 'all' => 'همه (کاربران Pro فقط اگر خودشان روشن کرده باشند)'];
    public const STORES = ['bazaar' => 'کافه‌بازار', 'myket' => 'مایکت', 'direct' => 'نسخهٔ سایت'];

    public static function enabled(): bool { return (bool)Settings::get('notices_enabled', true); }
    public static function dailyCap(): int { return max(1, min(5, (int)Settings::get('notices_daily_cap', 1))); }

    /** Device ids are stored only as a keyed hash. */
    public static function deviceHash(string $device): string { return Crypto::hash('notice|' . $device); }

    /** What one phone should show now (at most one, never twice, within the daily cap). */
    public static function due(string $device, string $store, bool $pro, bool $proOptIn): array
    {
        if (!self::enabled() || !preg_match('/^[a-f0-9]{16,64}$/', $device) || !isset(self::STORES[$store])) return [];
        if ($pro && !$proOptIn) return [];
        $h = self::deviceHash($device);
        $today = (int)Db::value('SELECT COUNT(*) FROM {notice_events} WHERE device_hash = ? AND shown_at >= ?', [$h, Clock::sql(Clock::now() - 86400)]);
        if ($today >= self::dailyCap()) return [];
        $now = Clock::sql();
        foreach (Db::all("SELECT * FROM {notices} WHERE active = 1 AND canceled_at IS NULL AND (send_at IS NULL OR send_at <= ?) AND (ends_at IS NULL OR ends_at > ?)
                ORDER BY COALESCE(send_at, created_at), id", [$now, $now]) as $n) {
            if (!in_array($store, explode(',', (string)$n['stores']), true)) continue;
            if ($pro && $n['audience'] !== 'all') continue;
            // Store versions: only in-app pages (store rules on ads); web links only in the site version.
            if ($store !== 'direct' && stripos((string)$n['target'], 'https://') === 0) continue;
            if (Db::value('SELECT id FROM {notice_events} WHERE notice_id = ? AND device_hash = ?', [$n['id'], $h]) !== null) continue;
            $media = Ads::mediaUrl($n['media_id'] === null ? null : (int)$n['media_id']);
            return [[
                'id' => (int)$n['id'], 'title' => $n['title'], 'body' => $n['body'],
                'image' => $media !== null && $media['kind'] === 'image' ? $media['url'] : null,
                'target' => (string)($n['target'] ?? ''),
            ]];
        }
        return [];
    }

    /** The app reports «shown» and «opened»; each counts once per phone. */
    public static function event(int $id, string $device, string $kind): void
    {
        if (!preg_match('/^[a-f0-9]{16,64}$/', $device) || !in_array($kind, ['shown', 'opened'], true)) throw new ApiError('bad_event', 'رویداد نامعتبر است.');
        if (Db::value('SELECT id FROM {notices} WHERE id = ?', [$id]) === null) return;
        $h = self::deviceHash($device);
        $row = Db::one('SELECT * FROM {notice_events} WHERE notice_id = ? AND device_hash = ?', [$id, $h]);
        if ($row === null) {
            try {
                Db::insert('notice_events', ['notice_id' => $id, 'device_hash' => $h, 'shown_at' => Clock::sql(), 'opened_at' => $kind === 'opened' ? Clock::sql() : null]);
                Db::run('UPDATE {notices} SET shown = shown + 1' . ($kind === 'opened' ? ', opened = opened + 1' : '') . ' WHERE id = ?', [$id]);
            } catch (PDOException $e) { /* the same report arrived twice at once */ }
        } elseif ($kind === 'opened' && $row['opened_at'] === null) {
            Db::update('notice_events', ['opened_at' => Clock::sql()], 'id = ?', [$row['id']]);
            Db::run('UPDATE {notices} SET opened = opened + 1 WHERE id = ?', [$id]);
        }
    }

    public static function validTarget(string $t): bool
    {
        if ($t === '') return true;
        if (strpos($t, 'divan://') === 0) return safe_target_url($t);
        return stripos($t, 'https://') === 0 && safe_target_url($t);
    }
}
