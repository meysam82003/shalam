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
            // Registration is always available; only the one-time trial is reserved per phone.
            [$u, $key] = Users::register((string)($in['username'] ?? ''), (string)($in['password'] ?? ''), isset($in['email']) ? (string)$in['email'] : null);
            if ($device !== null) {
                // Concurrent registrations serialize on the device PK. Keep the first account/window untouched;
                // a second account is created normally and gets no fresh trial. Reward cooldown is separate.
                Db::run('INSERT INTO {mafia_trials} (device,user_id,session_device,starts_at,ends_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE device=device',
                    [$device,$u['id'],Devices::clean($in['device_id'] ?? null),Clock::sql(),Clock::sql(Clock::now()+600)]);
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

    public static function start(array $u, array $session, int $adId, ?string $network = null): array
    {
        $device = Devices::clean($session['device_id'] ?? null);
        if ($device === null) throw new ApiError('no_device', 'شناسهٔ دستگاه دریافت نشد.');
        if (self::status((int)$u['id'], $device)['next_at'] > Clock::now()) throw new ApiError('reward_cooldown', 'هر ۲۴ ساعت فقط یک جایزه می‌توانید بگیرید.');
        $cfg = Ads::configFor($session['store'] ?? 'bazaar', false);
        if (empty($cfg['enabled'])) throw new ApiError('no_ad', 'ویدئویی برای جایزه موجود نیست.');
        $placement = array_values(array_filter($cfg['placements'] ?? [], fn($p) => $p['key'] === 'rewarded_offer' && $p['active']));
        $iran = Monetization::publicConfig(false);
        $usingIran = $adId === 0 && $network !== null && in_array($network, [$iran['primary'], $iran['fallback']], true) && !empty($iran['enabled']) && !empty($iran['rewarded']['enabled']);
        if ($network !== null && !$usingIran) throw new ApiError('no_ad', 'شبکهٔ جایزه‌ای غیرفعال است.');
        if (!$placement && !$usingIran) throw new ApiError('no_ad', 'ویدئوی جایزه‌ای غیرفعال است.');
        $ad = null;
        foreach ($cfg['ads'] ?? [] as $candidate) if ($candidate['id'] === $adId && $candidate['format'] === 'rewarded' && ($candidate['media']['kind'] ?? '') === 'video' && in_array('rewarded_offer', $candidate['placements'], true)) $ad = $candidate;
        $isTest = false;
        if ($adId === 0 && $network === null && !empty($cfg['network']['enabled']) && !empty($cfg['network']['zones']['rewarded'])) { $ad = ['reward_seconds' => 10]; $isTest = !empty($cfg['network']['test']); }
        if ($usingIran) {
            $n = $iran['networks'][$network] ?? [];
            if (!empty($n['enabled']) && !empty($n['app_id']) && !empty($n['zones']['rewarded'])) { $ad = ['reward_seconds' => $iran['rewarded']['min_seconds']]; $isTest = !empty($n['test']); }
        }

        if ($ad === null) throw new ApiError('no_ad', 'ویدئوی جایزه‌ای معتبر موجود نیست.');
        $token = bin2hex(random_bytes(32));
        Db::insert('reward_watches', ['token_hash' => Crypto::hash($token), 'user_id' => $u['id'], 'device' => $device, 'ad_id' => $adId, 'started_at' => Clock::now(), 'min_seconds' => max(1, (int)($ad['reward_seconds'] ?? 30)), 'is_test' => $isTest ? 1 : 0, 'claimed_at' => null]);
        return ['watch_token' => $token, 'test_mode' => $isTest, 'claim_after' => Clock::now() + max(1, (int)($ad['reward_seconds'] ?? 30)), 'expires_at' => Clock::now() + 3600];
    }

    public static function claim(array $u, array $session, string $token): bool
    {
        return Db::tx(function () use ($u, $session, $token) {
            // Account and device locks serialize concurrent claims, including claims from different accounts.
            Db::one('SELECT id FROM {users} WHERE id = ? FOR UPDATE', [$u['id']]);
            $device = Devices::clean($session['device_id'] ?? null);
            if ($device === null) throw new ApiError('no_device', 'شناسهٔ دستگاه دریافت نشد.');
            Db::run('INSERT IGNORE INTO {reward_devices} (device, user_id, claimed_at) VALUES (?, ?, 0)', [$device, $u['id']]);
            Db::one('SELECT device FROM {reward_devices} WHERE device = ? FOR UPDATE', [$device]);
            $w = Db::one('SELECT * FROM {reward_watches} WHERE token_hash = ? AND user_id = ? AND device = ? FOR UPDATE', [Crypto::hash($token), $u['id'], $device]);
            if ($w === null || Clock::now() < (int)$w['started_at'] + (int)$w['min_seconds'] || Clock::now() > (int)$w['started_at'] + 3600)
                throw new ApiError('incomplete_watch', 'ویدئو کامل نشده یا درخواست منقضی شده است.');
            if ($w['claimed_at'] !== null) return (int)$w['is_test'] === 1;
            if (self::status((int)$u['id'], $device)['next_at'] > Clock::now()) throw new ApiError('reward_cooldown', 'هر ۲۴ ساعت فقط یک جایزه می‌توانید بگیرید.');
            Db::update('reward_watches', ['claimed_at' => Clock::now()], 'token_hash = ?', [Crypto::hash($token)]);
            if ((int)$w['is_test'] === 1) return true;
            Db::update('reward_devices', ['user_id' => $u['id'], 'claimed_at' => Clock::now()], 'device = ?', [$device]);
            Db::insert('user_grants', ['user_id' => $u['id'], 'feature_k' => 'pro', 'expires_at' => Clock::sql(Clock::now() + 3600), 'reason' => 'ویدئوی جایزه‌ای: یک ساعت Pro', 'created_at' => Clock::sql()]);
            return false;
        });
    }
}
