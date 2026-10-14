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
require $src . '/app/payments.php';
require $src . '/app/paypages.php';
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

scenario('plans: separate store prices, list price and Bazaar campaigns', function () {
    freshIp();
    [$u] = Users::register('buyer0', 'secret123');
    // 5.3: a plan with a SKU and a price can be bought right away; no price sync step.
    foreach (Plans::offers('bazaar') as $o) check($o['state'] === 'ok' && $o['purchasable'], "plan {$o['code']} purchasable with its store price");
    $offers = array_column(Plans::offers('myket'), null, 'code');
    check($offers['m1']['final_price'] === 19000 && $offers['m3']['final_price'] === 49000 && $offers['m6']['final_price'] === 89000 && $offers['life']['final_price'] === 220000, 'default prices 19/49/89/220 thousand toman');
    check($offers['m1']['purchasable'] && $offers['life']['lifetime'], 'Myket plans purchasable');
    $plan = Plans::byCode('m3');
    // Each store has its own price and list (struck-through) price.
    Db::update('plans', ['store_price_myket' => 45000, 'list_price_myket' => 60000], 'id = ?', [$plan['id']]);
    $m = Plans::offer(Plans::byCode('m3'), 'myket'); $b = Plans::offer(Plans::byCode('m3'), 'bazaar');
    check($m['final_price'] === 45000 && $m['base_price'] === 60000 && $m['discount_percent'] === 25, 'Myket price with a struck-through list price');
    check($b['final_price'] === 49000 && $b['discount_percent'] === 0, 'Bazaar keeps its own price');
    Db::update('plans', ['on_myket' => 0], 'id = ?', [$plan['id']]);
    check(!in_array('m3', array_column(Plans::offers('myket'), 'code'), true) && in_array('m3', array_column(Plans::offers('bazaar'), 'code'), true), 'a plan can be offered in one store only');
    throws(fn() => Purchases::intent($u, 'm3', 'myket'), 'not_purchasable', 'a plan switched off in Myket cannot be bought there');
    Db::update('plans', ['on_myket' => 1, 'store_price_myket' => 49000, 'list_price_myket' => null], 'id = ?', [$plan['id']]);
    Db::update('plans', ['sku_myket' => null], 'id = ?', [$plan['id']]);
    check(Plans::offer(Plans::byCode('m3'), 'myket')['state'] === 'no_sku', 'a plan without a product id is not purchasable');
    Db::update('plans', ['sku_myket' => 'divan_pro_3m'], 'id = ?', [$plan['id']]);
    $cid = Db::insert('campaigns', ['name' => 'نوروز', 'plan_id' => $plan['id'], 'store' => 'bazaar', 'price_toman' => 39000, 'label' => 'نوروزی', 'starts_at' => Clock::sql(Clock::now() - 60), 'ends_at' => Clock::sql(Clock::now() + 86400), 'active' => 1, 'created_at' => Clock::sql(), 'updated_at' => Clock::sql()]);
    $b = Plans::offer(Plans::byCode('m3'), 'bazaar');
    check($b['final_price'] === 49000 && !$b['dynamic_price'] && $b['purchasable'], 'without the dynamic-price key a campaign never shows a price the store would not charge');
    Settings::setSecret('bazaar_discount_key', 'k3y-for-dynamic-pricing');
    $b = Plans::offer(Plans::byCode('m3'), 'bazaar');
    check($b['state'] === 'ok' && $b['dynamic_price'] && $b['final_price'] === 39000 && $b['discount_percent'] === 20, 'Bazaar dynamic discount applies the campaign');
    check(Plans::offer(Plans::byCode('m3'), 'myket')['final_price'] === 49000, 'a Bazaar campaign does not change Myket');
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
    check($p['lifetime'] && $p['offline_until'] === Entitlements::LIFETIME_OFFLINE && $p['exp'] === null, 'lifetime: no end date and no offline limit');
    Settings::set('single_device', true);
    $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate(Users::byId((int)$u['id']), 'myket', ['device_id' => 'phone-a'])['certificate'])[0]), true);
    check($p['lifetime'] && $p['offline_until'] === Entitlements::LIFETIME_OFFLINE && $p['device'] === 'phone-a', 'lifetime with one-phone rule: still no offline limit, bound to its phone');
    Settings::set('single_device', false);
    [$f] = Users::register('free_user', 'secret123');
    $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate($f)['certificate'])[0]), true);
    check($p['pro'] === false, 'free user certificate says not Pro');
    // Timed passes end exactly at their end; grace only for a store subscription that renews itself.
    foreach (['m1' => 30, 'm3' => 90, 'm6' => 180] as $code => $days) {
        [$t] = Users::register('cert_' . $code, 'secret123');
        buy($t, $code, 'bazaar', 'tok-ce-' . $code, $storeKey);
        $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate(Users::byId((int)$t['id']), 'bazaar')['certificate'])[0]), true);
        check($p['exp'] === Clock::now() + $days * 86400 && $p['grace'] === $p['exp'] && $p['offline_until'] <= $p['exp'], "$days-day pass: exact end, no grace, offline never past the end");
    }
    $last = Users::byId((int)$t['id']);
    $saved = Clock::$fixed;
    Clock::$fixed += 181 * 86400;
    $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate($last, 'bazaar')['certificate'])[0]), true);
    check($p['pro'] === false, 'after the end, going online does not extend anything');
    Clock::$fixed = $saved;
    Db::run('UPDATE {purchases} SET auto_renewing = 1 WHERE user_id = ?', [$last['id']]);
    $p = json_decode(Crypto::b64uDecode(explode('.', Entitlements::certificate($last, 'bazaar')['certificate'])[0]), true);
    check($p['grace'] === $p['exp'] + 3 * 86400, 'self-renewing store subscription: 3 days grace for the renewal');
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
    Db::update('features', ['mode' => 'pro', 'pro_items' => null], 'k = ?', ['themes']);
    $ft = Features::forUser((int)$free['id'], false)['themes'];
    check(!$ft['allowed'] && $ft['all_items'], 'a whole item module as Pro locks every item for a free user');
    check(!Features::forUser((int)$pro['id'], true)['themes']['all_items'], 'a Pro user has every item');
    Db::update('features', ['pro_items' => 'neon'], 'k = ?', ['themes']);
    $ft = Features::forUser((int)$free['id'], false)['themes'];
    check($ft['allowed'] && !$ft['all_items'] && $ft['items'] === ['neon'], 'ticked items only: the module stays open');
    Db::update('features', ['mode' => 'free', 'pro_items' => null], 'k = ?', ['themes']);
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

scenario('payment gateway in Iran: real HTTP, signed both ways, fake stores', function () use ($storeKey, $root) {
    freshIp();
    Http::$allowLocalHttpForTests = true;
    $savedStores = Stores::$override; Stores::$override = [];   // the real verifiers, routed through the gateway
    $port = 18000 + random_int(0, 999);
    $key = bin2hex(random_bytes(32));
    $bdata = "$root/bridge-data"; @mkdir($bdata, 0700, true);
    $cfgFile = "$root/bridge-cfg.json";
    file_put_contents($cfgFile, json_encode(['keys' => ['k1' => $key], 'allowed_ips' => ['127.0.0.1'], 'packages' => ['com.meysam.divanemtiaz'],
        'skus' => ['divan_pro_1m', 'divan_pro_3m', 'divan_pro_6m', 'divan_pro_lifetime'], 'myket_access_token' => 'MYKET-SECRET-123',
        'bazaar_api_secret' => 'BAZAAR-SECRET-456', 'rate_per_minute' => 1000, '__data' => $bdata]));
    $router = dirname(__DIR__, 2) . '/payment-bridge/divan-bridge/tests/http_router.php';
    $proc = null;
    $start = function () use (&$proc, $port, $router, $cfgFile) {
        $proc = proc_open([PHP_BINARY, '-S', "127.0.0.1:$port", $router], [0 => ['pipe', 'r'], 1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']], $pipes, null, ['DIVAN_BRIDGE_TEST_CFG' => $cfgFile] + getenv());
        for ($i = 0; $i < 50; $i++) { $s = @fsockopen('127.0.0.1', $port, $en, $es, 0.2); if ($s) { fclose($s); return; } usleep(100000); }
    };
    $stop = function () use (&$proc) { if ($proc) { proc_terminate($proc); proc_close($proc); $proc = null; usleep(200000); } };
    $calls = function () use ($cfgFile) { return is_file("$cfgFile.calls") ? count(file("$cfgFile.calls")) : 0; };
    $start();
    Settings::set('payment_bridge', ['url' => "http://127.0.0.1:$port/", 'key_id' => 'k1', 'route_myket' => 'bridge', 'route_bazaar' => 'bridge']);
    Settings::setSecret('bridge_key', $key);
    Settings::setSecret('myket_access_token', ''); Settings::setSecret('bazaar_api_secret', '');   // the Netherlands holds no store secret

    $p = PaymentBridge::call('ping');
    check($p['kind'] === 'pong' && $p['stores'] === ['myket' => true, 'bazaar' => true], 'Netherlands → gateway: signed ping, signed answer verified');
    check(strpos(json_encode($p), 'MYKET-SECRET') === false && strpos(json_encode($p), 'BAZAAR-SECRET') === false, 'no store secret travels back');
    check(PaymentBridge::testStore('myket')[0] === 'ok' && PaymentBridge::testStore('bazaar')[0] === 'ok', 'panel connection tests of both stores pass through the gateway');

    $reg = function (string $name) { freshIp(); return Users::register($name, 'secret123'); };
    $buy = function (array $u, string $plan, string $store, string $token) use ($storeKey) {
        $i = Purchases::intent($u, $plan, $store);
        return Purchases::verify($u, receipt($store, $i['sku'], $token, $i['payload'], $storeKey));
    };
    [$u] = $reg('bridge_buyer');
    $r = $buy($u, 'm1', 'myket', 'valid-mk-0001');
    $last = json_decode((string)array_slice(file("$cfgFile.calls"), -1)[0], true);
    check($r['purchase']['status'] === 'verified' && $r['purchase']['verify_method'] === 'store_api' && $r['consume'] === true, 'Myket purchase verified by the store through the gateway; consumable consumed after recording');
    check($last === ['GET', 'https://developer.myket.ir/api/applications/com.meysam.divanemtiaz/purchases/products/divan_pro_1m/tokens/valid-mk-0001'], 'gateway called the documented Myket verify endpoint');
    check(Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'one month of Pro');
    $buy($u, 'm3', 'myket', 'valid-mk-0002');
    check(Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 120 * 86400, 'renewal: three months added after the current month');
    $ents = (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE user_id = ?', [$u['id']]);
    $again = Purchases::verify($u, receipt('myket', 'divan_pro_1m', 'valid-mk-0001', '', $storeKey));
    check((int)$again['purchase']['id'] === (int)$r['purchase']['id'] && (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE user_id = ?', [$u['id']]) === $ents, 'same receipt again (retry): same purchase, no extra period');
    $rest = Purchases::verify($u, receipt('myket', 'divan_pro_3m', 'valid-mk-0002', '', $storeKey), true);
    check($rest['purchase']['status'] === 'verified' && (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE user_id = ?', [$u['id']]) === $ents, 'restore after reinstall keeps the purchase, adds nothing');
    [$u2] = $reg('bridge_other');
    throws(fn() => Purchases::verify($u2, receipt('myket', 'divan_pro_1m', 'valid-mk-0001', '', $storeKey)), 'owned_by_other', 'a receipt cannot serve a second account');
    throws(fn() => $buy($u2, 'm1', 'myket', 'refund-mk-0003'), 'rejected', 'Myket purchaseState=1 (unsuccessful, per Myket docs) rejected');
    throws(fn() => $buy($u2, 'm1', 'myket', 'cancel-mk-0004'), 'rejected', 'canceled payment (purchaseState=2) rejected');
    throws(fn() => $buy($u2, 'm1', 'myket', 'unknown-mk-0005'), 'rejected', 'fake receipt unknown to the store rejected');
    $n = $calls();
    $i = Purchases::intent($u2, 'm1', 'myket');
    throws(fn() => Purchases::verify($u2, receipt('myket', 'divan_pro_1m', 'valid-mk-0006', $i['payload'], $storeKey, true)), 'bad_signature', 'forged receipt signature refused');
    check($calls() === $n, '…before anything is sent to the gateway');
    check(!Entitlements::summary((int)$u2['id'])['pro'], 'no Pro from refused receipts');
    foreach (['denied-mk-0007' => 'store 401', 'forbid-mk-0008' => 'store 403', 'slow-mk-0009' => 'store timeout', 'down-mk-0010' => 'store 503'] as $tok => $what) {
        [$ux] = Users::register('bx' . substr(md5($tok), 0, 8), 'secret123');
        $rx = $buy($ux, 'm1', 'myket', $tok);
        check($rx['purchase']['status'] === 'signature_verified' && $rx['purchase']['verify_method'] === 'store_signature' && $rx['purchase']['next_check_at'] !== null, "$what: never «verified»; kept for recheck under the current signature policy");
    }
    // Bazaar through the gateway: lifetime and an official Bazaar subscription.
    [$u3] = $reg('bridge_bazaar');
    $r = $buy($u3, 'life', 'bazaar', 'valid-bz-0011');
    check($r['purchase']['status'] === 'verified' && $r['consume'] === false && Entitlements::summary((int)$u3['id'])['lifetime'], 'Bazaar lifetime: verified, never consumed, Pro for good');
    throws(fn() => $buy($u3, 'life', 'bazaar', 'valid-bz-0012'), 'already_lifetime', 'lifetime not sold twice');
    [$u4] = $reg('bridge_sub');
    $m1 = Plans::byCode('m1');
    Db::update('plans', ['type_bazaar' => 'subscription'], 'id = ?', [$m1['id']]);
    $r = $buy($u4, 'm1', 'bazaar', 'sub-bz-0013');
    $last = json_decode((string)array_slice(file("$cfgFile.calls"), -1)[0], true);
    check($r['purchase']['status'] === 'verified' && $r['purchase']['auto_renewing'] == 1 && Entitlements::summary((int)$u4['id'])['expires_at'] === 1800000000 + 30 * 86400, 'Bazaar subscription: end date from the store');
    check($last[1] === 'https://pardakht.cafebazaar.ir/devapi/v2/api/applications/com.meysam.divanemtiaz/subscriptions/divan_pro_1m/purchases/sub-bz-0013', 'official Bazaar subscription endpoint');
    Db::update('plans', ['type_bazaar' => 'consumable'], 'id = ?', [$m1['id']]);

    // The gateway is down: nothing is approved; once it is back, the recheck confirms the purchase.
    $stop();
    [$u5] = $reg('bridge_down');
    $r = $buy($u5, 'm1', 'myket', 'valid-mk-0020');
    $st = (array)Settings::get('bridge_status', []);
    check($r['purchase']['status'] === 'signature_verified' && $st['last_kind'] === 'bridge_unreachable', 'gateway down: purchase kept (signature only), error shown in the panel');
    $start();
    Clock::$fixed += 400;
    Purchases::recheck(50);
    check(Db::value('SELECT status FROM {purchases} WHERE id = ?', [$r['purchase']['id']]) === 'verified', 'gateway back: recheck confirms with the store');
    Clock::$fixed -= 400;

    // Wrong key, unknown key id, a forged answer.
    Settings::setSecret('bridge_key', bin2hex(random_bytes(32)));
    $w = PaymentBridge::call('ping');
    check($w['kind'] === 'bridge_auth', 'wrong shared key: refused by the gateway, reported as bridge_auth');
    Settings::setSecret('bridge_key', $key);
    check(PaymentBridge::call('ping', [], 'k9')['kind'] === 'bridge_auth', 'unknown key id refused');
    touch("$cfgFile.tamper");
    [$u6] = $reg('bridge_mitm');
    $r = $buy($u6, 'm1', 'myket', 'refund-mk-0021');
    check($r['purchase']['status'] !== 'verified' && ((array)Settings::get('bridge_status', []))['last_kind'] === 'bridge_bad_response', 'answer changed in transit (refund → paid): signature check rejects it');
    unlink("$cfgFile.tamper");
    // Only fixed actions: nothing like a URL can be passed through.
    check(PaymentBridge::call('fetch', ['url' => 'https://example.com/'])['kind'] === 'bad_request', 'unknown action refused by the gateway (no open proxy)');
    check(PaymentBridge::call('myket.verify', ['package' => 'com.evil.app', 'sku' => 'divan_pro_1m', 'token' => 'valid-x-0001'])['kind'] === 'bad_request', 'foreign package refused');

    // Several requests at the same time for one purchase: one row, one period.
    [$u7] = $reg('bridge_race');
    $i = Purchases::intent($u7, 'm1', 'myket');
    $rf = "$root/race.json";
    file_put_contents($rf, json_encode(receipt('myket', $i['sku'], 'slowvalid-race-0001', $i['payload'], $storeKey)));
    $procs = [];
    for ($k = 0; $k < 4; $k++) {
        $pipes = [];
        $pr = proc_open([PHP_BINARY, __DIR__ . '/race_child.php', $root, $rf, (string)$u7['id'], (string)Clock::now()], [1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pipes);
        $procs[] = [$pr, $pipes];
    }
    $outs = [];
    foreach ($procs as [$pr, $pipes]) { $outs[] = json_decode((string)stream_get_contents($pipes[1]), true); stream_get_contents($pipes[2]); fclose($pipes[1]); fclose($pipes[2]); proc_close($pr); }
    $ids = array_unique(array_map(fn($o) => is_array($o) ? ($o['id'] ?? null) : null, $outs));
    $pid = (int)Db::value('SELECT id FROM {purchases} WHERE store = ? AND token_hash = ?', ['myket', Crypto::hash('myket|slowvalid-race-0001')]);
    check(count(array_filter($outs, fn($o) => is_array($o) && $o['ok'] && $o['status'] === 'verified')) === 4 && count($ids) === 1, '4 simultaneous requests for one purchase: all answered with the same purchase');
    check((int)Db::value('SELECT COUNT(*) FROM {purchases} WHERE store = ? AND token_hash = ?', ['myket', Crypto::hash('myket|slowvalid-race-0001')]) === 1
        && (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE purchase_id = ?', [$pid]) === 1
        && Entitlements::summary((int)$u7['id'])['expires_at'] === Clock::now() + 30 * 86400, '…one purchase row, one 30-day period');

    // Nothing secret in the gateway log or the panel status.
    $log = '';
    foreach (glob("$bdata/logs/*.log") ?: [] as $f) $log .= file_get_contents($f);
    check($log !== '' && strpos($log, 'MYKET-SECRET') === false && strpos($log, 'BAZAAR-SECRET') === false && strpos($log, $key) === false && strpos($log, 'valid-mk-0001') === false, 'gateway log: no key, store secret or purchase token');
    $st = json_encode(Settings::get('bridge_status', []));
    check(strpos($st, $key) === false && strpos($st, 'SECRET') === false, 'panel status holds no secret');
    $stop();
    Stores::$override = $savedStores;
    Http::$allowLocalHttpForTests = false;
});

scenario('promotional notifications: consent, Pro, once per phone, daily cap', function () use ($storeKey) {
    freshIp();
    $dev = str_repeat('ab', 32); $dev2 = str_repeat('cd', 32);
    $mk = function (array $o = []) {
        return Db::insert('notices', $o + ['title' => 'تخفیف', 'body' => 'متن', 'audience' => 'free', 'stores' => 'bazaar,myket', 'active' => 1, 'created_at' => Clock::sql(), 'updated_at' => Clock::sql()]);
    };
    $a = $mk();
    $due = Notices::due($dev, 'bazaar', false, false);
    check(count($due) === 1 && $due[0]['id'] === $a && $due[0]['image'] === null, 'a free player gets the campaign');
    check(Notices::due($dev, 'bazaar', true, false) === [], 'a Pro player gets nothing without opting in');
    check(Notices::due($dev, 'bazaar', true, true) === [], '…and with opt-in only campaigns meant for everyone');
    check(Notices::due($dev, 'direct', false, false) === [], 'only the chosen app versions');
    check(Notices::due('../../etc', 'bazaar', false, false) === [], 'bad device id ignored');
    Notices::event($a, $dev, 'shown'); Notices::event($a, $dev, 'shown');
    check(Notices::due($dev, 'bazaar', false, false) === [], 'shown once per phone; a second report changes nothing');
    check((int)Db::value('SELECT shown FROM {notices} WHERE id = ?', [$a]) === 1, 'counted once');
    Notices::event($a, $dev, 'opened'); Notices::event($a, $dev, 'opened');
    check((int)Db::value('SELECT opened FROM {notices} WHERE id = ?', [$a]) === 1, 'opened counted once');
    $b = $mk(['audience' => 'all']);
    check(Notices::due($dev, 'bazaar', false, false) === [], 'daily cap (1 per 24 h by default)');
    check(Notices::due($dev, 'bazaar', true, true) === [], 'cap applies to Pro opt-in too');
    Settings::set('notices_daily_cap', 2);
    check(Notices::due($dev, 'bazaar', true, true)[0]['id'] === $b, 'Pro player who opted in gets a campaign for everyone');
    Settings::set('notices_daily_cap', 1);
    $c = $mk(['send_at' => Clock::sql(Clock::now() + 3600)]);
    $d = $mk(['ends_at' => Clock::sql(Clock::now() - 1)]);
    $e = $mk(); Db::update('notices', ['canceled_at' => Clock::sql(), 'active' => 0], 'id = ?', [$e]);
    $ids = array_column(Notices::due($dev2, 'bazaar', false, false), 'id');
    check($ids === [$a], 'scheduled, ended and canceled campaigns are not sent');
    Settings::set('notices_enabled', false);
    check(Notices::due($dev2, 'bazaar', false, false) === [], 'master switch stops everything');
    Settings::set('notices_enabled', true);
    check(Notices::validTarget('divan://pro') && Notices::validTarget('https://httpuser87890.ir/x') && !Notices::validTarget('javascript:alert(1)') && !Notices::validTarget('http://x.ir') && !Notices::validTarget('divan://evil'), 'only in-app targets or https links');
    throws(fn() => Notices::event($a, $dev, 'hack'), 'bad_event', 'unknown event refused');
    $w = $mk(['target' => 'https://example.org/offer', 'stores' => 'bazaar,myket,direct']);
    $dev3 = str_repeat('ef', 32);
    check(Notices::due($dev3, 'bazaar', false, false)[0]['id'] === $a && !in_array($w, array_column(Notices::due($dev3, 'myket', false, false), 'id'), true), 'store versions never get a web link (store ad rules)');
    Db::update('notices', ['active' => 0], 'id = ?', [$a]);
    check(Notices::due($dev3, 'direct', false, false)[0]['id'] === $w, 'web link only in the site version');
    Db::update('notices', ['active' => 1], 'id = ?', [$a]);
    Db::update('notices', ['active' => 0], 'id = ?', [$w]);
    $r = apiGet('v1/notices', ['device' => $dev2, 'store' => 'myket']);
    check(count($r['notices']) === 1 && $r['notices'][0]['id'] === $a, 'API returns the due campaign');
    Db::run('UPDATE {notices} SET active = 0');
});

scenario('payment methods: store rules, panel control, ZarinPal through the Iran gateway', function () use ($root) {
    freshIp();
    Http::$allowLocalHttpForTests = true;
    $port = 19000 + random_int(0, 999);
    $key = bin2hex(random_bytes(32));
    $bdata = "$root/bridge-data2"; @mkdir($bdata, 0700, true);
    $cfgFile = "$root/bridge-cfg2.json";
    file_put_contents($cfgFile, json_encode(['keys' => ['k1' => $key], 'allowed_ips' => ['127.0.0.1'], 'packages' => ['com.meysam.divanemtiaz'], 'skus' => [],
        'callback_hosts' => ['example.test'], 'rate_per_minute' => 1000, '__data' => $bdata]));
    $router = dirname(__DIR__, 2) . '/payment-bridge/divan-bridge/tests/http_router.php';
    $proc = proc_open([PHP_BINARY, '-S', "127.0.0.1:$port", $router], [0 => ['pipe', 'r'], 1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']], $pipes, null, ['DIVAN_BRIDGE_TEST_CFG' => $cfgFile] + getenv());
    for ($i = 0; $i < 50; $i++) { $s = @fsockopen('127.0.0.1', $port, $en, $es, 0.2); if ($s) { fclose($s); break; } usleep(100000); }
    Settings::set('payment_bridge', ['url' => "http://127.0.0.1:$port/", 'key_id' => 'k1', 'route_myket' => 'direct', 'route_bazaar' => 'direct']);
    Settings::setSecret('bridge_key', $key);

    $ids = fn(string $ch, ?array $plan = null) => array_column(PayRegistry::methodsFor($ch, $plan), 'id');
    check($ids('bazaar') === ['bazaar'] && $ids('myket') === ['myket'] && $ids('direct') === [], 'each store version shows only its own store billing; the site version nothing yet');
    Settings::set('pay_zarinpal', ['enabled' => true]);
    check(PayRegistry::status('zarinpal', 'direct') === 'needs_config' && $ids('direct') === [], 'switched on without a merchant id: «needs settings», not shown');
    Settings::setSecret('pay_zarinpal_merchant_id', '11111111-2222-3333-4444-555555555555');
    check($ids('direct') === ['zarinpal'], 'configured and on: shown in the site version');
    check(PayRegistry::status('zarinpal', 'bazaar') === 'not_allowed' && PayRegistry::status('zarinpal', 'myket') === 'not_allowed' && $ids('bazaar') === ['bazaar'] && $ids('myket') === ['myket'], 'never in the Bazaar or Myket versions (store rules), whatever the panel says');
    $api = apiGet('v1/pay/methods', ['store' => 'bazaar']);
    check(array_column($api['methods'], 'id') === ['bazaar'] && strpos(json_encode($api), 'zarinpal') === false, 'API for the Bazaar version does not even mention other gateways');
    Settings::set('pay_zarinpal', ['enabled' => true, 'kill' => true]);
    check($ids('direct') === [], 'emergency stop removes it at once');
    Settings::set('pay_zarinpal', ['enabled' => true, 'min' => 50000]);
    $m1 = Plans::byCode('m1'); $life = Plans::byCode('life');
    check($ids('direct', $m1) === [] && $ids('direct', $life) === ['zarinpal'], 'minimum amount hides it for cheaper plans');
    Settings::set('pay_zarinpal', ['enabled' => true]);
    check(PayRegistry::get('zarinpal')->test()[0] === 'ok', 'panel connection test through the gateway');

    $base = App::$config['base_url'];
    App::$config['base_url'] = 'https://example.test/divan';
    [$u] = Users::register('web_buyer', 'secret123');
    throws(fn() => PayOrders::create($u, 'zarinpal', 'm1', 'bazaar'), 'method_unavailable', 'the Bazaar version cannot open a web payment');
    $o = PayOrders::create($u, 'zarinpal', 'm1', 'direct');
    $row = PayOrders::byCode($o['order']);
    check(strpos($o['url'], 'https://payment.zarinpal.com/pg/StartPay/A') === 0 && $row['status'] === 'pending' && (int)$row['amount_rial'] === (int)$row['amount_toman'] * 10 && (int)$row['amount_toman'] === (int)$m1['base_price_toman'], 'order: price from the server, rials = toman × 10, StartPay page');
    touch("$cfgFile.zpunpaid");
    $row = PayOrders::settle($row);
    check($row['status'] === 'failed' && !Entitlements::summary((int)$u['id'])['pro'], 'unpaid (ZarinPal -51): failed, nothing granted');
    unlink("$cfgFile.zpunpaid");
    touch("$cfgFile.zpdown");
    $row = PayOrders::settle($row);
    check($row['status'] === 'failed' && !Entitlements::summary((int)$u['id'])['pro'], 'gateway unreachable: no change, nothing granted');
    unlink("$cfgFile.zpdown");
    $row = PayOrders::settle($row);
    check($row['status'] === 'paid' && $row['ref_id'] !== null && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'paid after all (late return): one month granted');
    $again = PayOrders::settle(Db::one('SELECT * FROM {pay_orders} WHERE id = ?', [$row['id']]));
    $again = PayOrders::settle(array_merge($again, ['status' => 'pending']));
    check((int)Db::value("SELECT COUNT(*) FROM {purchases} WHERE store = 'direct' AND user_id = ?", [$u['id']]) === 1 && (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE user_id = ?', [$u['id']]) === 1, 'settled again (repeat callback): no second period');
    $o2 = PayOrders::create($u, 'zarinpal', 'm3', 'direct');
    $r2 = PayOrders::byCode($o2['order']);
    Db::update('pay_orders', ['amount_rial' => 10000], 'id = ?', [$r2['id']]);
    $r2 = PayOrders::settle(PayOrders::byCode($o2['order']));
    check($r2['status'] === 'failed' && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'amount changed on our side: ZarinPal refuses (-50), nothing granted');
    $o3 = PayOrders::create($u, 'zarinpal', 'm3', 'direct');
    $procs = [];
    for ($k = 0; $k < 4; $k++) { $pp = []; $procs[] = [proc_open([PHP_BINARY, __DIR__ . '/race_child.php', $root, $o3['order'], '0', (string)Clock::now(), 'settle'], [1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pp, null, null), $pp]; }
    $outs = [];
    foreach ($procs as [$pr, $pp]) { $outs[] = json_decode((string)stream_get_contents($pp[1]), true); stream_get_contents($pp[2]); fclose($pp[1]); fclose($pp[2]); proc_close($pr); }
    $paid = PayOrders::byCode($o3['order']);
    check($paid['status'] === 'paid' && (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE purchase_id = ?', [$paid['purchase_id']]) === 1
        && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 120 * 86400, '4 settles at once: one purchase, three months added after the current month');
    $o4 = PayOrders::create($u, 'zarinpal', 'life', 'direct');
    PayOrders::settle(PayOrders::byCode($o4['order']));
    check(Entitlements::summary((int)$u['id'])['lifetime'], 'lifetime by web payment');
    throws(fn() => PayOrders::create($u, 'zarinpal', 'm1', 'direct'), 'already_lifetime', 'nothing more to sell after lifetime');
    $offers = array_column(Plans::offers('direct'), null, 'code');
    check($offers['m1']['purchasable'] && $offers['m1']['final_price'] === (int)$m1['base_price_toman'], 'site version plans: purchasable when a web method is ready');
    Settings::set('pay_zarinpal', ['enabled' => false]);
    check(!array_column(Plans::offers('direct'), null, 'code')['m1']['purchasable'], 'switched off in the panel: not purchasable, no APK needed');
    App::$config['base_url'] = $base;
    proc_terminate($proc); proc_close($proc);
    Http::$allowLocalHttpForTests = false;
});

scenario('Blupal card-to-card: sandbox/live key rules, server-side check, webhook only triggers a check', function () use ($root) {
    freshIp();
    Http::$allowLocalHttpForTests = true;
    $port = 20000 + random_int(0, 999);
    $state = "$root/fake-blupal.json";
    file_put_contents($state, json_encode(['keys' => ['blu_test_KEY12345', 'blu_live_LIVE12345'], 'invoices' => [], 'next' => 1000]));
    $proc = proc_open([PHP_BINARY, '-S', "127.0.0.1:$port", __DIR__ . '/fake_blupal.php'], [0 => ['pipe', 'r'], 1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']], $pipes, null, ['FAKE_BLUPAL_STATE' => $state] + getenv());
    for ($i = 0; $i < 50; $i++) { $s = @fsockopen('127.0.0.1', $port, $en, $es, 0.2); if ($s) { fclose($s); break; } usleep(100000); }
    // The API address is the panel's field (here: the local fake Blupal).
    $bset = fn(array $c) => Settings::set('pay_blupal', $c + ['api_base' => "http://127.0.0.1:$port/api/v1"]);
    $simulate = function (string $ref, string $scenario) use ($port) {
        return Http::request('POST', "http://127.0.0.1:$port/api/v1/sandbox/invoices/$ref/simulate", ['X-API-Key: blu_test_KEY12345', 'Content-Type: application/json'], json_encode(['scenario' => $scenario]));
    };
    $ids = fn(string $ch, ?array $plan = null) => array_column(PayRegistry::methodsFor($ch, $plan), 'id');
    Settings::set('pay_zarinpal', ['enabled' => false]);

    $bset(['enabled' => true]);
    check(PayRegistry::status('blupal', 'direct') === 'needs_config' && $ids('direct') === [], 'on without an API key: «needs settings», not shown');
    Settings::setSecret('pay_blupal_api_key', 'blu_test_KEY12345');
    check(PayRegistry::status('blupal', 'direct') === 'needs_config', 'a test key with the sandbox switch off is refused (no free Pro from test payments)');
    check(PayRegistry::get('blupal')->test()[0] === 'warn', 'panel test explains the key/mode mismatch');
    $bset(['enabled' => true, 'sandbox' => true]);
    check($ids('direct') === ['blupal'], 'test key + sandbox on: shown in the site version');
    check(PayRegistry::status('blupal', 'bazaar') === 'not_allowed' && PayRegistry::status('blupal', 'myket') === 'not_allowed' && $ids('bazaar') === ['bazaar'] && $ids('myket') === ['myket'], 'never in the Bazaar or Myket versions');
    check(PayRegistry::get('blupal')->test()[0] === 'ok', 'panel connection test reaches Blupal and the key is accepted');
    Settings::setSecret('pay_blupal_api_key', 'blu_test_WRONG9999');
    check(PayRegistry::get('blupal')->test()[0] === 'bad', 'panel test reports a wrong key');
    Settings::setSecret('pay_blupal_api_key', 'blu_live_LIVE12345');
    check(PayRegistry::status('blupal', 'direct') === 'needs_config', 'a live key with the sandbox switch on is refused');
    $bset(['enabled' => true, 'sandbox' => false]);
    check($ids('direct') === ['blupal'] && PayRegistry::get('blupal')->test()[0] === 'ok', 'live key + sandbox off: ready');
    $bset(['enabled' => true, 'sandbox' => true]);
    Settings::setSecret('pay_blupal_api_key', 'blu_test_KEY12345');
    $api = apiGet('v1/pay/methods', ['store' => 'direct', 'plan' => 'm1']);
    check(array_column($api['methods'], 'id') === ['blupal'] && $api['methods'][0]['hosts'][0] === 'blupal.top' && strpos(json_encode(apiGet('v1/pay/methods', ['store' => 'myket'])), 'blupal') === false, 'API: listed with its page host for the site version only');

    [$u] = Users::register('blu_buyer', 'secret123');
    throws(fn() => PayOrders::create($u, 'blupal', 'm1', 'myket'), 'method_unavailable', 'the Myket version cannot open a Blupal payment');
    $m1 = Plans::byCode('m1');
    $o = PayOrders::create($u, 'blupal', 'm1', 'direct');
    $row = PayOrders::byCode($o['order']);
    $inv = json_decode(file_get_contents($state), true)['invoices'][$row['gateway_ref']];
    check(strpos($o['url'], 'https://blupal.top/sandbox/payment/') === 0 && $row['status'] === 'pending' && (int)$inv['amount'] === (int)$m1['base_price_toman'] * 10 && (int)$row['amount_rial'] === (int)$inv['amount'], 'order: invoice created with the server price in rials, Blupal page opened');
    $row = PayOrders::settle($row);
    check($row['status'] === 'pending' && !Entitlements::summary((int)$u['id'])['pro'], 'not paid yet: stays pending, nothing granted');
    $r = PayWebhooks::handle('blupal', json_encode(['event' => 'payment.completed', 'invoice_id' => (int)$row['gateway_ref'], 'status' => 'PAID', 'amount' => (int)$row['amount_rial']]), [], '10.9.9.9');
    check($r[0] === 200 && PayOrders::byCode($o['order'])['status'] === 'pending' && !Entitlements::summary((int)$u['id'])['pro'], 'forged webhook saying PAID: Blupal is asked, still unpaid, nothing granted');
    touch("$state.down");
    $row = PayOrders::settle(PayOrders::byCode($o['order']));
    check($row['status'] === 'pending' && !Entitlements::summary((int)$u['id'])['pro'], 'Blupal down: no change');
    unlink("$state.down");
    $simulate($row['gateway_ref'], 'wrong_amount');
    $row = PayOrders::settle(PayOrders::byCode($o['order']));
    check($row['status'] === 'pending', 'payer sent a round/different amount: Blupal keeps it pending, so do we');
    $simulate($row['gateway_ref'], 'success');
    touch("$state.wrongamount");
    $row = PayOrders::settle(PayOrders::byCode($o['order']));
    check($row['status'] === 'failed' && !Entitlements::summary((int)$u['id'])['pro'], 'PAID but for another amount: refused, nothing granted');
    unlink("$state.wrongamount");
    touch("$state.wrongmode");
    $row = PayOrders::settle(array_merge(PayOrders::byCode($o['order']), ['status' => 'pending']));
    check($row['status'] === 'failed' && !Entitlements::summary((int)$u['id'])['pro'], 'PAID in the other mode (sandbox vs live): refused');
    unlink("$state.wrongmode");
    $row = PayOrders::settle(PayOrders::byCode($o['order']));
    check($row['status'] === 'paid' && strpos((string)$row['ref_id'], 'BLU-') === 0 && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'paid: one month granted, Blupal transaction kept as the tracking code');
    $r = PayWebhooks::handle('blupal', json_encode(['event' => 'payment.completed', 'invoice_id' => (int)$row['gateway_ref'], 'status' => 'PAID']), [], '10.9.9.9');
    PayWebhooks::handle('blupal', json_encode(['event' => 'payment.completed', 'invoice_id' => (int)$row['gateway_ref'], 'status' => 'PAID']), [], '10.9.9.9');
    PayOrders::settle(array_merge(PayOrders::byCode($o['order']), ['status' => 'pending']));
    check($r[0] === 200 && (int)Db::value("SELECT COUNT(*) FROM {purchases} WHERE store = 'direct' AND user_id = ?", [$u['id']]) === 1 && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'repeated webhooks and checks: still one purchase, no extra time');
    $o2 = PayOrders::create($u, 'blupal', 'm3', 'direct');
    $r2 = PayOrders::byCode($o2['order']);
    $w = PayWebhooks::handle('blupal', json_encode(['invoice_id' => (int)$r2['gateway_ref']]), [], '10.9.9.9');
    check($w[0] === 200 && PayOrders::byCode($o2['order'])['status'] === 'pending', 'webhook before payment: checked, still pending');
    $simulate($r2['gateway_ref'], 'success');
    $w = PayWebhooks::handle('blupal', json_encode(['event' => 'payment.completed', 'invoice_id' => (int)$r2['gateway_ref'], 'status' => 'PAID']), [], '10.9.9.9');
    check($w[0] === 200 && PayOrders::byCode($o2['order'])['status'] === 'paid' && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 120 * 86400, 'real webhook after payment: settled at once, three months added after the current month');
    check(PayWebhooks::handle('blupal', 'not json', [], '10.9.9.9')[0] === 400 && PayWebhooks::handle('blupal', json_encode(['invoice_id' => 'x;drop']), [], '10.9.9.9')[0] === 400 && PayWebhooks::handle('blupal', json_encode(['invoice_id' => 99999]), [], '10.9.9.9')[0] === 200, 'bad webhooks are rejected; unknown invoices are acknowledged and ignored');
    $o3 = PayOrders::create($u, 'blupal', 'm6', 'direct');
    $simulate(PayOrders::byCode($o3['order'])['gateway_ref'], 'cancel');
    check(PayOrders::settle(PayOrders::byCode($o3['order']))['status'] === 'failed', 'cancelled invoice: failed');
    $o4 = PayOrders::create($u, 'blupal', 'm6', 'direct');
    $simulate(PayOrders::byCode($o4['order'])['gateway_ref'], 'expire');
    check(PayOrders::settle(PayOrders::byCode($o4['order']))['status'] === 'failed', 'expired invoice: failed');
    $bset(['enabled' => true, 'sandbox' => true, 'api_base' => "http://127.0.0.1:1/api/v1"]);
    check(PayRegistry::get('blupal')->test()[0] === 'bad', 'API address changed in the panel: used at once (a wrong one fails the panel test)');
    Settings::set('pay_blupal', ['enabled' => true, 'sandbox' => true, 'api_base' => 'https://10.0.0.1/api']);
    check(PayEndpoint::base(PayRegistry::get('blupal')) === 'https://blupal.top/api/v1', 'an unsafe stored address falls back to the documented one');
    $bset(['enabled' => true, 'sandbox' => true, 'hosts' => 'pay.blupal-new.ir']);
    check(apiGet('v1/pay/methods', ['store' => 'direct'])['methods'][0]['hosts'][0] === 'pay.blupal-new.ir', 'payment-page domain changed in the panel: sent to the app');
    throws(fn() => PayOrders::create($u, 'blupal', 'm1', 'direct'), 'gateway_url', 'a payment page outside the panel domains is never opened');
    $bset(['enabled' => true, 'sandbox' => true]);
    check(PayRegistry::get('blupal')->test()[0] === 'ok' && PayRegistry::get('blupal')->payHosts() === ['blupal.top'], 'back to the right address: works again');
    $bset(['enabled' => true, 'sandbox' => true, 'kill' => true]);
    check($ids('direct') === [], 'emergency stop removes Blupal at once');
    $bset(['enabled' => false]);
    proc_terminate($proc); proc_close($proc);
    Http::$allowLocalHttpForTests = false;
});

scenario('Aban Gateway: token/mode rules, Iran bridge and direct routes, one-shot verify, signed webhook, panel address', function () use ($root) {
    freshIp();
    Http::$allowLocalHttpForTests = true;
    $fport = 21000 + random_int(0, 999);
    $state = "$root/fake-aban.json";
    $TT = 'ag_test_' . str_repeat('T1x', 16);
    $LT = 'ag_live_' . str_repeat('L2y', 16);
    file_put_contents($state, json_encode(['tokens' => [$TT, $LT], 'invoices' => [], 'requests' => []]));
    $fake = proc_open([PHP_BINARY, '-S', "127.0.0.1:$fport", __DIR__ . '/fake_aban.php'], [0 => ['pipe', 'r'], 1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']], $pp1, null, ['FAKE_ABAN_STATE' => $state] + getenv());
    // The real Iran bridge (fake stores), passing Aban calls on to the fake Aban.
    $bport = 22000 + random_int(0, 999);
    $key = bin2hex(random_bytes(32));
    $bdata = "$root/bridge-data-aban"; @mkdir($bdata, 0700, true);
    $cfgFile = "$root/bridge-cfg-aban.json";
    file_put_contents($cfgFile, json_encode(['keys' => ['k1' => $key], 'allowed_ips' => ['127.0.0.1'], 'packages' => ['com.meysam.divanemtiaz'], 'skus' => [], 'callback_hosts' => ['example.test'], 'rate_per_minute' => 1000, '__data' => $bdata]));
    $router = dirname(__DIR__, 2) . '/payment-bridge/divan-bridge/tests/http_router.php';
    $bridge = proc_open([PHP_BINARY, '-S', "127.0.0.1:$bport", $router], [0 => ['pipe', 'r'], 1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']], $pp2, null, ['DIVAN_BRIDGE_TEST_CFG' => $cfgFile, 'FAKE_ABAN_URL' => "http://127.0.0.1:$fport"] + getenv());
    foreach ([$fport, $bport] as $pt) for ($i = 0; $i < 50; $i++) { $sk = @fsockopen('127.0.0.1', $pt, $en, $es, 0.2); if ($sk) { fclose($sk); break; } usleep(100000); }
    Settings::set('payment_bridge', ['url' => "http://127.0.0.1:$bport/", 'key_id' => 'k1', 'route_myket' => 'direct', 'route_bazaar' => 'direct']);
    Settings::setSecret('bridge_key', $key);
    $base = App::$config['base_url'];
    App::$config['base_url'] = 'https://example.test/divan';
    $sim = function (string $ref) use ($fport, $TT) { return Http::request('POST', "http://127.0.0.1:$fport/api/v1/invoices/$ref/simulate-payment", ['Authorization: Bearer ' . $TT], '{}'); };
    $inv = fn(string $ref) => json_decode(file_get_contents($state), true)['invoices'][$ref];
    $ids = fn(string $ch) => array_column(PayRegistry::methodsFor($ch), 'id');
    $a = PayRegistry::get('aban');
    Settings::set('pay_zarinpal', ['enabled' => false]);
    Settings::set('pay_blupal', ['enabled' => false]);

    Settings::set('pay_aban', ['enabled' => true]);
    check(PayRegistry::status('aban', 'direct') === 'needs_config' && $ids('direct') === [], 'on without a token: «needs settings», not shown');
    Settings::setSecret('pay_aban_token', $TT);
    check(PayRegistry::status('aban', 'direct') === 'needs_config' && $a->test()[0] === 'warn', 'test token with the sandbox switch off: refused (no free Pro from test payments)');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true]);
    check($ids('direct') === ['aban'] && $a->test()[0] === 'ok', 'test token + sandbox, through the Iran bridge: ready, panel test reaches Aban');
    check(PayRegistry::status('aban', 'bazaar') === 'not_allowed' && PayRegistry::status('aban', 'myket') === 'not_allowed' && $ids('bazaar') === ['bazaar'] && $ids('myket') === ['myket'], 'never in the Bazaar or Myket versions');
    $api = apiGet('v1/pay/methods', ['store' => 'direct', 'plan' => 'm1']);
    check(array_column($api['methods'], 'id') === ['aban'] && $api['methods'][0]['hosts'] === ['abangateway.ir', 'example.test'] && strpos(json_encode(apiGet('v1/pay/methods', ['store' => 'bazaar'])), 'aban') === false, 'API: listed with its page domain for the site version only');
    Settings::setSecret('pay_aban_token', 'ag_test_' . str_repeat('W9z', 16));
    check($a->test()[0] === 'bad', 'panel test reports a wrong token');
    Settings::setSecret('pay_aban_token', $LT);
    check(PayRegistry::status('aban', 'direct') === 'needs_config', 'live token with the sandbox switch on: refused');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => false]);
    check($a->test()[0] === 'ok', 'live token + sandbox off: ready');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true]);
    Settings::setSecret('pay_aban_token', $TT);

    [$u] = Users::register('aban_buyer', 'secret123');
    throws(fn() => PayOrders::create($u, 'aban', 'm1', 'bazaar'), 'method_unavailable', 'the Bazaar version cannot open an Aban payment');
    $m1 = Plans::byCode('m1');
    $o = PayOrders::create($u, 'aban', 'm1', 'direct');
    $row = PayOrders::byCode($o['order']);
    $iv = $inv($row['gateway_ref']);
    check(strpos($o['url'], 'https://abangateway.ir/pay/inv_') === 0 && $row['status'] === 'pending' && $iv['amount'] === (int)$m1['base_price_toman'] * 10 && $iv['order'] === $o['order']
        && $iv['callback'] === 'https://example.test/divan/pay/webhook.php?m=aban' && $iv['return'] === 'https://example.test/divan/pay/return.php?o=' . $o['order'], 'order through the bridge: server price in rials, our order code, webhook and return to our site');
    $row = PayOrders::settle($row);
    check($row['status'] === 'pending' && !Entitlements::summary((int)$u['id'])['pro'], 'not paid yet: pending, nothing granted');
    $raw = json_encode(['event' => 'invoice.paid', 'invoice_id' => $row['gateway_ref'], 'order_id' => $o['order'], 'amount_rial' => (int)$row['amount_rial'], 'status' => 'paid', 'is_test' => true]);
    check(PayWebhooks::handle('aban', $raw, ['signature' => ''], '10.8.8.8')[0] === 200 && PayOrders::byCode($o['order'])['status'] === 'pending', 'forged «paid» webhook (no secret set): Aban is asked, still unpaid');
    Settings::setSecret('pay_aban_webhook_secret', 'whsec_test_123456');
    check(PayWebhooks::handle('aban', $raw, ['signature' => 'deadbeef'], '10.8.8.8')[0] === 401 && PayWebhooks::handle('aban', $raw, ['signature' => ''], '10.8.8.8')[0] === 401, 'with the webhook secret set: unsigned or wrongly signed webhooks are refused');
    $sim($row['gateway_ref']);
    touch("$state.wrongamount");
    check(PayOrders::settle(PayOrders::byCode($o['order']))['status'] === 'failed' && !Entitlements::summary((int)$u['id'])['pro'], 'paid but Aban reports another amount: refused');
    unlink("$state.wrongamount");
    touch("$state.wrongorder");
    check(PayOrders::settle(array_merge(PayOrders::byCode($o['order']), ['status' => 'pending']))['status'] === 'failed', 'paid invoice of another order: refused');
    unlink("$state.wrongorder");
    touch("$state.wrongtest");
    check(PayOrders::settle(array_merge(PayOrders::byCode($o['order']), ['status' => 'pending']))['status'] === 'failed', 'test/live mode differs: refused');
    unlink("$state.wrongtest");
    touch("$state.down");
    check(PayOrders::settle(PayOrders::byCode($o['order']))['status'] === 'failed' && !Entitlements::summary((int)$u['id'])['pro'], 'Aban down: no change');
    unlink("$state.down");
    $w = PayWebhooks::handle('aban', $raw, ['signature' => hash_hmac('sha256', $raw, 'whsec_test_123456')], '10.8.8.8');
    $row = PayOrders::byCode($o['order']);
    check($w[0] === 200 && $row['status'] === 'paid' && $row['ref_id'] === 'ABAN-' . $row['gateway_ref'] && $inv($row['gateway_ref'])['verified'] && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'signed webhook after payment: checked, verified once at Aban, one month granted');
    PayWebhooks::handle('aban', $raw, ['signature' => hash_hmac('sha256', $raw, 'whsec_test_123456')], '10.8.8.8');
    PayOrders::settle(array_merge(PayOrders::byCode($o['order']), ['status' => 'pending']));
    check((int)Db::value("SELECT COUNT(*) FROM {purchases} WHERE store = 'direct' AND user_id = ?", [$u['id']]) === 1 && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 30 * 86400, 'repeated webhooks and checks («already verified»): still one purchase');
    $o2 = PayOrders::create($u, 'aban', 'm3', 'direct');
    $r2 = PayOrders::byCode($o2['order']);
    $sim($r2['gateway_ref']);
    $procs = [];
    for ($k = 0; $k < 4; $k++) { $pp = []; $procs[] = [proc_open([PHP_BINARY, __DIR__ . '/race_child.php', $root, $o2['order'], '0', (string)Clock::now(), 'settle'], [1 => ['pipe', 'w'], 2 => ['pipe', 'w']], $pp, null, null), $pp]; }
    foreach ($procs as [$pr, $pp]) { stream_get_contents($pp[1]); stream_get_contents($pp[2]); fclose($pp[1]); fclose($pp[2]); proc_close($pr); }
    $paid = PayOrders::byCode($o2['order']);
    check($paid['status'] === 'paid' && (int)Db::value('SELECT COUNT(*) FROM {entitlements} WHERE purchase_id = ?', [$paid['purchase_id']]) === 1 && Entitlements::summary((int)$u['id'])['expires_at'] === Clock::now() + 120 * 86400, '4 checks at once: one purchase, three months added');
    check(PayWebhooks::handle('aban', 'not json', ['signature' => hash_hmac('sha256', 'not json', 'whsec_test_123456')], '10.8.8.8')[0] === 400
        && PayWebhooks::handle('aban', $bad = json_encode(['invoice_id' => '../x']), ['signature' => hash_hmac('sha256', $bad, 'whsec_test_123456')], '10.8.8.8')[0] === 400
        && PayWebhooks::handle('aban', $unk = json_encode(['event' => 'invoice.paid', 'invoice_id' => 'inv_unknown123']), ['signature' => hash_hmac('sha256', $unk, 'whsec_test_123456')], '10.8.8.8')[0] === 200, 'bad webhooks refused; unknown invoices acknowledged and ignored');
    $o3 = PayOrders::create($u, 'aban', 'm6', 'direct');
    Http::request('POST', "http://127.0.0.1:$fport/api/v1/invoices/" . PayOrders::byCode($o3['order'])['gateway_ref'] . '/cancel', ['Authorization: Bearer ' . $TT], '{}');
    check(PayOrders::settle(PayOrders::byCode($o3['order']))['status'] === 'failed', 'cancelled invoice: failed');

    // Direct route and a changed API address, from the panel only.
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true, 'route' => 'direct', 'api_base' => "http://127.0.0.1:$fport/api/v1"]);
    $before = count(json_decode(file_get_contents($state), true)['requests']);
    check($a->test()[0] === 'ok' && count(json_decode(file_get_contents($state), true)['requests']) === $before + 1, 'direct route with the address from the panel');
    $o4 = PayOrders::create($u, 'aban', 'm6', 'direct');
    $sim(PayOrders::byCode($o4['order'])['gateway_ref']);
    check(PayOrders::settle(PayOrders::byCode($o4['order']))['status'] === 'paid', 'direct route: paid and verified');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true, 'route' => 'direct', 'api_base' => 'http://127.0.0.1:1/api/v1']);
    check($a->test()[0] === 'bad', 'a wrong address in the panel shows up in the panel test');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true, 'api_base' => 'https://api.aban-new-domain.ir/api/v1']);
    check($a->test()[0] === 'ok', 'new API domain set in the panel, through the bridge: works without a new bridge or APK');
    file_put_contents("$state.payhost", 'pay.aban-new-domain.ir');
    throws(fn() => PayOrders::create($u, 'aban', 'm1', 'direct'), 'gateway_url', 'payment page moved but the panel still lists the old domain: never opened');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true, 'api_base' => 'https://api.aban-new-domain.ir/api/v1', 'hosts' => 'pay.aban-new-domain.ir']);
    $o5 = PayOrders::create($u, 'aban', 'm1', 'direct');
    check(strpos($o5['url'], 'https://pay.aban-new-domain.ir/pay/') === 0 && apiGet('v1/pay/methods', ['store' => 'direct'])['methods'][0]['hosts'] === ['pay.aban-new-domain.ir', 'example.test'], 'payment-page domain changed in the panel: orders work and the app gets the new domain');
    unlink("$state.payhost");
    check(PayEndpoint::parseHosts('a.ir, b.ir،c.ir') === ['a.ir', 'b.ir', 'c.ir'] && PayEndpoint::parseHosts('evil.ir/x') === null && PayEndpoint::parseHosts('10.0.0.1') === null
        && !PayEndpoint::validBase('https://127.0.0.1/api') && !PayEndpoint::validBase('https://u@x.ir/') && !PayEndpoint::validBase('https://x.ir:8443/') && !PayEndpoint::validBase('ftp://x.ir') && PayEndpoint::validBase('https://api.x.ir/api/v2'), 'panel address checks: HTTPS domains only');
    Settings::set('pay_aban', ['enabled' => true, 'sandbox' => true, 'kill' => true]);
    check($ids('direct') === [], 'emergency stop removes Aban at once');
    Settings::set('pay_aban', ['enabled' => false]);
    Settings::setSecret('pay_aban_webhook_secret', '');
    App::$config['base_url'] = $base;
    proc_terminate($fake); proc_close($fake); proc_terminate($bridge); proc_close($bridge);
    Http::$allowLocalHttpForTests = false;
});

scenario('pages around a web payment: Divan page first, result page, panel texts and offers, editable API paths', function () use ($root) {
    freshIp();
    Http::$allowLocalHttpForTests = true;
    $port = 23000 + random_int(0, 999);
    $state = "$root/fake-blupal-pages.json";
    file_put_contents($state, json_encode(['keys' => ['blu_test_KEY12345'], 'invoices' => [], 'next' => 5000]));
    $proc = proc_open([PHP_BINARY, '-S', "127.0.0.1:$port", __DIR__ . '/fake_blupal.php'], [0 => ['pipe', 'r'], 1 => ['file', '/dev/null', 'w'], 2 => ['file', '/dev/null', 'w']], $pipes, null, ['FAKE_BLUPAL_STATE' => $state] + getenv());
    for ($i = 0; $i < 50; $i++) { $sk = @fsockopen('127.0.0.1', $port, $en, $es, 0.2); if ($sk) { fclose($sk); break; } usleep(100000); }
    $base = App::$config['base_url'];
    App::$config['base_url'] = 'https://example.test/divan';
    $api = "http://127.0.0.1:$port/api/v1";
    Settings::set('pay_zarinpal', ['enabled' => false]);
    Settings::set('pay_aban', ['enabled' => false]);
    Settings::set('pay_blupal', ['enabled' => true, 'sandbox' => true, 'api_base' => $api]);
    Settings::setSecret('pay_blupal_api_key', 'blu_test_KEY12345');
    $methods = apiGet('v1/pay/methods', ['store' => 'direct']);
    check($methods['methods'][0]['hosts'] === ['blupal.top', 'example.test'], 'the app may open Divan\'s own page (this server) and the gateway page, nothing else');
    [$u] = Users::register('pages_buyer', 'secret123');
    $o = PayOrders::create($u, 'blupal', 'm1', 'direct', true);
    $row = PayOrders::byCode($o['order']);
    check($o['url'] === 'https://example.test/divan/pay/start.php?o=' . $o['order'] && $o['return'] === 'https://example.test/divan/pay/return.php?o=' . $o['order'] && strpos((string)$row['pay_url'], 'https://blupal.top/sandbox/payment/') === 0, 'new app: Divan\'s page first; the gateway page is kept on the order');
    $old = PayOrders::create($u, 'blupal', 'm1', 'direct');
    check(strpos($old['url'], 'https://blupal.top/') === 0, 'older app: the gateway page directly, as before');
    $m1 = Plans::byCode('m1');
    $html = PayPages::renderStart($row);
    check(strpos($html, fa_digits(number_format((int)$m1['base_price_toman']))) !== false && strpos($html, e($m1['title'])) !== false && strpos($html, 'href="' . e($row['pay_url']) . '"') !== false
        && strpos($html, 'کارت به کارت، دقیقاً') !== false && strpos($html, 'divan://pay/' . $o['order']) !== false, 'page before payment: plan, exact amount, card-to-card note, continue to the gateway, back to the app');
    check(strpos(PayPages::renderStart(array_merge($row, ['status' => 'expired'])), 'href="' . e($row['pay_url']) . '"') === false && strpos(PayPages::renderStart(null), 'سفارش پیدا نشد') !== false, 'expired or unknown order: no way to the gateway');
    Settings::set('pay_pages', ['before_title' => 'عنوان آزمایشی', 'before_notes' => "نکتهٔ یک\nنکتهٔ دو", 'card_note' => '', 'after_note' => 'پیام پایانی', 'show_ads' => true]);
    $html = PayPages::renderStart($row);
    check(strpos($html, 'عنوان آزمایشی') !== false && strpos($html, '<li>نکتهٔ دو</li>') !== false && strpos($html, 'کارت به کارت، دقیقاً') === false, 'texts of the page come from the panel');
    $adId = Db::insert('ads', ['name' => 'pay-test', 'title' => 'پیشنهاد <ویژه>', 'body' => 'متن', 'format' => 'card', 'placements' => 'pay_before', 'stores' => 'direct', 'target_url' => 'https://example.org/x', 'cta_text' => 'دیدن', 'active' => 1, 'priority' => 5, 'closable' => 1, 'min_interval_sec' => 0, 'impressions_total' => 0, 'style_json' => '{}', 'created_at' => Clock::sql(), 'updated_at' => Clock::sql()]);
    $html = PayPages::renderStart($row);
    check(strpos($html, 'پیشنهاد &lt;ویژه&gt;') !== false && strpos($html, 'https://example.org/x') !== false && (int)Db::value('SELECT impressions_total FROM {ads} WHERE id = ?', [$adId]) === 1, 'an offer for the «before payment» slot is shown (escaped) and counted');
    check(strpos(PayPages::renderReturn(PayOrders::byCode($o['order'])), 'پیشنهاد &lt;ویژه&gt;') === false, 'the offer stays in its own slot');
    Settings::set('pay_pages', ['show_ads' => false] + PayPages::cfg());
    check(strpos(PayPages::renderStart($row), 'پیشنهاد &lt;ویژه&gt;') === false, 'offers switched off in the panel');
    Db::update('ads', ['active' => 0], 'id = ?', [$adId]);

    check(PayPages::findOrder(['o' => $o['order']], [])['id'] === $row['id'] && PayPages::findOrder(['m' => 'blupal', 'invoice_id' => $row['gateway_ref']], [])['id'] === $row['id']
        && PayPages::findOrder(['m' => 'blupal'], [PayPages::COOKIE => $o['order']])['id'] === $row['id'] && PayPages::findOrder(['m' => 'blupal', 'invoice_id' => '../1'], []) === null && PayPages::findOrder([], []) === null, 'result page finds the order: our address, the gateway invoice id, or the remembered order');
    $html = PayPages::renderReturn($row);
    check(strpos($html, 'در حال بررسی پرداخت') !== false && strpos($html, 'data-order="' . $o['order'] . '"') !== false && preg_match('/<script nonce="[A-Za-z0-9_-]{20,}">/', $html) === 1, 'not paid yet: the result page checks again by itself');
    Http::request('POST', "http://127.0.0.1:$port/api/v1/sandbox/invoices/{$row['gateway_ref']}/simulate", ['X-API-Key: blu_test_KEY12345', 'Content-Type: application/json'], '{"scenario":"success"}');
    $paid = PayOrders::settle($row);
    $html = PayPages::renderReturn($paid);
    check($paid['status'] === 'paid' && strpos($html, 'پرداخت با موفقیت انجام شد') !== false && strpos($html, e($paid['ref_id'])) !== false && strpos($html, 'اعتبار Pro تا') !== false && strpos($html, 'پیام پایانی') !== false && strpos($html, '<script') === false, 'paid: success, tracking code, Pro end date, panel message; no more checking');
    check(strpos(PayPages::renderReturn(array_merge($row, ['status' => 'failed'])), 'پرداخت انجام نشد') !== false, 'failed: clear message');
    check(strpos(PayPages::renderStart(null, true), 'نمونهٔ نمایشی') !== false && strpos(PayPages::renderReturn(null, true, 'paid'), 'نمونهٔ نمایشی') !== false, 'panel previews are marked as samples');

    // Editable API: a new path and an extra field of a new invoice, from the panel only.
    Settings::set('pay_blupal', ['enabled' => true, 'sandbox' => true, 'api_base' => $api, 'paths' => ['create' => '/bills/new'], 'extra' => "shop=divan\nnote=hello"]);
    PayOrders::create($u, 'blupal', 'm3', 'direct', true);
    $fs = json_decode(file_get_contents($state), true);
    check($fs['last_path'] === '/api/v1/bills/new' && $fs['last_create']['shop'] === 'divan' && $fs['last_create']['note'] === 'hello' && $fs['last_create']['amount'] === (int)Plans::byCode('m3')['base_price_toman'] * 10, 'a path and extra fields set in the panel are used');
    Settings::set('pay_blupal', ['enabled' => true, 'sandbox' => true, 'api_base' => $api, 'paths' => ['get' => '/../admin/{id}', 'create' => 'https://evil.example/x'], 'extra' => "amount=1"]);
    $b = PayRegistry::get('blupal');
    check(PayEndpoint::path($b, 'get', '7') === '/invoices/7' && PayEndpoint::path($b, 'create') === '/invoices/create' && PayEndpoint::extra($b) === [], 'unsafe stored paths and extra fields replacing Divan\'s own are ignored');
    check(PayEndpoint::validPath('/v2/invoices/{id}/status', true) && !PayEndpoint::validPath('/v2/invoices', true) && !PayEndpoint::validPath('/a/{id}/{id}', true) && !PayEndpoint::validPath('/a?b=1', false)
        && PayEndpoint::parseExtra("a=1\nb_c = متن", ['amount']) === ['a' => '1', 'b_c' => 'متن'] && PayEndpoint::parseExtra('amount=5', ['amount']) === null && PayEndpoint::parseExtra('Bad=1', []) === null, 'path and extra-field rules');
    Settings::set('pay_blupal', ['enabled' => false]);
    Settings::set('pay_pages', []);
    App::$config['base_url'] = $base;
    proc_terminate($proc); proc_close($proc);
    Http::$allowLocalHttpForTests = false;
});

// ---------------------------------------------------------------- report
echo "\n" . ($failed ? "FAILED " . count($failed) . " of " . ($passed + count($failed)) . " checks\n" . implode("\n", $failed) . "\n" : "ALL $passed CHECKS PASSED\n");
foreach ($pdo->query("SHOW TABLES LIKE 'dvt\\_%'")->fetchAll(PDO::FETCH_COLUMN) as $t) $pdo->exec("DROP TABLE `$t`");
exit($failed ? 1 : 0);
