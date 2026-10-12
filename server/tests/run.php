<?php
/**
 * Divan Emtiaz server — automated tests against a real MySQL/MariaDB database with fake stores.
 *
 *   DIVAN_TEST_DSN="host=127.0.0.1;port=3306;dbname=divan_test2" DIVAN_TEST_USER=divan DIVAN_TEST_PASS=... php server/tests/run.php
 *
 * The database is emptied (only tables with the test prefix "dvt_") before the run.
 */

declare(strict_types=1);

define('DIVAN', true);
define('DIVAN_TEST', true);
$src = dirname(__DIR__) . '/public';
require $src . '/app/lib.php';
require $src . '/app/migrations.php';
require $src . '/app/catalog.php';
require $src . '/app/domain.php';
require $src . '/app/markets.php';
require $src . '/app/backup.php';
require $src . '/app/installer.php';
require $src . '/app/api.php';

// ---------------------------------------------------------------- environment
$root = sys_get_temp_dir() . '/divan-test-' . bin2hex(random_bytes(4));
foreach (['storage/keys', 'storage/backups', 'storage/logs', 'media'] as $d) mkdir("$root/$d", 0700, true);
App::$root = $root;
App::$config = [
    'db_prefix' => 'dvt_', 'app_key' => base64_encode(random_bytes(32)), 'backup_key' => base64_encode(random_bytes(32)),
    'cron_key' => 'test', 'base_url' => 'https://example.test/divan',
];
file_put_contents("$root/storage/config.php", '<?php return ' . var_export(App::$config, true) . ';');
$dsn = 'mysql:' . (getenv('DIVAN_TEST_DSN') ?: 'host=127.0.0.1;port=3306;dbname=divan_test2') . ';charset=utf8mb4';
$pdo = new PDO($dsn, getenv('DIVAN_TEST_USER') ?: 'divan', (string)getenv('DIVAN_TEST_PASS'), [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC, PDO::ATTR_EMULATE_PREPARES => false]);
$pdo->exec("SET time_zone = '+00:00'");
foreach ($pdo->query("SHOW TABLES LIKE 'dvt\\_%'")->fetchAll(PDO::FETCH_COLUMN) as $t) $pdo->exec("DROP TABLE `$t`");
Db::use($pdo, 'dvt_');
$_SERVER['REMOTE_ADDR'] = '10.0.0.1';
date_default_timezone_set('UTC');
Clock::$fixed = 1_800_000_000;
Migrations::run();
Crypto::generateKeys("$root/storage/keys");

// ---------------------------------------------------------------- tiny test kit
$passed = 0; $failed = []; $current = '';
function check(bool $ok, string $what): void
{
    global $passed, $failed, $current;
    if ($ok) $passed++; else { $failed[] = "$current: $what"; echo "  ✗ $what\n"; }
}
function throws(callable $fn, string $code, string $what): void
{
    try { $fn(); check(false, "$what (no error, expected $code)"); }
    catch (ApiError $e) { check($e->errorCode === $code, "$what (got {$e->errorCode}, expected $code)"); }
}
function scenario(string $name, callable $fn): void
{
    global $current;
    $current = $name;
    echo "• $name\n";
    try { $fn(); } catch (Throwable $e) { check(false, 'unexpected ' . get_class($e) . ': ' . $e->getMessage() . ' @' . basename($e->getFile()) . ':' . $e->getLine()); }
    Settings::reset();
}
function freshIp(): void { $_SERVER['REMOTE_ADDR'] = '10.' . random_int(1, 250) . '.' . random_int(1, 250) . '.' . random_int(1, 250); }

/** Fake store: answers by token; default valid. */
final class FakeStore implements StoreVerifier
{
    public array $answers = [];
    public int $calls = 0;
    public function configured(): bool { return true; }
    public function verify(string $sku, string $token, string $productType): StoreVerdict
    {
        $this->calls++;
        $a = $this->answers[$token] ?? 'valid';
        if ($a === 'valid') return new StoreVerdict(StoreVerdict::VALID, '', Clock::now() - 60, null, false, false, ['purchaseState' => 0]);
        if ($a === 'refunded') return new StoreVerdict(StoreVerdict::REFUNDED, 'refunded by store');
        if ($a === 'invalid') return new StoreVerdict(StoreVerdict::INVALID, 'unknown purchase');
        return new StoreVerdict(StoreVerdict::UNAVAILABLE, 'store offline');
    }
}
$bazaar = new FakeStore(); $myket = new FakeStore();
Stores::$override = ['bazaar' => $bazaar, 'myket' => $myket];

// A store signing key, like the RSA key in the Bazaar/Myket developer panel.
$storeKey = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
$storePub = openssl_pkey_get_details($storeKey)['key'];
$storePubB64 = preg_replace('/-----[^-]+-----|\s/', '', $storePub);
Settings::set('bazaar', ['enabled' => true, 'package' => 'com.meysam.divanemtiaz', 'public_key' => $storePubB64]);
Settings::set('myket', ['enabled' => true, 'package' => 'com.meysam.divanemtiaz', 'public_key' => $storePubB64]);

function receipt(string $store, string $sku, string $token, string $payload, $key, bool $forge = false): array
{
    $json = json_encode(['orderId' => 'ORD-' . substr($token, -6), 'packageName' => 'com.meysam.divanemtiaz', 'productId' => $sku, 'purchaseTime' => Clock::now() * 1000, 'purchaseState' => 0, 'developerPayload' => $payload, 'purchaseToken' => $token]);
    openssl_sign($json, $sig, $key, OPENSSL_ALGO_SHA1);
    if ($forge) $sig = strrev($sig);
    return ['store' => $store, 'sku' => $sku, 'token' => $token, 'original_json' => $json, 'signature' => base64_encode($sig), 'order_id' => 'ORD-' . substr($token, -6), 'payload' => $payload];
}
function syncPrices(): void
{
    foreach (Plans::all() as $p) Db::update('plans', ['store_price_bazaar' => $p['base_price_toman'], 'store_price_myket' => $p['base_price_toman']], 'id = ?', [$p['id']]);
}
function buy(array $u, string $plan, string $store, string $token, $key, string $answer = 'valid'): array
{
    global $bazaar, $myket;
    $fake = $store === 'bazaar' ? $bazaar : $myket;
    $fake->answers[$token] = $answer;
    $i = Purchases::intent($u, $plan, $store);
    return Purchases::verify($u, receipt($store, $i['sku'], $token, $i['payload'], $key));
}

function apiPost(string $route, array $body): array { Api::reset(); Http::$testBody = $body; return Api::handle($route, 'POST'); }
function apiGet(string $route, array $query = []): array { Api::reset(); $_GET = $query; return Api::handle($route, 'GET'); }

// ================================================================ scenarios
scenario('accounts: register, login, lockout', function () {
    freshIp();
    [$u, $key] = Users::register('Sara_1', 'secret123');
    check(preg_match('/^([A-HJ-NP-Z2-9]{4}-){7}[A-HJ-NP-Z2-9]{4}$/', $key) === 1, 'recovery key is 8×4 base32 groups (160 bits)');
    check($u['recovery_hash'] !== null && strpos($u['recovery_hash'], $key) === false, 'recovery key stored only as a hash');
    throws(fn() => Users::register('sara_1', 'secret123'), 'username_taken', 'usernames are unique regardless of case');
    throws(fn() => Users::register('ab', 'secret123'), 'bad_username', 'short username rejected');
    throws(fn() => Users::register('nobody', 'password'), 'weak_password', 'password without digits rejected');
    check(Users::login('SARA_1', 'secret123')['id'] === $u['id'], 'login is case-insensitive on username');
    throws(fn() => Users::login('sara_1', 'wrong123'), 'bad_credentials', 'wrong password rejected');
    throws(fn() => Users::login('ghost', 'wrong123'), 'bad_credentials', 'unknown user gives the same error');
    [$v] = Users::register('victim', 'secret123');
    for ($i = 0; $i < 10; $i++) { freshIp(); try { Users::login('victim', 'nope1234'); } catch (ApiError $e) {} }
    freshIp();
    throws(fn() => Users::login('victim', 'secret123'), 'locked', 'account locked after 10 failures, even with the right password');
    freshIp(); try { Users::login('victim', 'x1234567'); } catch (ApiError $e) {}
    freshIp();
    throws(fn() => Users::login('victim', 'secret123'), 'rate_limited', 'more attempts on one account are throttled from any network');
    RateLimit::clear('login-user:victim');
    Clock::$fixed += 901;
    check(Users::login('victim', 'secret123')['id'] === $v['id'], 'lock expires after 15 minutes');
});

scenario('accounts: recovery key, rotation, sessions', function () {
    freshIp();
    [$u, $key] = Users::register('recover_me', 'secret123');
    $t1 = Sessions::create((int)$u['id'], ['device' => 'A']);
    throws(fn() => Users::recover('recover_me', 'AAAA-BBBB-CCCC-DDDD-EEEE-FFFF-GGGG-HHHH', 'newpass123'), 'bad_recovery', 'wrong recovery key rejected');
    check((int)Db::value("SELECT COUNT(*) FROM {security_events} WHERE kind = 'recover_failed' AND user_id = ?", [$u['id']]) === 1, 'failed recovery is logged for the panel');
    [$u2, $key2] = Users::recover('recover_me', strtolower(str_replace('-', ' ', $key)), 'newpass123');
    check($key2 !== $key, 'a new recovery key is issued after use');
    check(Users::login('recover_me', 'newpass123')['id'] === $u['id'], 'new password works');
    throws(fn() => Users::recover('recover_me', $key, 'another123'), 'bad_recovery', 'old recovery key no longer works');
    $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $t1;
    check(Sessions::current() === null, 'all sessions closed after recovery');
    unset($_SERVER['HTTP_AUTHORIZATION']);
    $k3 = Users::newRecoveryKey(Users::byId((int)$u['id']), 'newpass123');
    check(Users::recover('recover_me', $k3, 'third1234')[0]['id'] === $u['id'], 'rotated key from the app works');
    for ($i = 0; $i < 5; $i++) { try { Users::recover('recover_me', 'x', 'third1234'); } catch (ApiError $e) {} }
    throws(fn() => Users::recover('recover_me', 'x', 'third1234'), 'rate_limited', 'recovery attempts are limited per account');
});

scenario('plans: price sync gate and real discounts', function () {
    freshIp();
    [$u] = Users::register('buyer0', 'secret123');
    foreach (Plans::offers('bazaar') as $o) check($o['state'] === 'price_sync_pending' && !$o['purchasable'], "plan {$o['code']} not purchasable before the store price is confirmed");
    throws(fn() => Purchases::intent($u, 'm1', 'bazaar'), 'not_purchasable', 'intent refused while prices are not synced');
    syncPrices();
    $offers = array_column(Plans::offers('myket'), null, 'code');
    check($offers['m1']['final_price'] === 19000 && $offers['m3']['final_price'] === 49000 && $offers['m6']['final_price'] === 89000 && $offers['life']['final_price'] === 220000, 'default prices 19/49/89/220 thousand toman');
    check($offers['m1']['purchasable'] && $offers['life']['lifetime'], 'plans purchasable after sync');
    $plan = Plans::byCode('m3');
    $cid = Db::insert('campaigns', ['name' => 'نوروز', 'plan_id' => $plan['id'], 'store' => 'all', 'price_toman' => 39000, 'label' => 'نوروزی', 'starts_at' => Clock::sql(Clock::now() - 60), 'ends_at' => Clock::sql(Clock::now() + 86400), 'active' => 1, 'created_at' => Clock::sql(), 'updated_at' => Clock::sql()]);
    $m = Plans::offer(Plans::byCode('m3'), 'myket');
    check($m['final_price'] === 39000 && $m['discount_percent'] === 20 && $m['state'] === 'price_sync_pending', 'campaign shows only after the store price matches (Myket)');
    $b = Plans::offer(Plans::byCode('m3'), 'bazaar');
    check($b['state'] === 'price_sync_pending', 'Bazaar without discount key also waits');
    Settings::setSecret('bazaar_discount_key', 'k3y-for-dynamic-pricing');
    $b = Plans::offer(Plans::byCode('m3'), 'bazaar');
    check($b['state'] === 'ok' && $b['dynamic_price'], 'Bazaar dynamic discount makes the campaign purchasable');
    $i = Purchases::intent($u, 'm3', 'bazaar');
    [$h, $c, $s] = explode('.', (string)$i['dynamic_price_token']);
    $claims = json_decode(Crypto::b64uDecode($c), true);
    check(hash_equals(Crypto::b64u(hash_hmac('sha256', "$h.$c", 'k3y-for-dynamic-pricing', true)), $s), 'dynamic price token is signed with HS256');
    check($claims['price'] === 390000 && $claims['sku'] === 'divan_pro_3m' && $claims['exp'] > Clock::now(), 'token carries price in rials, sku and expiry');
    check($i['price'] === 39000, 'intent records the campaign price');
    Db::update('campaigns', ['active' => 0], 'id = ?', [$cid]);
    Settings::setSecret('bazaar_discount_key', '');
    check(Plans::offer(Plans::byCode('m3'), 'bazaar')['final_price'] === 49000, 'price returns after the campaign ends');
});

scenario('purchase: each plan, duplicate, stacking, consume', function () use ($storeKey) {
    freshIp();
    [$u] = Users::register('buyer1', 'secret123');
    $r = buy($u, 'm1', 'bazaar', 'tok-m1-a', $storeKey);
    check($r['purchase']['status'] === 'verified' && $r['consume'] === true, '1-month: verified and consumable');
    $s = Entitlements::summary((int)$u['id']);
    check($s['pro'] && $s['days_left'] === 30, '1-month gives 30 days');
    $again = Purchases::verify($u, receipt('bazaar', 'divan_pro_1m', 'tok-m1-a', '', $storeKey));
    check((int)$again['purchase']['id'] === (int)$r['purchase']['id'], 'duplicate receipt returns the same purchase');
    check((int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE user_id = ?', [$u['id']]) === 1, 'duplicate receipt grants nothing more');
    buy($u, 'm3', 'myket', 'tok-m3-a', $storeKey);
    check(Entitlements::summary((int)$u['id'])['days_left'] === 120, '3-month stacks after the current period (30 + 90)');
    buy($u, 'm6', 'bazaar', 'tok-m6-a', $storeKey);
    check(Entitlements::summary((int)$u['id'])['days_left'] === 300, '6-month stacks too (120 + 180)');
    $r = buy($u, 'life', 'myket', 'tok-life-a', $storeKey);
    check($r['consume'] === false, 'lifetime is never consumed');
    $s = Entitlements::summary((int)$u['id']);
    check($s['lifetime'] && $s['expires_at'] === null, 'lifetime has no end');
    throws(fn() => Purchases::intent($u, 'm1', 'bazaar'), 'already_lifetime', 'no more purchases after lifetime');
    check((int)Db::value('SELECT COUNT(*) FROM {purchase_intents} WHERE user_id = ? AND used_at IS NOT NULL', [$u['id']]) === 4, 'every intent marked used');
});

scenario('purchase: fake, mismatched and foreign receipts', function () use ($storeKey) {
    freshIp();
    [$u] = Users::register('buyer2', 'secret123');
    [$other] = Users::register('buyer3', 'secret123');
    $i = Purchases::intent($u, 'm1', 'bazaar');
    throws(fn() => Purchases::verify($u, receipt('bazaar', 'divan_pro_1m', 'tok-fake', $i['payload'], $storeKey, true)), 'bad_signature', 'forged signature rejected');
    $attacker = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
    throws(fn() => Purchases::verify($u, receipt('bazaar', 'divan_pro_1m', 'tok-fake2', $i['payload'], $attacker)), 'bad_signature', 'receipt signed with another key rejected');
    $r = receipt('bazaar', 'divan_pro_1m', 'tok-mis', $i['payload'], $storeKey);
    $r['sku'] = 'divan_pro_lifetime';
    throws(fn() => Purchases::verify($u, $r), 'bad_receipt', 'receipt for a cheaper product cannot claim lifetime');
    throws(fn() => Purchases::verify($u, ['store' => 'bazaar', 'sku' => 'nope', 'token' => 'x']), 'unknown_sku', 'unknown product rejected');
    check(!Entitlements::summary((int)$u['id'])['pro'], 'nothing granted for fake receipts');
    check((int)Db::value("SELECT COUNT(*) FROM {security_events} WHERE kind = 'receipt_bad_signature'") >= 2, 'fake receipts logged');
    // Another account replays the receipt of a real purchase.
    buy($u, 'm1', 'bazaar', 'tok-real-2', $storeKey);
    throws(fn() => Purchases::verify($other, receipt('bazaar', 'divan_pro_1m', 'tok-real-2', '', $storeKey)), 'owned_by_other', 'receipt cannot move to another account');
    $i2 = Purchases::intent($u, 'm1', 'myket');
    throws(fn() => Purchases::verify($other, receipt('myket', 'divan_pro_1m', 'tok-steal', $i2['payload'], $storeKey)), 'payload_mismatch', 'purchase started by another account cannot be claimed');
    check(!Entitlements::summary((int)$other['id'])['pro'], 'the other account got nothing');
});

scenario('purchase: canceled, failed, delayed store confirmation', function () use ($storeKey, $bazaar) {
    freshIp();
    [$u] = Users::register('buyer4', 'secret123');
    // Canceled in the store: the app never sends a receipt; the unused intent changes nothing.
    Purchases::intent($u, 'm1', 'bazaar');
    check(!Entitlements::summary((int)$u['id'])['pro'], 'canceled purchase grants nothing');
    throws(fn() => buy($u, 'm1', 'bazaar', 'tok-invalid', $storeKey, 'invalid'), 'rejected', 'store says not paid → rejected');
    check(!Entitlements::summary((int)$u['id'])['pro'], 'failed payment grants nothing');
    // Store API down, signature valid: provisional Pro, confirmed later.
    $r = buy($u, 'm1', 'bazaar', 'tok-delay', $storeKey, 'offline');
    check($r['purchase']['status'] === 'signature_verified', 'store offline + valid signature → provisional');
    check(Entitlements::summary((int)$u['id'])['pro'], 'buyer is not left waiting');
    $bazaar->answers['tok-delay'] = 'valid';
    Clock::$fixed += 400;
    Purchases::recheck(20);
    check(Db::value('SELECT status FROM {purchases} WHERE id = ?', [$r['purchase']['id']]) === 'verified', 'recheck confirms with the store later');
    // Store down and no signature key configured → pending, nothing granted until the store answers.
    Settings::set('bazaar', ['enabled' => true, 'package' => 'com.meysam.divanemtiaz', 'public_key' => '']);
    [$w] = Users::register('buyer5', 'secret123');
    $r = buy($w, 'm1', 'bazaar', 'tok-pending', $storeKey, 'offline');
    check($r['purchase']['status'] === 'pending' && !Entitlements::summary((int)$w['id'])['pro'], 'unverifiable receipt waits as pending');
    $bazaar->answers['tok-pending'] = 'valid';
    Clock::$fixed += 400;
    Purchases::recheck(20);
    check(Entitlements::summary((int)$w['id'])['pro'], 'pending purchase granted once the store confirms');
    global $storePubB64;
    Settings::set('bazaar', ['enabled' => true, 'package' => 'com.meysam.divanemtiaz', 'public_key' => $storePubB64]);
});

scenario('refund, expiry and restore after reinstall', function () use ($storeKey, $bazaar) {
    freshIp();
    [$u] = Users::register('buyer6', 'secret123');
    $a = buy($u, 'm1', 'bazaar', 'tok-r1', $storeKey);
    $b = buy($u, 'm3', 'bazaar', 'tok-r2', $storeKey);
    check(Entitlements::summary((int)$u['id'])['days_left'] === 120, 'two stacked periods');
    $bazaar->answers['tok-r1'] = 'refunded';
    Db::update('purchases', ['next_check_at' => Clock::sql(Clock::now() - 1)], 'id = ?', [$a['purchase']['id']]);
    Purchases::recheck(20);
    check(Db::value('SELECT status FROM {purchases} WHERE id = ?', [$a['purchase']['id']]) === 'refunded', 'store refund detected by the recheck');
    check(Entitlements::summary((int)$u['id'])['days_left'] === 90, 'refunded month removed; the later period moves up');
    throws(fn() => Purchases::verify($u, receipt('bazaar', 'divan_pro_1m', 'tok-r1', '', $storeKey)), 'refunded', 'refunded receipt cannot be restored');
    // Reinstall: the app sends its owned purchases again.
    $res = Purchases::verify($u, receipt('bazaar', 'divan_pro_3m', 'tok-r2', '', $storeKey), true);
    check((int)$res['purchase']['id'] === (int)$b['purchase']['id'], 'restore finds the same purchase');
    Clock::$fixed += 91 * 86400;
    check(!Entitlements::summary((int)$u['id'])['pro'], 'Pro ends when the period expires');
    Clock::$fixed -= 91 * 86400;
});

scenario('offline certificate', function () use ($storeKey) {
    freshIp();
    Settings::set('single_device', false);
    [$u] = Users::register('cert_user', 'secret123');
    buy($u, 'm1', 'myket', 'tok-c1', $storeKey);
    $c = Entitlements::certificate(Users::byId((int)$u['id']), 'myket');
    [$body, $sig] = explode('.', $c['certificate']);
    $pub = openssl_pkey_get_public(Crypto::publicKeyPem());
    check(openssl_verify($body, Crypto::b64uDecode($sig), $pub, OPENSSL_ALGO_SHA256) === 1, 'certificate verifies with the public key only');
    $p = json_decode(Crypto::b64uDecode($body), true);
    check($p['pro'] === true && $p['uid'] === (int)$u['id'] && $p['kid'] === Crypto::keyId(), 'certificate names the user and key');
    check($p['offline_until'] <= $p['exp'], 'timed plan: offline validity never passes the paid end');
    check($p['offline_until'] <= Clock::now() + 21 * 86400, 'offline window is at most 21 days');
    $tampered = Crypto::b64u(str_replace('"pro":true', '"pro":true,"lifetime":true', Crypto::b64uDecode($body)));
    check(openssl_verify($tampered, Crypto::b64uDecode($sig), $pub, OPENSSL_ALGO_SHA256) !== 1, 'edited certificate fails verification');
    buy($u, 'life', 'myket', 'tok-c2', $storeKey);
    $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate(Users::byId((int)$u['id']), 'myket')['certificate'])[0]), true);
    check($p['lifetime'] && $p['offline_until'] >= Clock::now() + 364 * 86400, 'lifetime works offline for a year');
    [$f] = Users::register('free_user', 'secret123');
    $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate($f)['certificate'])[0]), true);
    check($p['pro'] === false, 'free user certificate says not Pro');
    Settings::set('single_device', true);
});

scenario('one phone per subscription', function () use ($storeKey) {
    freshIp();
    $phoneA = hash('sha256', 'phone-a'); $phoneB = hash('sha256', 'phone-b');
    [$u] = Users::register('one_phone', 'secret123');
    $tA = Sessions::create((int)$u['id'], ['device' => 'Samsung A54', 'device_id' => $phoneA]);
    $tB = Sessions::create((int)$u['id'], ['device' => 'Xiaomi Note 12', 'device_id' => $phoneB]);
    $sA = Db::one('SELECT * FROM {sessions} WHERE token_hash = ?', [Crypto::hash($tA)]);
    $sB = Db::one('SELECT * FROM {sessions} WHERE token_hash = ?', [Crypto::hash($tB)]);
    check($sA['device_id'] === $phoneA, 'session remembers its phone');
    buy($u, 'm1', 'bazaar', 'tok-dev-1', $storeKey);
    $cert = fn(array $s) => json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate(Users::byId((int)$u['id']), 'bazaar', $s)['certificate'])[0]), true);
    $a = $cert($sA);
    check($a['pro'] === true && $a['device'] === $phoneA, 'first phone gets Pro, signed for that phone');
    $b = $cert($sB);
    check($b['pro'] === false && $b['device_conflict']['name'] === 'Samsung A54', 'second phone: not Pro, told where it is active');
    check($a['offline_until'] <= Clock::now() + 7 * 86400, 'timed plan with one-phone rule must check in within 7 days');
    throws(fn() => Devices::transfer(Users::byId((int)$u['id']), $sB, 'wrong123'), 'bad_credentials', 'transfer needs the password');
    Devices::transfer(Users::byId((int)$u['id']), $sB, 'secret123');
    check($cert($sB)['pro'] === true && $cert($sA)['pro'] === false, 'after transfer only the new phone is Pro');
    Devices::transfer(Users::byId((int)$u['id']), $sA, 'secret123');
    Devices::transfer(Users::byId((int)$u['id']), $sB, 'secret123');
    throws(fn() => Devices::transfer(Users::byId((int)$u['id']), $sA, 'secret123'), 'transfer_limit', 'at most 3 transfers in 30 days');
    check((int)Db::value("SELECT COUNT(*) FROM {security_events} WHERE kind = 'device_transfer' AND user_id = ?", [$u['id']]) === 3, 'transfers are logged');
    Clock::$fixed += 31 * 86400;
    Devices::transfer(Users::byId((int)$u['id']), $sA, 'secret123');
    check(Users::byId((int)$u['id'])['pro_device'] === $phoneA, 'transfer limit resets after 30 days');
    Clock::$fixed -= 31 * 86400;
    // Support releases the phone: the next phone that syncs takes the subscription.
    Db::update('users', ['pro_device' => null], 'id = ?', [$u['id']]);
    check($cert($sB)['pro'] === true && $cert($sA)['pro'] === false, 'released subscription binds to the next phone');
    // Buying on a phone moves the subscription there.
    [$w] = Users::register('two_phone', 'secret123');
    $tC = Sessions::create((int)$w['id'], ['device' => 'C', 'device_id' => hash('sha256', 'c')]);
    $tD = Sessions::create((int)$w['id'], ['device' => 'D', 'device_id' => hash('sha256', 'd')]);
    $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $tC; Api::reset();
    $i = apiPost('v1/purchases/intent', ['plan' => 'm1', 'store' => 'myket']);
    $r = apiPost('v1/purchases/verify', receipt('myket', $i['sku'], 'tok-dev-c', $i['payload'], $storeKey) + ['store' => 'myket']);
    check($r['entitlement']['this_device'] === true, 'purchase phone holds the subscription');
    $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $tD;
    $me = apiGet('v1/me');
    check($me['entitlement']['this_device'] === false && $me['entitlement']['device_conflict']['name'] === 'C', 'other phone sees where it is active');
    $t = apiPost('v1/devices/transfer', ['password' => 'secret123']);
    check($t['entitlement']['this_device'] === true, 'transfer through the API');
    unset($_SERVER['HTTP_AUTHORIZATION']);
    Settings::set('single_device', false);
    $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $tC;
    check(apiGet('v1/me')['entitlement']['this_device'] === true, 'rule can be switched off in the panel');
    unset($_SERVER['HTTP_AUTHORIZATION']);
    Settings::set('single_device', true);
});

scenario('cross-store setting', function () use ($storeKey) {
    freshIp();
    [$u] = Users::register('cross_user', 'secret123');
    buy($u, 'm1', 'bazaar', 'tok-x1', $storeKey);
    check(Entitlements::summary((int)$u['id'], 'myket')['pro'], 'with cross-store on, a Bazaar purchase counts in the Myket app');
    Settings::set('cross_store', false);
    check(!Entitlements::summary((int)$u['id'], 'myket')['pro'] && Entitlements::summary((int)$u['id'], 'bazaar')['pro'], 'with cross-store off, it counts only in Bazaar');
    Settings::set('cross_store', true);
});

scenario('features, gifts and ads', function () use ($storeKey) {
    freshIp();
    [$free] = Users::register('feat_free', 'secret123');
    [$pro] = Users::register('feat_pro', 'secret123');
    buy($pro, 'm1', 'bazaar', 'tok-f1', $storeKey);
    $all = Features::forUser((int)$free['id'], false);
    check(count($all) === 27 && !in_array(false, array_column($all, 'allowed'), true), 'all 27 modules free by default');
    // Single items as Pro: two mafia scenarios and one last-move card.
    Db::update('features', ['pro_items' => 'godfather,zodiac'], 'k = ?', ['mafia_scenarios']);
    Db::update('features', ['pro_items' => 'INTELLIGENCE'], 'k = ?', ['mafia_last_cards']);
    $fi = Features::forUser((int)$free['id'], false);
    check($fi['mafia_scenarios']['pro_items'] === ['godfather', 'zodiac'] && $fi['mafia_scenarios']['allowed'], 'Pro-only scenarios listed for a free user; the module stays open');
    check(Features::forUser((int)$pro['id'], true)['mafia_scenarios']['pro_items'] === [], 'Pro user has no locked items');
    check($fi['mafia_last_cards']['pro_items'] === ['INTELLIGENCE'], 'single last-move card as Pro');
    check(count(Catalog::items('mafia_scenarios')) === 48 && isset(Catalog::items('mafia_last_cards')['INTELLIGENCE']) && isset(Catalog::items('themes')['emerald']), 'item catalog from the app');
    Db::run("UPDATE {features} SET pro_items = NULL");
    Db::update('features', ['mode' => 'pro'], 'k = ?', ['cloud_backup']);
    Db::update('features', ['mode' => 'limited', 'limit_value' => 2], 'k = ?', ['leagues']);
    Db::update('features', ['mode' => 'gift'], 'k = ?', ['share']);
    $f = Features::forUser((int)$free['id'], false);
    $p = Features::forUser((int)$pro['id'], true);
    check(!$f['cloud_backup']['allowed'] && $p['cloud_backup']['allowed'], 'Pro-only module');
    check($f['leagues']['limit'] === 2 && $p['leagues']['limit'] === null, 'limited module: limit for free, none for Pro');
    check(!$f['share']['allowed'], 'gift-only module closed without a gift');
    Db::insert('user_grants', ['user_id' => $free['id'], 'feature_k' => 'share', 'expires_at' => null, 'reason' => 'test', 'created_at' => Clock::sql()]);
    check(Features::forUser((int)$free['id'], false)['share']['allowed'], 'gift opens the module');
    Db::insert('user_grants', ['user_id' => $free['id'], 'feature_k' => 'pro', 'expires_at' => Clock::sql(Clock::now() + 7 * 86400), 'reason' => 'test', 'created_at' => Clock::sql()]);
    check(Entitlements::summary((int)$free['id'])['pro'] && Entitlements::summary((int)$free['id'])['days_left'] === 7, '7-day Pro gift');
    Db::run("UPDATE {features} SET mode = 'free', limit_value = NULL");
    // Ads
    check(Ads::configFor('bazaar', false) === ['enabled' => false], 'ads off by default');
    Settings::set('ads_enabled', true);
    check(Ads::configFor('bazaar', true) === ['enabled' => false], 'Pro receives no ad configuration at all');
    $id = Db::insert('ads', ['name' => 'a', 'format' => 'card', 'placements' => 'home_inline', 'stores' => 'myket', 'active' => 1, 'total_cap' => 5, 'created_at' => Clock::sql(), 'updated_at' => Clock::sql()]);
    check(count(Ads::configFor('bazaar', false)['ads']) === 0 && count(Ads::configFor('myket', false)['ads']) === 1, 'store filter');
    $n = Ads::record([['event' => 'impression', 'ad_id' => $id, 'placement' => 'home_inline', 'count' => 5], ['event' => 'click', 'ad_id' => $id, 'placement' => 'home_inline'], ['event' => 'impression', 'placement' => 'mafia_night'], ['event' => 'hack', 'placement' => 'home_inline']]);
    check($n === 2, 'unknown events and unsafe placements ignored');
    check(count(Ads::configFor('myket', false)['ads']) === 0, 'total cap stops the ad');
    // Fixed bars: off by default, on for chosen pages only.
    $cfgSticky = fn() => array_column(Ads::configFor('myket', false)['placements'], null, 'key');
    $st = $cfgSticky();
    check(isset($st['sticky_bottom'], $st['sticky_top']) && !$st['sticky_bottom']['active'] && in_array('home', $st['sticky_bottom']['screens'], true), 'fixed bars exist, off by default, with page list');
    check(!array_intersect($st['sticky_bottom']['screens'], ['game', 'board', 'timer', 'mafia_night', 'vote', 'scoring']), 'fixed bars never on game pages');
    check(safe_target_url('divan://pro') && safe_target_url('divan://plan/m3') && !safe_target_url('divan://evil/x') && !safe_target_url('javascript:x'), 'ad buttons may open the Pro page or a plan in the app');
    $placements = array_column(Ads::placements(), 'screen');
    check(!array_intersect($placements, ['game', 'timer', 'mafia', 'reveal', 'scoring']), 'no placement on game, timer, mafia or scoring screens');
    check(Ads::configFor('myket', false)['network']['test'] === true, 'network test flag reaches the app');
    Settings::set('ads_enabled', false);
});

scenario('media upload checks', function () {
    $png = tempnam(sys_get_temp_dir(), 'png');
    $im = imagecreatetruecolor(20, 10);
    imagepng($im, $png);
    $id = Media::store(['name' => 'b.png', 'tmp_name' => $png, 'error' => UPLOAD_ERR_OK]);
    $m = Db::one('SELECT * FROM {media} WHERE id = ?', [$id]);
    check($m['mime'] === 'image/png' && (int)$m['width'] === 20 && preg_match('/^\d{4}\/\d{2}\/[0-9a-f]{24}\.png$/', $m['path']) === 1, 'image stored with a random name');
    $php = tempnam(sys_get_temp_dir(), 'php');
    file_put_contents($php, "<?php echo 'x';");
    throws(fn() => Media::store(['name' => 'evil.png', 'tmp_name' => $php, 'error' => UPLOAD_ERR_OK]), 'upload_type', 'PHP disguised as an image rejected');
    check(safe_target_url('https://example.ir/x') && !safe_target_url('javascript:alert(1)') && !safe_target_url('http://127.0.0.1/admin') && !safe_target_url('https://user:pw@example.ir'), 'ad target URLs are checked');
});

scenario('support and cloud backup of game data', function () {
    freshIp();
    $id = Support::create(null, ['kind' => 'recovery', 'username' => 'lost', 'message' => 'رمز و کلید را گم کرده‌ام', 'order_id' => 'ORD-1']);
    check($id > 0, 'support request without login');
    for ($i = 0; $i < 5; $i++) Support::create(null, ['message' => 'یک پیام آزمایشی کامل']);
    throws(fn() => Support::create(null, ['message' => 'یک پیام آزمایشی کامل']), 'rate_limited', 'support spam limited');
    throws(fn() => Support::action($id, null, 1, 'reset', ' '), 'reason', 'support action needs a reason');
    [$u] = Users::register('cloud_user', 'secret123');
    UserData::put((int)$u['id'], '{"games":[1,2,3]}');
    check(UserData::get((int)$u['id'])['data'] === '{"games":[1,2,3]}', 'game backup round trip');
    check(strpos((string)Db::value('SELECT data_enc FROM {user_backups} WHERE user_id = ?', [$u['id']]), 'games') === false, 'game backup encrypted at rest');
});

scenario('backup and safe restore', function () use ($storeKey) {
    freshIp();
    $b = Backup::create('manual');
    $blob = file_get_contents(Backup::dir() . '/' . $b['file']);
    check(substr($blob, 0, 8) === 'DIVANBK1' && strpos($blob, 'buyer1') === false, 'backup is encrypted');
    $victim = Users::byName('buyer6');
    Db::run('DELETE FROM {users} WHERE id = ?', [$victim['id']]);
    // A purchase made after the backup must survive the restore.
    [$late] = Users::register('late_buyer', 'secret123');
    $r = buy($late, 'm1', 'myket', 'tok-late', $storeKey);
    Db::update('plans', ['title' => 'عنوان تازه'], 'code = ?', ['m1']);
    $added = Backup::restore($blob);
    check(Users::byName('buyer6') !== null, 'deleted account restored');
    check(Db::value('SELECT status FROM {purchases} WHERE id = ?', [$r['purchase']['id']]) === 'verified', 'newer purchase untouched');
    check(Plans::byCode('m1')['title'] === 'عنوان تازه', 'newer edits are not overwritten');
    check(($added['users'] ?? 0) === 1, 'only the missing row was added');
    throws(fn() => Backup::restore($blob, false, base64_encode(random_bytes(32))), 'bad_backup', 'wrong backup key cannot open the file');
    throws(fn() => Backup::restore($blob, true), 'not_fresh', 'keys are adopted only on a fresh install');
});


scenario('discount and gift codes', function () use ($storeKey, $bazaar) {
    freshIp();
    [$a] = Users::register('code_a', 'secret123');
    [$b] = Users::register('code_b', 'secret123');
    $mk = fn(array $row) => Db::insert('promo_codes', $row + ['active' => 1, 'uses' => 0, 'created_at' => Clock::sql()]);
    $mk(['code' => 'GIFT30', 'kind' => 'gift_pro', 'days' => 30, 'max_uses' => 1]);
    $p = Promo::redeemGift(' gift-30 ', $a);
    check(Entitlements::summary((int)$a['id'])['pro'] && Entitlements::summary((int)$a['id'])['days_left'] === 30, 'gift code gives 30 days of Pro (code typed loosely)');
    throws(fn() => Promo::redeemGift('GIFT30', $a), 'promo_used', 'same account cannot use a code twice');
    throws(fn() => Promo::redeemGift('GIFT30', $b), 'promo_used_up', 'code with one use is used up');
    throws(fn() => Promo::redeemGift('NOPE99', $b), 'promo_invalid', 'unknown code rejected');
    $mk(['code' => 'ONLYA', 'kind' => 'gift_pro', 'days' => null, 'users' => (string)$a['id']]);
    throws(fn() => Promo::redeemGift('ONLYA', $b), 'promo_not_yours', 'code limited to chosen accounts');
    $mk(['code' => 'OLD1', 'kind' => 'gift_pro', 'days' => 5, 'ends_at' => Clock::sql(Clock::now() - 10)]);
    throws(fn() => Promo::redeemGift('OLD1', $b), 'promo_expired', 'expired code rejected');
    $mk(['code' => 'CARDS', 'kind' => 'gift_feature', 'feature_k' => 'mafia_last_cards', 'days' => 7]);
    Db::update('features', ['mode' => 'pro'], 'k = ?', ['mafia_last_cards']);
    Promo::redeemGift('CARDS', $b);
    check(Features::forUser((int)$b['id'], false)['mafia_last_cards']['allowed'] && !Entitlements::summary((int)$b['id'])['pro'], 'gift code for one module opens just that module');
    Db::update('features', ['mode' => 'free'], 'k = ?', ['mafia_last_cards']);
    // Discount: Bazaar only, through the signed dynamic price.
    $mk(['code' => 'OFF20', 'kind' => 'discount', 'percent' => 20, 'plan_code' => 'm3']);
    throws(fn() => Purchases::intent($b, 'm3', 'bazaar', 'OFF20'), 'promo_store', 'discount needs the Bazaar dynamic-price key');
    Settings::setSecret('bazaar_discount_key', 'k3y-for-dynamic-pricing');
    throws(fn() => Purchases::intent($b, 'm3', 'myket', 'OFF20'), 'promo_store', 'no discount codes in Myket');
    throws(fn() => Purchases::intent($b, 'm1', 'bazaar', 'OFF20'), 'promo_other_plan', 'discount for another plan rejected');
    throws(fn() => Promo::redeemGift('OFF20', $b), 'promo_is_discount', 'discount code is not a gift');
    $i = Purchases::intent($b, 'm3', 'bazaar', 'OFF20');
    $claims = json_decode(Crypto::b64uDecode(explode('.', (string)$i['dynamic_price_token'])[1]), true);
    check($i['price'] === 39200 && $claims['price'] === 392000, 'discounted price 49,000 → 39,200 toman, signed for Bazaar in rials');
    $bazaar->answers['tok-off'] = 'valid';
    $r = Purchases::verify($b, receipt('bazaar', $i['sku'], 'tok-off', $i['payload'], $storeKey));
    check((int)$r['purchase']['amount_toman'] === 39200, 'purchase recorded at the discounted price');
    check((int)Db::value("SELECT uses FROM {promo_codes} WHERE code = 'OFF20'") === 1, 'discount use counted after the purchase is confirmed');
    throws(fn() => Purchases::intent($b, 'm3', 'bazaar', 'OFF20'), 'promo_used', 'discount used once per account');
    Settings::setSecret('bazaar_discount_key', '');
});

scenario('API end to end', function () use ($storeKey, $myket) {
    freshIp();
    $cfg = apiGet('v1/config', ['store' => 'myket']);
    check(count($cfg['plans']) === 4 && $cfg['ads'] === ['enabled' => false], 'config without account');
    $reg = apiPost('v1/auth/register', ['username' => 'api_user', 'password' => 'secret123', 'device' => 'Pixel', 'store' => 'myket']);
    check(isset($reg['token'], $reg['recovery_key'], $reg['certificate']) && $reg['entitlement']['pro'] === false, 'register returns token, key and certificate');
    $tok = $reg['token'];
    $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $tok;
    $intent = apiPost('v1/purchases/intent', ['plan' => 'm6', 'store' => 'myket']);
    $myket->answers['tok-api'] = 'valid';
    $v = apiPost('v1/purchases/verify', receipt('myket', $intent['sku'], 'tok-api', $intent['payload'], $storeKey));
    check($v['status'] === 'verified' && $v['consume'] === true && $v['entitlement']['pro'] === true && $v['entitlement']['days_left'] === 180, 'verify through the API grants 180 days');
    $me = apiGet('v1/me');
    check(count($me['sessions']) === 1 && count($me['purchases']) === 1, 'me lists sessions and purchases');
    $rest = apiPost('v1/purchases/restore', ['store' => 'myket', 'purchases' => [receipt('myket', 'divan_pro_6m', 'tok-api', '', $storeKey), ['sku' => 'x', 'token' => 'y']]]);
    check($rest['results'][0]['status'] === 'verified' && $rest['results'][1]['status'] === 'error', 'restore reports each receipt');
    apiPost('v1/auth/logout', []);
    unset($_SERVER['HTTP_AUTHORIZATION']);
    $_SERVER['HTTP_AUTHORIZATION'] = 'Bearer ' . $tok;
    throws(fn() => apiGet('v1/me'), 'unauthorized', 'token revoked by logout');
    unset($_SERVER['HTTP_AUTHORIZATION']);
    throws(fn() => apiGet('v1/nothing'), 'not_found', 'unknown route');
});

// ---------------------------------------------------------------- report
echo "\n" . ($failed ? "FAILED " . count($failed) . " of " . ($passed + count($failed)) . " checks\n" . implode("\n", $failed) . "\n" : "ALL $passed CHECKS PASSED\n");
foreach ($pdo->query("SHOW TABLES LIKE 'dvt\\_%'")->fetchAll(PDO::FETCH_COLUMN) as $t) $pdo->exec("DROP TABLE `$t`");
exit($failed ? 1 : 0);
