<?php
/**
 * Database schema, versioned. Each migration runs once; later releases only append new entries,
 * so upgrading an installation never drops data.
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

final class Migrations
{
    /** @return array<int, string[]> */
    public static function all(): array
    {
        $e = 'ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci';
        return [
            1 => [
                "CREATE TABLE IF NOT EXISTS {migrations} (version INT PRIMARY KEY, applied_at DATETIME NOT NULL) $e",
                "CREATE TABLE IF NOT EXISTS {settings} (k VARCHAR(100) PRIMARY KEY, v MEDIUMTEXT NULL, updated_at DATETIME NOT NULL) $e",
                "CREATE TABLE IF NOT EXISTS {users} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(32) NOT NULL,
                    username_norm VARCHAR(32) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    recovery_hash VARCHAR(255) NULL,
                    recovery_created_at DATETIME NULL,
                    email VARCHAR(190) NULL,
                    phone VARCHAR(20) NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'active',
                    failed_logins INT NOT NULL DEFAULT 0,
                    locked_until DATETIME NULL,
                    note TEXT NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    last_login_at DATETIME NULL,
                    UNIQUE KEY uq_username (username_norm)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {sessions} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NOT NULL,
                    token_hash CHAR(64) NOT NULL,
                    device_name VARCHAR(80) NULL,
                    platform VARCHAR(20) NULL,
                    app_version VARCHAR(20) NULL,
                    store VARCHAR(16) NULL,
                    ip VARCHAR(45) NULL,
                    created_at DATETIME NOT NULL,
                    last_seen_at DATETIME NOT NULL,
                    expires_at DATETIME NOT NULL,
                    revoked_at DATETIME NULL,
                    UNIQUE KEY uq_token (token_hash),
                    KEY k_user (user_id)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {plans} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(32) NOT NULL,
                    title VARCHAR(80) NOT NULL,
                    description VARCHAR(400) NULL,
                    duration_days INT NULL,
                    base_price_toman INT NOT NULL,
                    sort INT NOT NULL DEFAULT 0,
                    active TINYINT NOT NULL DEFAULT 1,
                    badge VARCHAR(20) NULL,
                    badge_text VARCHAR(40) NULL,
                    badge_color VARCHAR(16) NULL,
                    renewal_note VARCHAR(300) NULL,
                    purchase_limit INT NULL,
                    sku_bazaar VARCHAR(64) NULL,
                    sku_myket VARCHAR(64) NULL,
                    type_bazaar VARCHAR(16) NOT NULL DEFAULT 'consumable',
                    type_myket VARCHAR(16) NOT NULL DEFAULT 'consumable',
                    store_price_bazaar INT NULL,
                    store_price_myket INT NULL,
                    updated_at DATETIME NOT NULL,
                    UNIQUE KEY uq_code (code)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {campaigns} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(80) NOT NULL,
                    plan_id INT UNSIGNED NOT NULL,
                    store VARCHAR(16) NOT NULL DEFAULT 'all',
                    price_toman INT NOT NULL,
                    label VARCHAR(40) NULL,
                    label_color VARCHAR(16) NULL,
                    starts_at DATETIME NULL,
                    ends_at DATETIME NULL,
                    active TINYINT NOT NULL DEFAULT 1,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    KEY k_plan (plan_id)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {purchase_intents} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NOT NULL,
                    plan_id INT UNSIGNED NOT NULL,
                    store VARCHAR(16) NOT NULL,
                    payload VARCHAR(64) NOT NULL,
                    price_toman INT NOT NULL,
                    created_at DATETIME NOT NULL,
                    used_at DATETIME NULL,
                    UNIQUE KEY uq_payload (payload),
                    KEY k_user (user_id)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {purchases} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NOT NULL,
                    store VARCHAR(16) NOT NULL,
                    sku VARCHAR(64) NOT NULL,
                    plan_id INT UNSIGNED NULL,
                    product_type VARCHAR(16) NOT NULL,
                    order_id VARCHAR(128) NULL,
                    token_hash CHAR(64) NOT NULL,
                    token_enc TEXT NOT NULL,
                    payload VARCHAR(64) NULL,
                    amount_toman INT NULL,
                    purchase_time DATETIME NULL,
                    status VARCHAR(24) NOT NULL,
                    verify_method VARCHAR(24) NULL,
                    verify_attempts INT NOT NULL DEFAULT 0,
                    last_verified_at DATETIME NULL,
                    next_check_at DATETIME NULL,
                    auto_renewing TINYINT NOT NULL DEFAULT 0,
                    store_expires_at DATETIME NULL,
                    consumed TINYINT NOT NULL DEFAULT 0,
                    raw_enc MEDIUMTEXT NULL,
                    note VARCHAR(500) NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    UNIQUE KEY uq_token (store, token_hash),
                    KEY k_user (user_id),
                    KEY k_order (order_id),
                    KEY k_status (status)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {entitlements} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NOT NULL,
                    source VARCHAR(16) NOT NULL,
                    purchase_id BIGINT UNSIGNED NULL,
                    plan_code VARCHAR(32) NOT NULL,
                    starts_at DATETIME NOT NULL,
                    expires_at DATETIME NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'active',
                    reason VARCHAR(300) NULL,
                    admin_id INT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    KEY k_user (user_id),
                    UNIQUE KEY uq_purchase (purchase_id)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {features} (
                    k VARCHAR(64) PRIMARY KEY,
                    title VARCHAR(80) NOT NULL,
                    description VARCHAR(300) NULL,
                    mode VARCHAR(16) NOT NULL DEFAULT 'free',
                    limit_value INT NULL,
                    sort INT NOT NULL DEFAULT 0,
                    updated_at DATETIME NOT NULL
                ) $e",
                "CREATE TABLE IF NOT EXISTS {user_grants} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NOT NULL,
                    feature_k VARCHAR(64) NOT NULL,
                    expires_at DATETIME NULL,
                    reason VARCHAR(300) NOT NULL,
                    admin_id INT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    revoked_at DATETIME NULL,
                    KEY k_user (user_id)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {placements} (
                    k VARCHAR(48) PRIMARY KEY,
                    title VARCHAR(80) NOT NULL,
                    screen VARCHAR(40) NOT NULL,
                    position VARCHAR(16) NOT NULL,
                    formats VARCHAR(200) NOT NULL,
                    network_formats VARCHAR(200) NULL,
                    active TINYINT NOT NULL DEFAULT 1,
                    sort INT NOT NULL DEFAULT 0
                ) $e",
                "CREATE TABLE IF NOT EXISTS {media} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    kind VARCHAR(10) NOT NULL,
                    path VARCHAR(190) NOT NULL,
                    mime VARCHAR(40) NOT NULL,
                    size INT NOT NULL,
                    width INT NULL,
                    height INT NULL,
                    sha256 CHAR(64) NOT NULL,
                    original_name VARCHAR(190) NULL,
                    created_at DATETIME NOT NULL
                ) $e",
                "CREATE TABLE IF NOT EXISTS {ads} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(80) NOT NULL,
                    title VARCHAR(80) NULL,
                    body VARCHAR(300) NULL,
                    format VARCHAR(24) NOT NULL,
                    media_id INT UNSIGNED NULL,
                    target_url VARCHAR(500) NULL,
                    cta_text VARCHAR(30) NULL,
                    style_json VARCHAR(500) NULL,
                    closable TINYINT NOT NULL DEFAULT 1,
                    starts_at DATETIME NULL,
                    ends_at DATETIME NULL,
                    total_cap INT NULL,
                    daily_cap INT NULL,
                    min_interval_sec INT NOT NULL DEFAULT 0,
                    priority INT NOT NULL DEFAULT 0,
                    placements VARCHAR(400) NOT NULL,
                    stores VARCHAR(40) NOT NULL DEFAULT 'bazaar,myket',
                    active TINYINT NOT NULL DEFAULT 0,
                    impressions_total INT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL
                ) $e",
                "CREATE TABLE IF NOT EXISTS {ad_stats} (
                    day DATE NOT NULL,
                    ad_id INT UNSIGNED NOT NULL DEFAULT 0,
                    source VARCHAR(10) NOT NULL,
                    placement VARCHAR(48) NOT NULL,
                    requests INT NOT NULL DEFAULT 0,
                    fills INT NOT NULL DEFAULT 0,
                    impressions INT NOT NULL DEFAULT 0,
                    clicks INT NOT NULL DEFAULT 0,
                    closes INT NOT NULL DEFAULT 0,
                    rewards INT NOT NULL DEFAULT 0,
                    errors INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (day, ad_id, source, placement)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {revenue} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    day DATE NOT NULL,
                    source VARCHAR(16) NOT NULL,
                    amount_toman BIGINT NOT NULL,
                    label VARCHAR(12) NOT NULL,
                    note VARCHAR(300) NULL,
                    admin_id INT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    KEY k_day (day)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {support_requests} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NULL,
                    username VARCHAR(64) NULL,
                    contact VARCHAR(120) NULL,
                    kind VARCHAR(16) NOT NULL,
                    store VARCHAR(16) NULL,
                    order_id VARCHAR(128) NULL,
                    message TEXT NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'open',
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    KEY k_status (status)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {support_actions} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    request_id BIGINT UNSIGNED NULL,
                    user_id BIGINT UNSIGNED NULL,
                    admin_id INT UNSIGNED NULL,
                    action VARCHAR(40) NOT NULL,
                    reason VARCHAR(500) NOT NULL,
                    details TEXT NULL,
                    created_at DATETIME NOT NULL,
                    KEY k_req (request_id),
                    KEY k_user (user_id)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {admins} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    username VARCHAR(32) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    totp_enc VARCHAR(255) NULL,
                    totp_last_step BIGINT NOT NULL DEFAULT 0,
                    failed INT NOT NULL DEFAULT 0,
                    locked_until DATETIME NULL,
                    active TINYINT NOT NULL DEFAULT 1,
                    last_login_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    UNIQUE KEY uq_username (username)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {audit_log} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    actor_type VARCHAR(10) NOT NULL,
                    actor_id BIGINT NULL,
                    action VARCHAR(60) NOT NULL,
                    entity VARCHAR(40) NULL,
                    entity_id VARCHAR(64) NULL,
                    reason VARCHAR(500) NULL,
                    before_json MEDIUMTEXT NULL,
                    after_json MEDIUMTEXT NULL,
                    ip VARCHAR(45) NULL,
                    created_at DATETIME NOT NULL,
                    KEY k_entity (entity, entity_id),
                    KEY k_time (created_at)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {security_events} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    kind VARCHAR(40) NOT NULL,
                    user_id BIGINT UNSIGNED NULL,
                    username VARCHAR(64) NULL,
                    ip VARCHAR(45) NULL,
                    details VARCHAR(1000) NULL,
                    created_at DATETIME NOT NULL,
                    KEY k_time (created_at)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {rate_limits} (k CHAR(64) PRIMARY KEY, window_start INT NOT NULL, hits INT NOT NULL) $e",
                "CREATE TABLE IF NOT EXISTS {user_backups} (
                    user_id BIGINT UNSIGNED PRIMARY KEY,
                    data_enc MEDIUMTEXT NOT NULL,
                    size INT NOT NULL,
                    updated_at DATETIME NOT NULL
                ) $e",
                "CREATE TABLE IF NOT EXISTS {backups} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    file VARCHAR(190) NOT NULL,
                    size BIGINT NOT NULL,
                    sha256 CHAR(64) NOT NULL,
                    kind VARCHAR(12) NOT NULL,
                    status VARCHAR(12) NOT NULL,
                    note VARCHAR(300) NULL,
                    created_at DATETIME NOT NULL
                ) $e",
            ],
            // 5.1: one subscription works on one phone at a time.
            2 => [
                "ALTER TABLE {users} ADD COLUMN pro_device VARCHAR(64) NULL, ADD COLUMN pro_device_name VARCHAR(80) NULL,
                    ADD COLUMN pro_device_at DATETIME NULL, ADD COLUMN device_moves INT NOT NULL DEFAULT 0, ADD COLUMN device_moves_since DATETIME NULL",
                "ALTER TABLE {sessions} ADD COLUMN device_id VARCHAR(64) NULL",
            ],
            // 5.2: fixed ad bars on chosen pages; single items of a module (scenario, card, mode, style) as Pro.
            3 => [
                "ALTER TABLE {placements} ADD COLUMN screens VARCHAR(300) NULL",
                "ALTER TABLE {features} ADD COLUMN pro_items TEXT NULL, ADD COLUMN section VARCHAR(20) NULL",
            ],
            // 5.2: discount and gift codes.
            4 => [
                "CREATE TABLE IF NOT EXISTS {promo_codes} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    code VARCHAR(32) NOT NULL,
                    kind VARCHAR(12) NOT NULL,
                    days INT NULL,
                    feature_k VARCHAR(64) NULL,
                    plan_code VARCHAR(32) NULL,
                    percent INT NULL,
                    max_uses INT NULL,
                    uses INT NOT NULL DEFAULT 0,
                    users TEXT NULL,
                    starts_at DATETIME NULL,
                    ends_at DATETIME NULL,
                    active TINYINT NOT NULL DEFAULT 1,
                    note VARCHAR(300) NULL,
                    admin_id INT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    UNIQUE KEY uq_code (code)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {promo_redemptions} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    code_id INT UNSIGNED NOT NULL,
                    user_id BIGINT UNSIGNED NOT NULL,
                    purchase_id BIGINT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    UNIQUE KEY uq_once (code_id, user_id)
                ) $e",
                "ALTER TABLE {purchase_intents} ADD COLUMN promo_id INT UNSIGNED NULL",
            ],
            // 5.3: each store has its own price, list (struck-through) price and on/off switch; no price sync step.
            5 => [
                "ALTER TABLE {plans} ADD COLUMN on_bazaar TINYINT NOT NULL DEFAULT 1, ADD COLUMN on_myket TINYINT NOT NULL DEFAULT 1,
                    ADD COLUMN list_price_bazaar INT NULL, ADD COLUMN list_price_myket INT NULL",
                "UPDATE {plans} SET store_price_bazaar = base_price_toman WHERE store_price_bazaar IS NULL OR store_price_bazaar <= 0",
                "UPDATE {plans} SET store_price_myket = base_price_toman WHERE store_price_myket IS NULL OR store_price_myket <= 0",
            ],
            // 5.4: promotional notifications in the phone's notification bar (only with the player's consent).
            6 => [
                "CREATE TABLE IF NOT EXISTS {notices} (
                    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    title VARCHAR(80) NOT NULL,
                    body VARCHAR(300) NOT NULL,
                    media_id INT UNSIGNED NULL,
                    target VARCHAR(300) NULL,
                    audience VARCHAR(12) NOT NULL DEFAULT 'free',
                    stores VARCHAR(40) NOT NULL DEFAULT 'bazaar,myket',
                    send_at DATETIME NULL,
                    ends_at DATETIME NULL,
                    active TINYINT NOT NULL DEFAULT 1,
                    canceled_at DATETIME NULL,
                    shown INT NOT NULL DEFAULT 0,
                    opened INT NOT NULL DEFAULT 0,
                    admin_id INT UNSIGNED NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    KEY k_due (active, send_at)
                ) $e",
                "CREATE TABLE IF NOT EXISTS {notice_events} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    notice_id INT UNSIGNED NOT NULL,
                    device_hash CHAR(64) NOT NULL,
                    shown_at DATETIME NOT NULL,
                    opened_at DATETIME NULL,
                    UNIQUE KEY uq_once (notice_id, device_hash),
                    KEY k_device (device_hash, shown_at)
                ) $e",
                // 5.4: web payments (site version only); one row per checkout, settled once.
                "CREATE TABLE IF NOT EXISTS {pay_orders} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    code CHAR(20) NOT NULL,
                    user_id BIGINT UNSIGNED NOT NULL,
                    plan_id INT UNSIGNED NOT NULL,
                    method VARCHAR(32) NOT NULL,
                    channel VARCHAR(16) NOT NULL,
                    amount_toman INT NOT NULL,
                    amount_rial BIGINT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    gateway_ref VARCHAR(80) NULL,
                    ref_id VARCHAR(80) NULL,
                    purchase_id BIGINT UNSIGNED NULL,
                    attempts INT NOT NULL DEFAULT 0,
                    last_error VARCHAR(300) NULL,
                    expires_at DATETIME NULL,
                    paid_at DATETIME NULL,
                    created_at DATETIME NOT NULL,
                    updated_at DATETIME NOT NULL,
                    UNIQUE KEY uq_code (code),
                    UNIQUE KEY uq_ref (method, gateway_ref),
                    KEY k_user (user_id),
                    KEY k_status (status, created_at)
                ) $e",
            ],
            // 5.5: the gateway's page of each order, opened from Divan's own page before payment (pay/start.php).
            7 => [
                "ALTER TABLE {pay_orders} ADD COLUMN pay_url VARCHAR(600) NULL",
            ],
            // 5.5: buying on the site: for an account by its username, or for an activation code typed in the app.
            8 => [
                "ALTER TABLE {pay_orders} ADD COLUMN buyer VARCHAR(8) NOT NULL DEFAULT 'app', ADD COLUMN activation_hash CHAR(64) NULL,
                    ADD COLUMN activation_enc TEXT NULL, ADD COLUMN redeemed_by BIGINT UNSIGNED NULL, ADD COLUMN redeemed_at DATETIME NULL,
                    ADD UNIQUE KEY uq_activation (activation_hash)",
            ],
            // Discount codes on purchases from the site.
            9 => [
                "ALTER TABLE {pay_orders} ADD COLUMN promo_id INT UNSIGNED NULL, ADD COLUMN list_toman INT UNSIGNED NULL",
            ],
            // One account on all of its owner's devices and versions (Bazaar, Myket, site, web, Windows, iPhone), up to a
            // limit set in the panel. The phone each account was bound to so far is kept as its first device.
            10 => [
                "CREATE TABLE IF NOT EXISTS {pro_devices} (
                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT UNSIGNED NOT NULL,
                    device VARCHAR(64) NOT NULL,
                    name VARCHAR(80) NULL,
                    bound_at DATETIME NOT NULL,
                    seen_at DATETIME NOT NULL,
                    UNIQUE KEY uq_user_device (user_id, device),
                    KEY k_user (user_id)
                ) $e",
                "INSERT IGNORE INTO {pro_devices} (user_id, device, name, bound_at, seen_at)
                    SELECT id, pro_device, pro_device_name, COALESCE(pro_device_at, UTC_TIMESTAMP()), COALESCE(pro_device_at, UTC_TIMESTAMP()) FROM {users} WHERE pro_device IS NOT NULL",
            ],
        ];
    }

    /** Cheap check on every request: an updated ZIP upgrades the database by itself, without opening the panel. */
    public static function ensure(): void
    {
        $latest = max(array_keys(self::all()));
        $have = (int)(Db::value('SELECT MAX(version) FROM {migrations}') ?? 0);
        if ($have < $latest) self::run();
    }

    /** Applies every pending migration; returns the versions applied. */
    public static function run(): array
    {
        $done = [];
        Db::pdo()->exec(Db::sql('CREATE TABLE IF NOT EXISTS {migrations} (version INT PRIMARY KEY, applied_at DATETIME NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4'));
        $applied = array_map('intval', array_column(Db::all('SELECT version FROM {migrations}'), 'version'));
        foreach (self::all() as $version => $statements) {
            if (in_array($version, $applied, true)) continue;
            foreach ($statements as $sql) Db::pdo()->exec(Db::sql($sql));
            Db::insert('migrations', ['version' => $version, 'applied_at' => Clock::sql()]);
            $done[] = $version;
        }
        Seed::run();
        return $done;
    }
}

/** Default rows; inserted only when missing, so admin changes are never overwritten. */
final class Seed
{
    private static function hasColumn(string $table, string $col): bool
    {
        return Db::one("SHOW COLUMNS FROM {{$table}} LIKE " . Db::pdo()->quote($col)) !== null;
    }

    public static function run(): void
    {
        $now = Clock::sql();
        $plans = [
            ['m1', 'اشتراک یک‌ماهه', 'یک ماه Pro و بدون تبلیغ', 30, 19000, 1, null, null, 'divan_pro_1m'],
            ['m3', 'اشتراک سه‌ماهه', 'سه ماه Pro؛ حدود ۱۴٪ صرفه‌جویی نسبت به سه بار خرید یک‌ماهه', 90, 49000, 2, 'popular', 'محبوب‌ترین', 'divan_pro_3m'],
            ['m6', 'اشتراک شش‌ماهه', 'شش ماه Pro؛ حدود ۲۲٪ صرفه‌جویی نسبت به شش بار خرید یک‌ماهه', 180, 89000, 3, 'economic', 'اقتصادی', 'divan_pro_6m'],
            ['life', 'اشتراک مادام‌العمر', 'یک بار پرداخت، Pro و بدون تبلیغ برای همیشه', null, 220000, 4, 'special', 'پیشنهاد ویژه', 'divan_pro_lifetime'],
        ];
        foreach ($plans as [$code, $title, $desc, $days, $price, $sort, $badge, $badgeText, $sku]) {
            if (Db::value('SELECT id FROM {plans} WHERE code = ?', [$code]) !== null) continue;
            Db::insert('plans', [
                'code' => $code, 'title' => $title, 'description' => $desc, 'duration_days' => $days,
                'base_price_toman' => $price, 'sort' => $sort, 'active' => 1, 'badge' => $badge, 'badge_text' => $badgeText,
                'badge_color' => $badge === 'special' ? '#C9A24B' : ($badge === 'popular' ? '#2D63E6' : '#12A891'),
                'renewal_note' => $days === null ? 'بدون تمدید؛ دسترسی دائمی.' : 'بدون تمدید خودکار؛ برای ادامه، دوباره خرید کنید. خرید جدید به انتهای اشتراک فعلی اضافه می‌شود.',
                'sku_bazaar' => $sku, 'sku_myket' => $sku,
                'type_bazaar' => $days === null ? 'permanent' : 'consumable',
                'type_myket' => $days === null ? 'permanent' : 'consumable',
                'store_price_bazaar' => $price, 'store_price_myket' => $price, 'updated_at' => $now,
            ]);
        }
        $hasSection = self::hasColumn('features', 'section');
        foreach (Features::defaults() as $i => [$k, $title, $desc, $section]) {
            if (Db::value('SELECT k FROM {features} WHERE k = ?', [$k]) !== null) {
                // Order and grouping follow the app; the owner's mode, limit and items are never touched.
                Db::update('features', ['sort' => $i] + ($hasSection ? ['section' => $section] : []), 'k = ?', [$k]);
                continue;
            }
            Db::insert('features', ['k' => $k, 'title' => $title, 'description' => $desc, 'mode' => 'free', 'limit_value' => null, 'sort' => $i, 'updated_at' => $now] + ($hasSection ? ['section' => $section] : []));
        }
        $hasScreens = self::hasColumn('placements', 'screens');
        foreach (Ads::defaultPlacements() as $i => [$k, $title, $screen, $pos, $formats, $network]) {
            if (Db::value('SELECT k FROM {placements} WHERE k = ?', [$k]) !== null) continue;
            $sticky = $pos === 'sticky';
            Db::insert('placements', ['k' => $k, 'title' => $title, 'screen' => $screen, 'position' => $pos, 'formats' => $formats, 'network_formats' => $network, 'active' => $sticky ? 0 : 1, 'sort' => $i]
                + ($hasScreens ? ['screens' => $sticky ? implode(',', array_keys(Ads::ZONES)) : null] : []));
        }
        $defaults = [
            'offline_days' => 21,
            'renew_grace_days' => 3,
            'lifetime_offline_days' => 365,
            'cross_store' => true,
            'single_device' => true,
            'device_moves_limit' => 3,
            'pro_devices_limit' => 5,
            'ads_enabled' => false,
            'ads_priority' => 'direct_first',
            'ads_interstitial_min_minutes' => 10,
            'ads_daily_interstitial_cap' => 3,
            'tapsell' => ['enabled' => false, 'zones' => ['banner' => '', 'native' => '', 'interstitial' => '', 'rewarded' => ''], 'test' => true],
            'rewarded_ad_free_minutes' => 60,
            'bazaar' => ['enabled' => true, 'package' => 'com.meysam.divanemtiaz'],
            'myket' => ['enabled' => true, 'package' => 'com.meysam.divanemtiaz'],
            'support_text' => 'برای پیگیری خرید یا بازیابی حساب، درخواست پشتیبانی ثبت کنید.',
        ];
        foreach ($defaults as $k => $v) if (Settings::get($k) === null) Settings::set($k, $v);
    }
}
