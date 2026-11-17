<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }

/** A registration trial is tied to both account and device, never to IP or a client clock. */
final class MafiaTrial
{
    public static function register(array $in): array
    {
        return Db::tx(function () use ($in) {
            $device = Devices::clean($in['trial_device_id'] ?? null);
            if ($device !== null && Devices::clean($in['device_id'] ?? null) === null) throw new ApiError('no_device', 'شناسهٔ دستگاه دریافت نشد.');
            // Older clients may still register, but cannot start a trial without the stable identifier.
            if ($device !== null && Db::one('SELECT device FROM {mafia_trials} WHERE device = ?', [$device]) !== null)
                throw new ApiError('trial_used', 'یک بار با این گوشی قابلیت مافیا را تست کرده‌اید؛ وارد حساب قبلی شوید و اشتراک Pro بخرید.');
            [$u, $key] = Users::register((string)($in['username'] ?? ''), (string)($in['password'] ?? ''), isset($in['email']) ? (string)$in['email'] : null);
            if ($device !== null) {
                try {
                    Db::insert('mafia_trials', ['device' => $device, 'user_id' => $u['id'], 'session_device' => Devices::clean($in['device_id'] ?? null), 'starts_at' => Clock::sql(), 'ends_at' => Clock::sql(Clock::now() + 600)]);
                } catch (PDOException $e) {
                    if ($e->getCode() !== '23000') throw $e;
                    throw new ApiError('trial_used', 'یک بار با این گوشی قابلیت مافیا را تست کرده‌اید؛ اشتراک Pro بخرید.');
                }
            }
            return [$u, $key];
        });
    }

    public static function until(int $uid, ?string $device): int
    {
        if ($device === null) return 0;
        return Clock::ts(Db::value('SELECT ends_at FROM {mafia_trials} WHERE user_id = ? AND session_device = ?', [$uid, $device])) ?? 0;
    }
}

/** Single-use watch sessions; grants and the rolling 24h cooldown are committed atomically on the server. */
final class ProRewards
{
    public static function status(int $uid, ?string $device): array
    {
        $r = $device === null ? null : Db::one('SELECT * FROM {reward_devices} WHERE device = ?', [$device]);
        $last = max((int)($r['claimed_at'] ?? 0), (int)(Db::value('SELECT MAX(claimed_at) FROM {reward_devices} WHERE user_id = ?', [$uid]) ?? 0));
        return ['next_at' => $last ? $last + 86400 : 0];
    }

    public static function start(array $u, array $session, int $adId): array
    {
        $device = Devices::clean($session['device_id'] ?? null);
        if ($device === null) throw new ApiError('no_device', 'شناسهٔ دستگاه دریافت نشد.');
        if (self::status((int)$u['id'], $device)['next_at'] > Clock::now()) throw new ApiError('reward_cooldown', 'هر ۲۴ ساعت فقط یک جایزه می‌توانید بگیرید.');
        $cfg = Ads::configFor($session['store'] ?? 'bazaar', false);
        if (empty($cfg['enabled'])) throw new ApiError('no_ad', 'ویدئویی برای جایزه موجود نیست.');
        $placement = array_values(array_filter($cfg['placements'] ?? [], fn($p) => $p['key'] === 'rewarded_offer' && $p['active']));
        if (!$placement) throw new ApiError('no_ad', 'ویدئوی جایزه‌ای غیرفعال است.');
        $ad = null;
        foreach ($cfg['ads'] ?? [] as $candidate) if ($candidate['id'] === $adId && $candidate['format'] === 'rewarded' && ($candidate['media']['kind'] ?? '') === 'video' && in_array('rewarded_offer', $candidate['placements'], true)) $ad = $candidate;
        if ($adId === 0 && !empty($cfg['network']['enabled']) && !empty($cfg['network']['zones']['rewarded'])) $ad = ['reward_seconds' => 1];
        if ($ad === null) throw new ApiError('no_ad', 'ویدئوی جایزه‌ای معتبر موجود نیست.');
        $token = bin2hex(random_bytes(32));
        Db::insert('reward_watches', ['token_hash' => Crypto::hash($token), 'user_id' => $u['id'], 'device' => $device, 'ad_id' => $adId, 'started_at' => Clock::now(), 'min_seconds' => max(1, (int)($ad['reward_seconds'] ?? 30)), 'claimed_at' => null]);
        return ['watch_token' => $token];
    }

    public static function claim(array $u, array $session, string $token): void
    {
        Db::tx(function () use ($u, $session, $token) {
            // Account and device locks serialize concurrent claims, including claims from different accounts.
            Db::one('SELECT id FROM {users} WHERE id = ? FOR UPDATE', [$u['id']]);
            $device = Devices::clean($session['device_id'] ?? null);
            if ($device === null) throw new ApiError('no_device', 'شناسهٔ دستگاه دریافت نشد.');
            Db::run('INSERT IGNORE INTO {reward_devices} (device, user_id, claimed_at) VALUES (?, ?, 0)', [$device, $u['id']]);
            Db::one('SELECT device FROM {reward_devices} WHERE device = ? FOR UPDATE', [$device]);
            $w = Db::one('SELECT * FROM {reward_watches} WHERE token_hash = ? AND user_id = ? AND device = ? FOR UPDATE', [Crypto::hash($token), $u['id'], $device]);
            if ($w === null || $w['claimed_at'] !== null || Clock::now() < (int)$w['started_at'] + (int)$w['min_seconds'] || Clock::now() > (int)$w['started_at'] + 3600)
                throw new ApiError('incomplete_watch', 'ویدئو کامل نشده یا درخواست منقضی شده است.');
            if (self::status((int)$u['id'], $device)['next_at'] > Clock::now()) throw new ApiError('reward_cooldown', 'هر ۲۴ ساعت فقط یک جایزه می‌توانید بگیرید.');
            Db::update('reward_watches', ['claimed_at' => Clock::now()], 'token_hash = ?', [Crypto::hash($token)]);
            Db::update('reward_devices', ['user_id' => $u['id'], 'claimed_at' => Clock::now()], 'device = ?', [$device]);
            Db::insert('user_grants', ['user_id' => $u['id'], 'feature_k' => 'pro', 'expires_at' => Clock::sql(Clock::now() + 3600), 'reason' => 'ویدئوی جایزه‌ای: یک ساعت Pro', 'created_at' => Clock::sql()]);
        });
    }
}
