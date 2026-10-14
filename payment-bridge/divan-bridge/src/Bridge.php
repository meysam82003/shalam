<?php
/**
 * Divan Emtiaz — payment verification gateway (runs on the Iranian host, pay.httpuser87890.ir).
 *
 * Only the main Divan server may call it. Every request is signed with a shared key (HMAC-SHA256)
 * over a timestamp, a one-time nonce and the body; a nonce is accepted once. Only fixed actions exist,
 * each mapped to one official endpoint of Myket, Cafe Bazaar or ZarinPal: no URL ever comes from the caller.
 * Aban Gateway is the one exception: its API address is set in the Divan panel (so a domain change needs no
 * new bridge), but it must be a plain HTTPS domain, may be pinned by «aban_api_hosts» here, and only the
 * four invoice calls with checked fields can be sent to it.
 * The response is signed too, bound to the request nonce, so the caller can trust it.
 *
 * Store credentials live only in config.php outside the public folder. Logs never hold tokens or keys.
 */

declare(strict_types=1);

namespace DivanBridge;

final class Bridge
{
    public const VERSION = '1.1.0';
    public const MAX_BODY = 16384;
    public const WINDOW = 300;
    private const TAG = "DIVAN-BRIDGE-V1";

    /** The only places the gateway talks to. */
    private const MYKET = 'https://developer.myket.ir/api/partners/applications/';
    /** Documented verification address (myket.ir/kb «verification of in-app purchases on the server»). */
    private const MYKET_DOC = 'https://developer.myket.ir/api/applications/';
    private const BAZAAR = 'https://pardakht.cafebazaar.ir/devapi/v2/';

    /** ZarinPal payment gateway v4 (live and sandbox). */
    private const ZARINPAL = 'https://payment.zarinpal.com/pg/v4/payment/';
    private const ZARINPAL_SANDBOX = 'https://sandbox.zarinpal.com/pg/v4/payment/';

    private const DESTINATIONS = [self::MYKET, self::MYKET_DOC, self::BAZAAR, self::ZARINPAL, self::ZARINPAL_SANDBOX];

    public const ACTIONS = ['ping', 'myket.verify', 'myket.products', 'myket.set_price', 'myket.test', 'bazaar.verify', 'bazaar.test', 'zarinpal.request', 'zarinpal.verify', 'aban.call'];

    /** Aban Gateway's documented API address (docs: github.com/AbanGateway/docs). */
    public const ABAN_DEFAULT = 'https://api.abangateway.ir/api/v1';

    /** The checked Aban address of the current request (an extra allowed destination for this call only). */
    private string $extraDestination = '';

    /**
     * Replaced only by the automated tests: fn(method, url, headers, body, timeout) → [status, body, error].
     * @var callable|null
     */
    public static $transport = null;

    /** Fixed clock for tests. */
    public static ?int $now = null;

    private array $cfg;
    private string $data;
    private string $nonce = '';
    private string $key = '';
    private string $keyId = '';

    public function __construct(array $cfg, string $dataDir)
    {
        $this->cfg = $cfg;
        $this->data = rtrim($dataDir, '/');
    }

    private function now(): int { return self::$now ?? time(); }

    /** Valid shared keys by id; placeholders and short keys are ignored. */
    private function keys(): array
    {
        $out = [];
        foreach ((array)($this->cfg['keys'] ?? []) as $id => $k) {
            $id = (string)$id; $k = (string)$k;
            if (preg_match('/^[a-z0-9_-]{1,32}$/', $id) && strlen($k) >= 32 && stripos($k, 'PASTE') === false) $out[$id] = $k;
        }
        return $out;
    }

    private function secret(string $name): string
    {
        $v = trim((string)($this->cfg[$name] ?? ''));
        return stripos($v, 'PASTE') !== false ? '' : $v;
    }

    /**
     * Handles one HTTP request. $headers uses lower-case names.
     * @return array{0:int,1:array<string,string>,2:string}
     */
    public function handle(string $method, array $headers, string $body, string $ip): array
    {
        $t0 = microtime(true);
        if (strtoupper($method) !== 'POST') return $this->plain(405, 'method_not_allowed', ['Allow' => 'POST']);

        $allowed = array_filter(array_map('strval', (array)($this->cfg['allowed_ips'] ?? [])));
        if ($allowed && !in_array($ip, $allowed, true)) { $this->log($ip, '-', '-', 'ip_denied', 0, $t0); return $this->plain(403, 'forbidden'); }

        if (!$this->rateOk($ip)) { $this->log($ip, '-', '-', 'rate_limited', 0, $t0); return $this->plain(429, 'rate_limited', ['Retry-After' => '60']); }
        if (strlen($body) > self::MAX_BODY) { $this->log($ip, '-', '-', 'too_large', 0, $t0); return $this->plain(413, 'too_large'); }

        $kid = (string)($headers['x-divan-key'] ?? '');
        $ts = (string)($headers['x-divan-time'] ?? '');
        $nonce = (string)($headers['x-divan-nonce'] ?? '');
        $sig = strtolower((string)($headers['x-divan-signature'] ?? ''));
        $keys = $this->keys();
        if (!preg_match('/^[a-z0-9_-]{1,32}$/', $kid) || !preg_match('/^\d{9,11}$/', $ts) || !preg_match('/^[A-Za-z0-9_-]{16,64}$/', $nonce) || !preg_match('/^[0-9a-f]{64}$/', $sig)) {
            $this->log($ip, $kid, '-', 'auth_missing', 0, $t0); return $this->plain(401, 'unauthorized');
        }
        if (!isset($keys[$kid])) { $this->log($ip, $kid, '-', 'auth_unknown_key', 0, $t0); return $this->plain(401, 'unauthorized'); }
        $expected = self::sign($keys[$kid], $ts, $nonce, $body);
        if (!hash_equals($expected, $sig)) { $this->log($ip, $kid, '-', 'auth_bad_signature', 0, $t0); return $this->plain(401, 'unauthorized'); }
        // Checked after the signature, so an unsigned caller learns nothing about the clock.
        if (abs($this->now() - (int)$ts) > self::WINDOW) { $this->log($ip, $kid, '-', 'auth_expired', 0, $t0); return $this->plain(401, 'expired'); }
        if (!$this->useNonce($kid, $nonce)) { $this->log($ip, $kid, '-', 'auth_replay', 0, $t0); return $this->plain(401, 'replay'); }
        $this->nonce = $nonce; $this->key = $keys[$kid]; $this->keyId = $kid;

        $req = json_decode($body, true);
        $action = is_array($req) ? (string)($req['action'] ?? '') : '';
        $params = is_array($req) && is_array($req['params'] ?? null) ? $req['params'] : [];
        if (!in_array($action, self::ACTIONS, true)) { $this->log($ip, $kid, $action ?: '-', 'bad_action', 0, $t0); return $this->signed(400, ['ok' => false, 'kind' => 'bad_request', 'error' => 'unknown action']); }

        try {
            $result = $this->dispatch($action, $params);
        } catch (BadRequest $e) {
            $result = ['ok' => false, 'kind' => 'bad_request', 'error' => $e->getMessage()];
        }
        $this->log($ip, $kid, $action, (string)$result['kind'], (int)($result['status'] ?? 0), $t0, (string)($params['sku'] ?? ''), (string)($params['token'] ?? ''));
        return $this->signed($result['kind'] === 'bad_request' ? 400 : 200, $result);
    }

    public static function sign(string $key, string $ts, string $nonce, string $body): string
    {
        return hash_hmac('sha256', self::TAG . "\n" . $ts . "\n" . $nonce . "\n" . hash('sha256', $body), $key);
    }

    public static function signResponse(string $key, string $nonce, string $body): string
    {
        return hash_hmac('sha256', self::TAG . "-RESPONSE\n" . $nonce . "\n" . hash('sha256', $body), $key);
    }

    // ------------------------------------------------------------ actions
    private function dispatch(string $action, array $p): array
    {
        switch ($action) {
            case 'ping':
                return ['ok' => true, 'kind' => 'pong', 'version' => self::VERSION, 'time' => $this->now(), 'key_id' => $this->keyId,
                    'stores' => ['myket' => $this->secret('myket_access_token') !== '', 'bazaar' => $this->bazaarConfigured()],
                    'set_price' => (bool)($this->cfg['allow_set_price'] ?? false), 'actions' => self::ACTIONS,
                    'packages' => array_values((array)($this->cfg['packages'] ?? [])), 'skus' => array_values(array_filter((array)($this->cfg['skus'] ?? [])))];
            case 'myket.verify':
                [$pkg, $sku, $token] = [$this->package($p), $this->sku($p), $this->token($p)];
                // Default: the documented GET …/purchases/products/{sku}/tokens/{token}; the older «partners» form stays selectable.
                if (($this->cfg['myket_verify'] ?? 'tokens') === 'partners') return $this->myket('POST', $pkg . '/purchases/products/' . rawurlencode($sku) . '/verify', json_encode(['tokenId' => $token]));
                return $this->myket('GET', $pkg . '/purchases/products/' . rawurlencode($sku) . '/tokens/' . rawurlencode($token), null, self::MYKET_DOC);
            case 'myket.products':
            case 'myket.test':
                $r = $this->myket('GET', $this->package($p) . '/products?limit=200&offset=0', null);
                if ($action === 'myket.test' && $r['kind'] === 'store') $r['body'] = ['count' => is_array($r['body']['skus'] ?? null) ? count($r['body']['skus']) : null];
                return $r;
            case 'myket.set_price':
                if (!($this->cfg['allow_set_price'] ?? false)) return ['ok' => false, 'kind' => 'not_allowed', 'error' => 'set_price is off in config'];
                $rial = $p['price_rial'] ?? null;
                if (!is_int($rial) || $rial < 10000 || $rial > 1000000000) throw new BadRequest('price_rial');
                return $this->myket('PUT', $this->package($p) . '/products/' . rawurlencode($this->sku($p)), json_encode(['price' => $rial]));
            case 'bazaar.verify':
                $type = (string)($p['type'] ?? 'inapp');
                if (!in_array($type, ['inapp', 'subscription'], true)) throw new BadRequest('type');
                return $this->bazaar($this->package($p), $this->sku($p), $this->token($p), $type);
            case 'zarinpal.request':
            case 'zarinpal.verify':
                return $this->zarinpal($action === 'zarinpal.request' ? 'request' : 'verify', $p);
            case 'aban.call':
                return $this->aban($p);
            case 'bazaar.test':
                // A made-up receipt: 404 means the key was accepted, 401/403 that it was not.
                return $this->bazaar($this->package($p), 'divan_connectivity_probe', 'probe' . bin2hex(random_bytes(6)), 'inapp');
        }
        throw new BadRequest('action');
    }

    private function package(array $p): string
    {
        $pkg = (string)($p['package'] ?? '');
        $allowed = (array)($this->cfg['packages'] ?? ['com.meysam.divanemtiaz']);
        if (!preg_match('/^[a-zA-Z][a-zA-Z0-9_.]{2,100}$/', $pkg) || !in_array($pkg, $allowed, true)) throw new BadRequest('package');
        return rawurlencode($pkg);
    }

    private function sku(array $p): string
    {
        $sku = (string)($p['sku'] ?? '');
        if (!preg_match('/^[A-Za-z0-9_.]{1,64}$/', $sku)) throw new BadRequest('sku');
        $list = array_filter((array)($this->cfg['skus'] ?? []));
        if ($list && !in_array($sku, $list, true)) throw new BadRequest('sku');
        return $sku;
    }

    private function token(array $p): string
    {
        $t = (string)($p['token'] ?? '');
        if (!preg_match('#^[A-Za-z0-9._:+/=-]{8,600}$#', $t)) throw new BadRequest('token');
        return $t;
    }

    private function myket(string $method, string $path, ?string $body, string $base = self::MYKET): array
    {
        $token = $this->secret('myket_access_token');
        if ($token === '') return ['ok' => false, 'kind' => 'not_configured', 'error' => 'myket token missing'];
        $h = ['X-Access-Token: ' . $token, 'Accept: application/json'];
        if ($body !== null) $h[] = 'Content-Type: application/json';
        return $this->storeResult($this->http($method, $base . $path, $h, $body));
    }

    /** ZarinPal: fixed address, every field checked; the buyer may only be sent back to the Divan site. */
    private function zarinpal(string $what, array $p): array
    {
        $merchant = (string)($p['merchant_id'] ?? '');
        if (!preg_match('/^[0-9a-fA-F-]{36}$/', $merchant)) throw new BadRequest('merchant_id');
        $list = array_filter((array)($this->cfg['zarinpal_merchants'] ?? []));
        if ($list && !in_array($merchant, $list, true)) throw new BadRequest('merchant_id');
        $amount = $p['amount'] ?? null;
        if (!is_int($amount) || $amount < 10000 || $amount > 2000000000) throw new BadRequest('amount');
        $body = ['merchant_id' => $merchant, 'amount' => $amount];
        if ($what === 'request') {
            $cb = (string)($p['callback_url'] ?? '');
            $host = strtolower((string)parse_url($cb, PHP_URL_HOST));
            $hosts = (array)($this->cfg['callback_hosts'] ?? ['httpuser87890.ir']);
            if (stripos($cb, 'https://') !== 0 || strlen($cb) > 300 || !in_array($host, $hosts, true) || preg_match('/[\s"<>]/', $cb)) throw new BadRequest('callback_url');
            $order = (string)($p['metadata']['order_id'] ?? '');
            if (!preg_match('/^[a-z0-9]{20}$/', $order)) throw new BadRequest('order_id');
            $body += ['currency' => 'IRR', 'callback_url' => $cb, 'description' => mb_substr(strip_tags((string)($p['description'] ?? '')), 0, 200), 'metadata' => ['order_id' => $order]];
        } else {
            $authority = (string)($p['authority'] ?? '');
            if (!preg_match('/^[A-Za-z0-9]{10,64}$/', $authority)) throw new BadRequest('authority');
            $body['authority'] = $authority;
        }
        $base = !empty($p['sandbox']) ? self::ZARINPAL_SANDBOX : self::ZARINPAL;
        $r = $this->http('POST', $base . $what . '.json', ['Content-Type: application/json', 'Accept: application/json'], json_encode($body, JSON_UNESCAPED_UNICODE));
        if ((int)$r['status'] === 0) return ['ok' => false, 'kind' => 'store_unreachable', 'status' => 0, 'error' => self::netError((string)$r['error'])];
        $j = json_decode((string)$r['body'], true);
        return ['ok' => true, 'kind' => 'store', 'status' => (int)$r['status'], 'body' => is_array($j) ? $j : null];
    }

    /** A plain HTTPS address on a domain name: no IP, no port, no user, no query, no «..». */
    public static function safeBase(string $base): bool
    {
        return strlen($base) <= 200 && (bool)preg_match('#^https://([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}(/[A-Za-z0-9._~-]+)*$#i', $base) && strpos($base, '..') === false;
    }

    /**
     * Aban Gateway invoices: create, read, verify, cancel. The token comes from the Divan panel; the body of a new
     * invoice is rebuilt here from checked fields, and the buyer and the webhook may only go to the Divan site.
     */
    private function aban(array $p): array
    {
        $base = rtrim((string)($p['base'] ?? self::ABAN_DEFAULT), '/');
        if (!self::safeBase($base)) throw new BadRequest('base');
        $pinned = array_map('strtolower', array_filter((array)($this->cfg['aban_api_hosts'] ?? [])));
        if ($pinned && !in_array(strtolower((string)parse_url($base, PHP_URL_HOST)), $pinned, true)) throw new BadRequest('base');
        $token = (string)($p['api_token'] ?? '');
        if (!preg_match('/^ag_(live|test)_[A-Za-z0-9]{16,128}$/', $token)) throw new BadRequest('api_token');
        $method = (string)($p['method'] ?? '');
        $path = (string)($p['path'] ?? '');
        $id = '[A-Za-z0-9_-]{4,80}';
        $body = null;
        if ($method === 'POST' && $path === '/invoices') {
            $in = is_array($p['body'] ?? null) ? $p['body'] : [];
            $amount = $in['amount_rial'] ?? null;
            if (!is_int($amount) || $amount < 10000 || $amount > 2000000000) throw new BadRequest('amount_rial');
            $order = (string)($in['order_id'] ?? '');
            if (!preg_match('/^[a-z0-9]{20}$/', $order)) throw new BadRequest('order_id');
            $out = ['amount_rial' => $amount, 'order_id' => $order];
            foreach (['callback_url', 'return_url'] as $k) {
                if (!isset($in[$k])) continue;
                $u = (string)$in[$k];
                $host = strtolower((string)parse_url($u, PHP_URL_HOST));
                $hosts = (array)($this->cfg['callback_hosts'] ?? ['httpuser87890.ir']);
                if (stripos($u, 'https://') !== 0 || strlen($u) > 300 || !in_array($host, $hosts, true) || preg_match('/[\s"<>]/', $u)) throw new BadRequest($k);
                $out[$k] = $u;
            }
            if (isset($in['description'])) $out['description'] = mb_substr(strip_tags((string)$in['description']), 0, 200);
            if (isset($in['expiry_minutes'])) {
                if (!is_int($in['expiry_minutes']) || $in['expiry_minutes'] < 1 || $in['expiry_minutes'] > 1440) throw new BadRequest('expiry_minutes');
                $out['expiry_minutes'] = $in['expiry_minutes'];
            }
            $body = json_encode($out, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        } elseif ($method === 'GET' && preg_match('#^/invoices/' . $id . '$#', $path)) {
            $body = null;
        } elseif ($method === 'POST' && preg_match('#^/invoices/' . $id . '/(verify|cancel)$#', $path)) {
            $body = '{}';
        } else {
            throw new BadRequest('path');
        }
        $headers = ['Authorization: Bearer ' . $token, 'Accept: application/json'];
        if ($body !== null) $headers[] = 'Content-Type: application/json';
        $this->extraDestination = $base . '/';
        try {
            $r = $this->http($method, $base . $path, $headers, $body);
        } finally {
            $this->extraDestination = '';
        }
        if ((int)$r['status'] === 0) return ['ok' => false, 'kind' => 'store_unreachable', 'status' => 0, 'error' => self::netError((string)$r['error'])];
        $j = json_decode((string)$r['body'], true);
        return ['ok' => true, 'kind' => 'store', 'status' => (int)$r['status'], 'body' => is_array($j) ? $j : null];
    }

    private function bazaarConfigured(): bool
    {
        return $this->secret('bazaar_api_secret') !== '' || $this->secret('bazaar_refresh_token') !== '';
    }

    private function bazaar(string $pkg, string $sku, string $token, string $type): array
    {
        if (!$this->bazaarConfigured()) return ['ok' => false, 'kind' => 'not_configured', 'error' => 'bazaar key missing'];
        $path = $type === 'subscription'
            ? 'api/applications/' . $pkg . '/subscriptions/' . rawurlencode($sku) . '/purchases/' . rawurlencode($token)
            : 'api/validate/' . $pkg . '/inapp/' . rawurlencode($sku) . '/purchases/' . rawurlencode($token) . '/';
        $url = self::BAZAAR . $path;
        $h = ['Accept: application/json'];
        $secret = $this->secret('bazaar_api_secret');
        if ($secret !== '') $h[] = 'CAFEBAZAAR-PISHKHAN-API-SECRET: ' . $secret;
        else {
            $access = $this->bazaarAccess();
            if ($access === null) return ['ok' => false, 'kind' => 'store_auth', 'status' => 401, 'error' => 'bazaar oauth refresh failed'];
            $url .= '?access_token=' . rawurlencode($access);
        }
        return $this->storeResult($this->http('GET', $url, $h, null));
    }

    /** Older Bazaar OAuth: access token from the refresh token, cached in the private data folder. */
    private function bazaarAccess(): ?string
    {
        $file = $this->data . '/cache/bazaar_access.json';
        $c = is_file($file) ? json_decode((string)file_get_contents($file), true) : null;
        if (is_array($c) && ($c['until'] ?? 0) > $this->now() + 60 && !empty($c['token'])) return (string)$c['token'];
        $r = $this->http('POST', self::BAZAAR . 'auth/token/', ['Content-Type: application/x-www-form-urlencoded', 'Accept: application/json'], http_build_query([
            'grant_type' => 'refresh_token', 'client_id' => $this->secret('bazaar_client_id'),
            'client_secret' => $this->secret('bazaar_client_secret'), 'refresh_token' => $this->secret('bazaar_refresh_token'),
        ]));
        $j = json_decode($r['body'], true);
        if ($r['status'] !== 200 || empty($j['access_token'])) return null;
        @mkdir(dirname($file), 0700, true);
        file_put_contents($file, json_encode(['token' => (string)$j['access_token'], 'until' => $this->now() + (int)($j['expires_in'] ?? 3600)]), LOCK_EX);
        @chmod($file, 0600);
        return (string)$j['access_token'];
    }

    /** Separates "the store answered" from "the store refused our key" from "the store could not be reached". */
    private function storeResult(array $r): array
    {
        $status = (int)$r['status'];
        if ($status === 0) return ['ok' => false, 'kind' => 'store_unreachable', 'status' => 0, 'error' => self::netError((string)$r['error'])];
        $j = json_decode((string)$r['body'], true);
        $body = is_array($j) ? $j : null;
        if ($status === 401 || $status === 403) return ['ok' => false, 'kind' => 'store_auth', 'status' => $status, 'body' => $body];
        return ['ok' => true, 'kind' => 'store', 'status' => $status, 'body' => $body];
    }

    private static function netError(string $e): string
    {
        $l = strtolower($e);
        if (strpos($l, 'timed out') !== false || strpos($l, 'timeout') !== false) return 'timeout';
        if (strpos($l, 'resolve') !== false) return 'dns';
        if (strpos($l, 'ssl') !== false || strpos($l, 'certificate') !== false || strpos($l, 'tls') !== false) return 'tls';
        if (strpos($l, 'connect') !== false) return 'connect';
        return 'network';
    }

    /** HTTPS only, certificate checked, no redirects, fixed destinations only. */
    private function http(string $method, string $url, array $headers, ?string $body): array
    {
        $allowed = false;
        foreach (self::DESTINATIONS as $d) if (strpos($url, $d) === 0) $allowed = true;
        if ($this->extraDestination !== '' && strpos($url, $this->extraDestination) === 0) $allowed = true;
        if (!$allowed) return ['status' => 0, 'body' => '', 'error' => 'destination not allowed'];
        $timeout = max(5, min(40, (int)($this->cfg['timeout'] ?? 20)));
        if (self::$transport !== null) return (self::$transport)($method, $url, $headers, $body, $timeout);
        if (!function_exists('curl_init')) return ['status' => 0, 'body' => '', 'error' => 'curl extension missing'];
        $ch = curl_init($url);
        curl_setopt_array($ch, [
            CURLOPT_CUSTOMREQUEST => $method,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_MAXREDIRS => 0,
            CURLOPT_TIMEOUT => $timeout,
            CURLOPT_CONNECTTIMEOUT => min(10, $timeout),
            CURLOPT_HTTPHEADER => $headers,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
            CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS,
        ]);
        if ($body !== null) curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
        $resp = curl_exec($ch);
        $err = $resp === false ? curl_error($ch) : '';
        $status = (int)curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
        curl_close($ch);
        if ($resp !== false && strlen((string)$resp) > 2000000) return ['status' => 0, 'body' => '', 'error' => 'response too large'];
        return ['status' => $resp === false ? 0 : $status, 'body' => $resp === false ? '' : (string)$resp, 'error' => $err];
    }

    // ------------------------------------------------------------ replay, rate limit, log
    /** A nonce is stored by creating its file exclusively: two equal requests cannot both pass. */
    private function useNonce(string $kid, string $nonce): bool
    {
        $h = hash('sha256', $kid . ':' . $nonce);
        $dir = $this->data . '/nonces/' . substr($h, 0, 2);
        if (!is_dir($dir) && !@mkdir($dir, 0700, true) && !is_dir($dir)) return false;
        $f = @fopen($dir . '/' . $h, 'x');
        if ($f === false) return false;
        fwrite($f, (string)$this->now());
        fclose($f);
        if (random_int(1, 50) === 1) $this->pruneNonces();
        return true;
    }

    private function pruneNonces(): void
    {
        $limit = $this->now() - 2 * self::WINDOW - 60;
        foreach (glob($this->data . '/nonces/*/*') ?: [] as $f) if (@filemtime($f) < $limit) @unlink($f);
    }

    private function rateOk(string $ip): bool
    {
        $max = max(10, (int)($this->cfg['rate_per_minute'] ?? 120));
        $dir = $this->data . '/rate';
        if (!is_dir($dir)) @mkdir($dir, 0700, true);
        $f = @fopen($dir . '/' . hash('sha256', $ip), 'c+');
        if ($f === false) return true;
        flock($f, LOCK_EX);
        $minute = intdiv($this->now(), 60);
        $c = json_decode((string)stream_get_contents($f), true);
        $n = (is_array($c) && ($c['m'] ?? 0) === $minute) ? (int)$c['n'] + 1 : 1;
        ftruncate($f, 0); rewind($f); fwrite($f, json_encode(['m' => $minute, 'n' => $n]));
        flock($f, LOCK_UN); fclose($f);
        return $n <= $max;
    }

    /** One JSON line per request: never a token, key or store body; only a short fingerprint of the token. */
    private function log(string $ip, string $kid, string $action, string $result, int $status, float $t0, string $sku = '', string $token = ''): void
    {
        $dir = $this->data . '/logs';
        if (!is_dir($dir)) @mkdir($dir, 0700, true);
        $line = json_encode([
            't' => gmdate('c', $this->now()), 'ip' => $ip, 'key' => substr($kid, 0, 32), 'action' => substr($action, 0, 32), 'result' => $result,
            'status' => $status, 'ms' => (int)round((microtime(true) - $t0) * 1000),
            'sku' => preg_match('/^[A-Za-z0-9_.]{1,64}$/', $sku) ? $sku : '', 'token_fp' => $token === '' ? '' : substr(hash('sha256', $token), 0, 10),
        ], JSON_UNESCAPED_SLASHES);
        @file_put_contents($dir . '/bridge-' . gmdate('Y-m', $this->now()) . '.log', $line . "\n", FILE_APPEND | LOCK_EX);
    }

    // ------------------------------------------------------------ responses
    /** Unsigned short answer for requests that did not prove who they are. */
    private function plain(int $status, string $error, array $headers = []): array
    {
        return [$status, $headers + ['Content-Type' => 'application/json; charset=utf-8'], json_encode(['ok' => false, 'error' => $error])];
    }

    private function signed(int $status, array $data): array
    {
        $body = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        return [$status, ['Content-Type' => 'application/json; charset=utf-8', 'X-Divan-Signature' => self::signResponse($this->key, $this->nonce, $body)], $body];
    }
}

final class BadRequest extends \RuntimeException {}

/** Front controller: finds the request data and writes the answer. */
function run(array $cfg, string $dataDir): void
{
    $headers = [];
    foreach ($_SERVER as $k => $v) if (strpos($k, 'HTTP_X_DIVAN_') === 0) $headers[strtolower(str_replace('_', '-', substr($k, 5)))] = (string)$v;
    $body = (string)file_get_contents('php://input', false, null, 0, Bridge::MAX_BODY + 1);
    $ip = (string)($_SERVER['REMOTE_ADDR'] ?? '');
    [$status, $out, $payload] = (new Bridge($cfg, $dataDir))->handle((string)($_SERVER['REQUEST_METHOD'] ?? 'GET'), $headers, $body, $ip);
    http_response_code($status);
    header('Cache-Control: no-store');
    header('X-Content-Type-Options: nosniff');
    foreach ($out as $k => $v) header($k . ': ' . $v);
    echo $payload;
}
