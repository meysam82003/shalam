<?php
/**
 * Divan Emtiaz server — encrypted database backups and safe restore.
 *
 * A backup holds every table, the installation key and the licence signing keys, gzip-compressed and
 * encrypted (AES-256-GCM) with the backup key shown at install time. Restoring never overwrites rows
 * that already exist, so purchases made after the backup are kept; a fresh installation can adopt
 * the whole backup (including the keys, so certificates already on phones stay valid).
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

final class Backup
{
    public const TABLES = [
        'settings', 'users', 'sessions', 'plans', 'campaigns', 'purchase_intents', 'purchases', 'entitlements', 'features',
        'user_grants', 'placements', 'media', 'ads', 'ad_stats', 'revenue', 'support_requests', 'support_actions', 'admins',
        'audit_log', 'security_events', 'user_backups', 'promo_codes', 'promo_redemptions',
    ];
    /** Natural keys used to recognise a row that already exists. */
    private const KEYS = [
        'settings' => ['k'], 'users' => ['username_norm'], 'sessions' => ['token_hash'], 'plans' => ['code'], 'purchases' => ['store', 'token_hash'],
        'entitlements' => ['id'], 'features' => ['k'], 'placements' => ['k'], 'admins' => ['username'], 'ad_stats' => ['day', 'ad_id', 'source', 'placement'],
        'purchase_intents' => ['payload'], 'user_backups' => ['user_id'], 'promo_codes' => ['code'], 'promo_redemptions' => ['code_id', 'user_id'],
    ];

    public static function dir(): string { return App::$root . '/storage/backups'; }

    private static function key(?string $override = null): string
    {
        $k = base64_decode($override !== null && trim($override) !== '' ? trim($override) : (string)App::cfg('backup_key', ''), true);
        if ($k === false || strlen($k) < 32) throw new RuntimeException('backup_key missing');
        return $k;
    }

    public static function create(string $kind = 'manual', string $note = ''): array
    {
        $data = ['format' => 'divan-backup', 'version' => 1, 'created_at' => Clock::now(), 'prefix' => Db::$prefix, 'tables' => []];
        foreach (self::TABLES as $t) $data['tables'][$t] = Db::all("SELECT * FROM {{$t}}");
        $data['secrets'] = [
            'app_key' => App::cfg('app_key'),
            'license_private' => @file_get_contents(App::$root . '/storage/keys/license-private.pem') ?: '',
            'license_public' => @file_get_contents(App::$root . '/storage/keys/license-public.pem') ?: '',
        ];
        $plain = gzencode(json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 6);
        $iv = random_bytes(12);
        $tag = '';
        $ct = openssl_encrypt($plain, 'aes-256-gcm', self::key(), OPENSSL_RAW_DATA, $iv, $tag);
        $blob = "DIVANBK1" . $iv . $tag . $ct;
        if (!is_dir(self::dir())) mkdir(self::dir(), 0700, true);
        $file = 'divan-' . gmdate('Ymd-His', Clock::now()) . '-' . bin2hex(random_bytes(4)) . '.dvbk';
        if (file_put_contents(self::dir() . '/' . $file, $blob) === false) throw new RuntimeException('backup write failed');
        @chmod(self::dir() . '/' . $file, 0600);
        $id = Db::insert('backups', ['file' => $file, 'size' => strlen($blob), 'sha256' => hash('sha256', $blob), 'kind' => $kind, 'status' => 'ok', 'note' => $note ?: null, 'created_at' => Clock::sql()]);
        Settings::set('last_backup_at', Clock::now());
        return ['id' => $id, 'file' => $file, 'size' => strlen($blob)];
    }

    public static function decode(string $blob, ?string $key = null): array
    {
        if (substr($blob, 0, 8) !== 'DIVANBK1' || strlen($blob) < 37) throw new ApiError('bad_backup', 'فایل پشتیبان دیوان نیست.');
        $pt = openssl_decrypt(substr($blob, 36), 'aes-256-gcm', self::key($key), OPENSSL_RAW_DATA, substr($blob, 8, 12), substr($blob, 20, 16));
        if ($pt === false) throw new ApiError('bad_backup', 'رمزگشایی پشتیبان ممکن نشد؛ کلید پشتیبان این نصب با فایل یکی نیست.');
        $data = json_decode((string)gzdecode($pt), true);
        if (!is_array($data) || ($data['format'] ?? '') !== 'divan-backup') throw new ApiError('bad_backup', 'محتوای پشتیبان معتبر نیست.');
        return $data;
    }

    /**
     * Restores a backup without overwriting anything: rows whose natural key already exists are kept as
     * they are (so newer purchases and edits win). Returns the number of rows added per table.
     */
    public static function restore(string $blob, bool $adoptKeys = false, ?string $key = null): array
    {
        $data = self::decode($blob, $key);
        $added = [];
        $fresh = (int)Db::value('SELECT COUNT(*) FROM {users}') === 0 && (int)Db::value('SELECT COUNT(*) FROM {purchases}') === 0;
        if ($adoptKeys && !$fresh) throw new ApiError('not_fresh', 'انتقال کلیدها فقط روی نصب تازهٔ بدون کاربر و خرید ممکن است.');
        Db::tx(function () use ($data, &$added, $fresh) {
            foreach (self::TABLES as $t) {
                $rows = $data['tables'][$t] ?? [];
                $n = 0;
                $cols = array_column(Db::all("SHOW COLUMNS FROM {{$t}}"), 'Field');
                foreach ($rows as $row) {
                    $row = array_intersect_key($row, array_flip($cols));
                    if (!$row) continue;
                    if (self::exists($t, $row, $fresh)) continue;
                    if (!$fresh && isset($row['id']) && in_array($t, ['users', 'purchases', 'entitlements', 'admins', 'support_requests'], true)
                        && Db::value("SELECT id FROM {{$t}} WHERE id = ?", [$row['id']]) !== null) {
                        // Same id already used by a newer row: such a conflict is reported, never overwritten.
                        $added["$t:conflicts"] = ($added["$t:conflicts"] ?? 0) + 1;
                        continue;
                    }
                    Db::insert($t, $row);
                    $n++;
                }
                $added[$t] = $n;
            }
        });
        if ($adoptKeys && $fresh) {
            $s = $data['secrets'] ?? [];
            if (!empty($s['license_private']) && !empty($s['license_public'])) {
                file_put_contents(App::$root . '/storage/keys/license-private.pem', $s['license_private']);
                file_put_contents(App::$root . '/storage/keys/license-public.pem', $s['license_public']);
            }
            if (!empty($s['app_key'])) {
                $cfg = App::$config;
                $cfg['app_key'] = $s['app_key'];
                Installer::writeConfig($cfg);
            }
            $added['keys_adopted'] = 1;
        }
        Audit::log('system', null, 'backup_restored', 'backup', null, 'rows added', null, $added);
        return $added;
    }

    private static function exists(string $t, array $row, bool $fresh): bool
    {
        $key = self::KEYS[$t] ?? (isset($row['id']) ? ['id'] : null);
        if ($key === null) return false;
        $where = [];
        $params = [];
        foreach ($key as $c) {
            if (!array_key_exists($c, $row)) return false;
            $where[] = "`$c` = ?";
            $params[] = $row[$c];
        }
        return Db::value("SELECT 1 FROM {{$t}} WHERE " . implode(' AND ', $where) . ' LIMIT 1', $params) !== null;
    }

    public static function list(): array { return Db::all('SELECT * FROM {backups} ORDER BY id DESC LIMIT 60'); }

    public static function prune(int $keep = 14): void
    {
        $old = Db::all("SELECT * FROM {backups} WHERE kind = 'auto' ORDER BY id DESC LIMIT 1000 OFFSET " . (int)$keep);
        foreach ($old as $b) {
            @unlink(self::dir() . '/' . basename($b['file']));
            Db::run('DELETE FROM {backups} WHERE id = ?', [$b['id']]);
        }
    }

    /** Daily housekeeping (cron or "poor man's cron" from admin visits). */
    public static function daily(): array
    {
        $out = ['rechecked' => Purchases::recheck(50)];
        RateLimit::prune();
        $last = (int)Settings::get('last_backup_at', 0);
        if ($last < Clock::now() - 20 * 3600) {
            try {
                $out['backup'] = self::create('auto');
                self::prune();
                Settings::set('last_backup_error', '');
            } catch (Throwable $e) {
                Settings::set('last_backup_error', $e->getMessage());
                $out['backup_error'] = $e->getMessage();
            }
        }
        return $out;
    }
}
