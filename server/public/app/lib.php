<?php
/**
 * Divan Emtiaz server — core library: configuration, database, crypto, HTTP helpers,
 * rate limiting, settings, audit trail and TOTP. Plain PHP 7.4+ with PDO/MySQL, no Composer.
 */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

// ---------------------------------------------------------------- polyfills (PHP 7.4)
if (!function_exists('str_contains')) {
    function str_contains(string $h, string $n): bool { return $n === '' || strpos($h, $n) !== false; }
}
if (!function_exists('str_starts_with')) {
    function str_starts_with(string $h, string $n): bool { return strncmp($h, $n, strlen($n)) === 0; }
}
if (!function_exists('str_ends_with')) {
    function str_ends_with(string $h, string $n): bool { return $n === '' || substr($h, -strlen($n)) === $n; }
}

final class App
{
    /** @var array<string,mixed> */
    public static array $config = [];
    public static string $root = '';

    public static function configFile(): string { return self::$root . '/storage/config.php'; }
    public static function installed(): bool { return is_file(self::$root . '/storage/install.lock') && is_file(self::configFile()); }

    public static function boot(string $root): void
    {
        self::$root = $root;
        date_default_timezone_set('UTC');
        if (is_file(self::configFile())) {
            /** @noinspection PhpIncludeInspection */
            self::$config = require self::configFile();
        }
    }

    /** @return mixed */
    public static function cfg(string $key, $default = null)
    {
        return self::$config[$key] ?? $default;
    }

    public static function baseUrl(): string
    {
        $u = (string)self::cfg('base_url', '');
        if ($u !== '') return rtrim($u, '/');
        $https = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
        $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
        $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
        foreach (['/api', '/admin', '/install'] as $sub) {
            if (str_ends_with($dir, $sub)) $dir = substr($dir, 0, -strlen($sub));
        }
        return ($https ? 'https' : 'http') . '://' . $host . $dir;
    }
}

// ---------------------------------------------------------------- time
final class Clock
{
    /** Tests can freeze or move the server clock. */
    public static ?int $fixed = null;
    public static function now(): int { return self::$fixed ?? time(); }
    public static function sql(?int $ts = null): string { return gmdate('Y-m-d H:i:s', $ts ?? self::now()); }
    public static function ts(?string $sql): ?int { return $sql === null || $sql === '' ? null : (int)strtotime($sql . ' UTC'); }
}

// ---------------------------------------------------------------- database
final class Db
{
    private static ?PDO $pdo = null;
    public static string $prefix = 'dv_';

    public static function connect(array $c): PDO
    {
        $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4', $c['db_host'], (int)($c['db_port'] ?? 3306), $c['db_name']);
        $pdo = new PDO($dsn, (string)$c['db_user'], (string)$c['db_pass'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
        $pdo->exec("SET time_zone = '+00:00'");
        return $pdo;
    }

    public static function pdo(): PDO
    {
        if (self::$pdo === null) {
            self::$prefix = (string)App::cfg('db_prefix', 'dv_');
            self::$pdo = self::connect(App::$config);
        }
        return self::$pdo;
    }

    public static function use(PDO $pdo, string $prefix): void { self::$pdo = $pdo; self::$prefix = $prefix; }

    /** Table name with the installation prefix. */
    public static function t(string $name): string { return '`' . self::$prefix . $name . '`'; }

    /** Replaces {name} placeholders with prefixed table names. */
    public static function sql(string $sql): string
    {
        return preg_replace_callback('/\{([a-z_]+)\}/', fn($m) => self::t($m[1]), $sql);
    }

    public static function run(string $sql, array $params = []): PDOStatement
    {
        $st = self::pdo()->prepare(self::sql($sql));
        $st->execute($params);
        return $st;
    }

    public static function one(string $sql, array $params = []): ?array
    {
        $r = self::run($sql, $params)->fetch();
        return $r === false ? null : $r;
    }

    public static function all(string $sql, array $params = []): array { return self::run($sql, $params)->fetchAll(); }

    /** @return mixed */
    public static function value(string $sql, array $params = [])
    {
        $v = self::run($sql, $params)->fetchColumn();
        return $v === false ? null : $v;
    }

    public static function insert(string $table, array $row): int
    {
        $cols = array_keys($row);
        $sql = 'INSERT INTO ' . self::t($table) . ' (`' . implode('`,`', $cols) . '`) VALUES (' . implode(',', array_fill(0, count($cols), '?')) . ')';
        $st = self::pdo()->prepare($sql);
        $st->execute(array_values($row));
        return (int)self::pdo()->lastInsertId();
    }

    public static function update(string $table, array $row, string $where, array $params = []): int
    {
        $set = implode(',', array_map(fn($c) => "`$c` = ?", array_keys($row)));
        $st = self::pdo()->prepare('UPDATE ' . self::t($table) . " SET $set WHERE " . self::sql($where));
        $st->execute(array_merge(array_values($row), $params));
        return $st->rowCount();
    }

    /** @return mixed */
    public static function tx(callable $fn)
    {
        $pdo = self::pdo();
        if ($pdo->inTransaction()) return $fn();
        $pdo->beginTransaction();
        try {
            $r = $fn();
            $pdo->commit();
            return $r;
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
    }
}

// ---------------------------------------------------------------- crypto
final class Crypto
{
    public static function key(): string
    {
        $k = base64_decode((string)App::cfg('app_key', ''), true);
        if ($k === false || strlen($k) < 32) throw new RuntimeException('app_key missing');
        return $k;
    }

    public static function random(int $bytes = 32): string { return random_bytes($bytes); }
    public static function token(int $bytes = 32): string { return self::b64u(random_bytes($bytes)); }
    public static function b64u(string $raw): string { return rtrim(strtr(base64_encode($raw), '+/', '-_'), '='); }
    public static function b64uDecode(string $s): string
    {
        $r = base64_decode(strtr($s, '-_', '+/') . str_repeat('=', (4 - strlen($s) % 4) % 4), true);
        return $r === false ? '' : $r;
    }

    /** Keyed hash for lookups of secrets (tokens, purchase tokens) that must not be stored in clear. */
    public static function hash(string $value): string { return hash_hmac('sha256', $value, self::key()); }

    /** AES-256-GCM: base64(iv | tag | ciphertext). */
    public static function encrypt(string $plain): string
    {
        $iv = random_bytes(12);
        $tag = '';
        $ct = openssl_encrypt($plain, 'aes-256-gcm', self::key(), OPENSSL_RAW_DATA, $iv, $tag);
        if ($ct === false) throw new RuntimeException('encrypt failed');
        return base64_encode($iv . $tag . $ct);
    }

    public static function decrypt(?string $box): ?string
    {
        if ($box === null || $box === '') return null;
        $raw = base64_decode($box, true);
        if ($raw === false || strlen($raw) < 29) return null;
        $pt = openssl_decrypt(substr($raw, 28), 'aes-256-gcm', self::key(), OPENSSL_RAW_DATA, substr($raw, 0, 12), substr($raw, 12, 16));
        return $pt === false ? null : $pt;
    }

    /** Human-friendly recovery key: 160 random bits as 8 groups of 4 base32 characters. */
    public static function recoveryKey(): string
    {
        $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        $bytes = random_bytes(20);
        $bits = '';
        foreach (str_split($bytes) as $b) $bits .= str_pad(decbin(ord($b)), 8, '0', STR_PAD_LEFT);
        $out = '';
        for ($i = 0; $i + 5 <= strlen($bits); $i += 5) $out .= $alphabet[bindec(substr($bits, $i, 5))];
        return implode('-', str_split($out, 4));
    }

    public static function normalizeRecovery(string $k): string { return strtoupper(preg_replace('/[^A-Za-z0-9]/', '', $k)); }

    public static function passwordHash(string $p): string
    {
        $algo = defined('PASSWORD_ARGON2ID') ? PASSWORD_ARGON2ID : PASSWORD_DEFAULT;
        return password_hash($p, $algo);
    }

    // ---- entitlement certificate signing (RSA-2048, SHA-256)
    public static function privateKey()
    {
        $pem = @file_get_contents(App::$root . '/storage/keys/license-private.pem');
        if ($pem === false) throw new RuntimeException('signing key missing');
        return openssl_pkey_get_private($pem);
    }

    public static function publicKeyPem(): string
    {
        $pem = @file_get_contents(App::$root . '/storage/keys/license-public.pem');
        if ($pem === false) throw new RuntimeException('public key missing');
        return $pem;
    }

    /** Key id: first 16 hex characters of the SHA-256 of the public key's DER. */
    public static function keyId(): string
    {
        return substr(hash('sha256', self::pemToDer(self::publicKeyPem())), 0, 16);
    }

    public static function pemToDer(string $pem): string
    {
        return base64_decode(preg_replace('/-----[A-Z ]+-----|\s+/', '', $pem)) ?: '';
    }

    public static function sign(string $data): string
    {
        $sig = '';
        if (!openssl_sign($data, $sig, self::privateKey(), OPENSSL_ALGO_SHA256)) throw new RuntimeException('sign failed');
        return $sig;
    }

    /** Creates the RSA key pair of the installation (used once by the installer). */
    public static function generateKeys(string $dir): void
    {
        $res = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
        if ($res === false) throw new RuntimeException('openssl_pkey_new failed: ' . openssl_error_string());
        $priv = '';
        openssl_pkey_export($res, $priv);
        $pub = openssl_pkey_get_details($res)['key'];
        file_put_contents($dir . '/license-private.pem', $priv);
        file_put_contents($dir . '/license-public.pem', $pub);
        @chmod($dir . '/license-private.pem', 0600);
    }
}

// ---------------------------------------------------------------- TOTP (RFC 6238)
final class Totp
{
    private const B32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

    public static function secret(): string
    {
        $raw = random_bytes(20);
        $bits = '';
        foreach (str_split($raw) as $b) $bits .= str_pad(decbin(ord($b)), 8, '0', STR_PAD_LEFT);
        $out = '';
        foreach (str_split($bits, 5) as $chunk) $out .= self::B32[bindec(str_pad($chunk, 5, '0'))];
        return $out;
    }

    public static function decode(string $b32): string
    {
        $b32 = strtoupper(preg_replace('/[^A-Za-z2-7]/', '', $b32));
        $bits = '';
        foreach (str_split($b32) as $c) $bits .= str_pad(decbin(strpos(self::B32, $c)), 5, '0', STR_PAD_LEFT);
        $out = '';
        foreach (str_split($bits, 8) as $byte) if (strlen($byte) === 8) $out .= chr(bindec($byte));
        return $out;
    }

    public static function code(string $secret, int $step): string
    {
        $bin = pack('N*', 0) . pack('N*', $step);
        $h = hash_hmac('sha1', $bin, self::decode($secret), true);
        $o = ord($h[19]) & 0xf;
        $v = ((ord($h[$o]) & 0x7f) << 24) | (ord($h[$o + 1]) << 16) | (ord($h[$o + 2]) << 8) | ord($h[$o + 3]);
        return str_pad((string)($v % 1000000), 6, '0', STR_PAD_LEFT);
    }

    /** Returns the matching time step (to block reuse) or null. */
    public static function verify(string $secret, string $code, int $lastStep = 0): ?int
    {
        $code = preg_replace('/\D/', '', $code);
        if (strlen($code) !== 6) return null;
        $now = intdiv(Clock::now(), 30);
        for ($d = -1; $d <= 1; $d++) {
            $s = $now + $d;
            if ($s <= $lastStep) continue;
            if (hash_equals(self::code($secret, $s), $code)) return $s;
        }
        return null;
    }

    public static function uri(string $secret, string $account, string $issuer = 'Divan Emtiaz'): string
    {
        return 'otpauth://totp/' . rawurlencode($issuer . ':' . $account) . '?secret=' . $secret . '&issuer=' . rawurlencode($issuer) . '&digits=6&period=30';
    }
}

// ---------------------------------------------------------------- settings (key/value)
final class Settings
{
    private static array $cache = [];

    /** @return mixed */
    public static function get(string $key, $default = null)
    {
        if (!array_key_exists($key, self::$cache)) {
            $v = Db::value('SELECT v FROM {settings} WHERE k = ?', [$key]);
            self::$cache[$key] = $v === null ? null : json_decode((string)$v, true);
        }
        return self::$cache[$key] ?? $default;
    }

    /** @param mixed $value */
    public static function set(string $key, $value): void
    {
        $json = json_encode($value, JSON_UNESCAPED_UNICODE);
        Db::run('INSERT INTO {settings} (k, v, updated_at) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v), updated_at = VALUES(updated_at)', [$key, $json, Clock::sql()]);
        self::$cache[$key] = $value;
    }

    /** Secret settings (market tokens) are stored encrypted. */
    public static function secret(string $key): string
    {
        $box = self::get($key);
        return is_string($box) ? (string)Crypto::decrypt($box) : '';
    }

    public static function setSecret(string $key, string $value): void { self::set($key, $value === '' ? '' : Crypto::encrypt($value)); }

    public static function reset(): void { self::$cache = []; }
}

// ---------------------------------------------------------------- rate limiting
final class RateLimit
{
    /** True when the caller may continue; counts the hit. */
    public static function hit(string $key, int $max, int $windowSec): bool
    {
        $now = Clock::now();
        $k = substr(hash('sha256', $key), 0, 64);
        $row = Db::one('SELECT window_start, hits FROM {rate_limits} WHERE k = ?', [$k]);
        if ($row === null || (int)$row['window_start'] + $windowSec <= $now) {
            Db::run('REPLACE INTO {rate_limits} (k, window_start, hits) VALUES (?, ?, 1)', [$k, $now]);
            return true;
        }
        if ((int)$row['hits'] >= $max) return false;
        Db::run('UPDATE {rate_limits} SET hits = hits + 1 WHERE k = ?', [$k]);
        return true;
    }

    public static function clear(string $key): void { Db::run('DELETE FROM {rate_limits} WHERE k = ?', [substr(hash('sha256', $key), 0, 64)]); }

    public static function prune(): void { Db::run('DELETE FROM {rate_limits} WHERE window_start < ?', [Clock::now() - 86400 * 2]); }
}

// ---------------------------------------------------------------- audit and security log
final class Audit
{
    public static function log(string $actorType, ?int $actorId, string $action, string $entity = '', $entityId = null, string $reason = '', $before = null, $after = null): void
    {
        Db::insert('audit_log', [
            'actor_type' => $actorType,
            'actor_id' => $actorId,
            'action' => $action,
            'entity' => $entity,
            'entity_id' => $entityId === null ? null : (string)$entityId,
            'reason' => mb_substr($reason, 0, 500),
            'before_json' => $before === null ? null : json_encode($before, JSON_UNESCAPED_UNICODE),
            'after_json' => $after === null ? null : json_encode($after, JSON_UNESCAPED_UNICODE),
            'ip' => Http::ip(),
            'created_at' => Clock::sql(),
        ]);
    }

    public static function security(string $kind, ?int $userId, string $username = '', string $details = ''): void
    {
        Db::insert('security_events', [
            'kind' => $kind, 'user_id' => $userId, 'username' => mb_substr($username, 0, 64),
            'ip' => Http::ip(), 'details' => mb_substr($details, 0, 1000), 'created_at' => Clock::sql(),
        ]);
    }
}

// ---------------------------------------------------------------- HTTP helpers
final class Http
{
    public static function ip(): string
    {
        return substr((string)($_SERVER['REMOTE_ADDR'] ?? 'cli'), 0, 45);
    }

    public static function json(array $data, int $status = 200): void
    {
        http_response_code($status);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');
        header('X-Content-Type-Options: nosniff');
        echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    }

    /** Request body set by the automated tests instead of php://input. */
    public static ?array $testBody = null;

    public static function body(): array
    {
        if (self::$testBody !== null) return self::$testBody;
        $raw = file_get_contents('php://input') ?: '';
        if (strlen($raw) > 2 * 1024 * 1024 + 200000) throw new ApiError('too_large', 'درخواست بیش از اندازه بزرگ است.', 413);
        $j = json_decode($raw, true);
        return is_array($j) ? $j : $_POST;
    }

    public static function header(string $name): string
    {
        $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
        if (isset($_SERVER[$key])) return (string)$_SERVER[$key];
        if ($name === 'Authorization' && isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) return (string)$_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
        if (function_exists('getallheaders')) {
            foreach (getallheaders() as $k => $v) if (strcasecmp($k, $name) === 0) return (string)$v;
        }
        return '';
    }

    /** Only the automated tests may talk plain HTTP to a local fake gateway (127.0.0.1). */
    public static bool $allowLocalHttpForTests = false;

    /** Outbound HTTPS request (market verification). Never follows redirects. Response headers come back lower-cased. */
    public static function request(string $method, string $url, array $headers = [], ?string $body = null, int $timeout = 15): array
    {
        $local = self::$allowLocalHttpForTests && preg_match('#^http://127\.0\.0\.1:\d+/#', $url);
        if (!$local && !preg_match('#^https://#i', $url)) return ['status' => 0, 'body' => '', 'error' => 'https only', 'headers' => []];
        if (!function_exists('curl_init')) return ['status' => 0, 'body' => '', 'error' => 'curl extension missing', 'headers' => []];
        $ch = curl_init($url);
        $respHeaders = [];
        curl_setopt_array($ch, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_TIMEOUT => $timeout,
            CURLOPT_CONNECTTIMEOUT => min(8, $timeout),
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_PROTOCOLS => $local ? CURLPROTO_HTTP : CURLPROTO_HTTPS,
            CURLOPT_HEADERFUNCTION => function ($c, string $line) use (&$respHeaders) {
                $p = strpos($line, ':');
                if ($p !== false) $respHeaders[strtolower(trim(substr($line, 0, $p)))] = trim(substr($line, $p + 1));
                return strlen($line);
            },
        ]);
        if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        $resp = curl_exec($ch);
        $err = $resp === false ? curl_error($ch) : '';
        $status = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        curl_close($ch);
        return ['status' => $resp === false ? 0 : $status, 'body' => $resp === false ? '' : (string)$resp, 'error' => $err, 'headers' => $respHeaders];
    }
}

final class ApiError extends RuntimeException
{
    public string $errorCode;
    public int $status;
    public array $extra;

    public function __construct(string $code, string $message, int $status = 400, array $extra = [])
    {
        parent::__construct($message);
        $this->errorCode = $code;
        $this->status = $status;
        $this->extra = $extra;
    }
}

// ---------------------------------------------------------------- HTML helpers
function e($v): string { return htmlspecialchars((string)$v, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }

function fa_digits($v): string { return strtr((string)$v, ['0' => '۰', '1' => '۱', '2' => '۲', '3' => '۳', '4' => '۴', '5' => '۵', '6' => '۶', '7' => '۷', '8' => '۸', '9' => '۹']); }

function money($toman): string { return fa_digits(number_format((int)$toman)) . ' تومان'; }

/** Gregorian UTC → Jalali (Tehran time) date string. */
function jdate(?string $sql, bool $time = true): string
{
    if ($sql === null || $sql === '') return '—';
    $ts = Clock::ts($sql) + 3 * 3600 + 1800;
    [$gy, $gm, $gd] = array_map('intval', explode('-', gmdate('Y-m-d', $ts)));
    $g_d_m = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
    $gy2 = ($gm > 2) ? ($gy + 1) : $gy;
    $days = 355666 + (365 * $gy) + intdiv($gy2 + 3, 4) - intdiv($gy2 + 99, 100) + intdiv($gy2 + 399, 400) + $gd + $g_d_m[$gm - 1];
    $jy = -1595 + (33 * intdiv($days, 12053));
    $days %= 12053;
    $jy += 4 * intdiv($days, 1461);
    $days %= 1461;
    if ($days > 365) { $jy += intdiv($days - 1, 365); $days = ($days - 1) % 365; }
    if ($days < 186) { $jm = 1 + intdiv($days, 31); $jd = 1 + ($days % 31); }
    else { $jm = 7 + intdiv($days - 186, 30); $jd = 1 + (($days - 186) % 30); }
    $s = sprintf('%04d/%02d/%02d', $jy, $jm, $jd);
    if ($time) $s .= ' ' . gmdate('H:i', $ts);
    return fa_digits($s);
}

/** Validates an outbound link of an ad: http(s) only, no credentials, no private hosts. */
function safe_target_url(string $url): bool
{
    if ($url === '') return true;
    if (strpos($url, 'divan://') === 0) return isset(Ads::APP_TARGETS[$url]) || (bool)preg_match('#^divan://plan/[a-z0-9_]{1,32}$#', $url);
    $p = parse_url($url);
    if (!$p || !in_array(strtolower($p['scheme'] ?? ''), ['https', 'http'], true)) return false;
    if (isset($p['user']) || isset($p['pass'])) return false;
    $host = strtolower($p['host'] ?? '');
    if ($host === '' || $host === 'localhost' || str_ends_with($host, '.local') || str_ends_with($host, '.internal')) return false;
    if (filter_var($host, FILTER_VALIDATE_IP) && !filter_var($host, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE)) return false;
    return true;
}
