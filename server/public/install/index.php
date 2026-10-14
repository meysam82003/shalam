<?php
/**
 * Divan Emtiaz — web installer. Checks the host, creates the tables, the keys and the first admin
 * (with two-step login), then locks itself.
 */
declare(strict_types=1);

require dirname(__DIR__) . '/app/bootstrap.php';

$secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
session_name('divan_install');
session_set_cookie_params(['lifetime' => 0, 'path' => '/', 'secure' => $secure, 'httponly' => true, 'samesite' => 'Strict']);
session_start();
header('X-Frame-Options: DENY');
header('Cache-Control: no-store');

$step = (int)($_SESSION['step'] ?? 1);
$error = '';
$info = '';

function csrf(): string
{
    if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16));
    return $_SESSION['csrf'];
}

function check_csrf(): void
{
    if (!hash_equals((string)($_SESSION['csrf'] ?? ''), (string)($_POST['csrf'] ?? ''))) {
        http_response_code(400);
        exit('درخواست نامعتبر است؛ صفحه را دوباره باز کنید.');
    }
}

if (App::installed() && $step !== 4) {
    $step = 0;
}

try {
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && $step > 0) {
        check_csrf();
        $action = (string)($_POST['action'] ?? '');

        if ($action === 'requirements' && Installer::ready()) {
            $_SESSION['step'] = $step = 2;
        }

        if ($action === 'database' && $step === 2) {
            $cfg = [
                'db_host' => trim((string)$_POST['db_host']) ?: 'localhost',
                'db_port' => (int)($_POST['db_port'] ?? 3306) ?: 3306,
                'db_name' => trim((string)$_POST['db_name']),
                'db_user' => trim((string)$_POST['db_user']),
                'db_pass' => (string)$_POST['db_pass'],
                'db_prefix' => preg_replace('/[^a-z0-9_]/', '', strtolower((string)($_POST['db_prefix'] ?? 'dv_'))) ?: 'dv_',
                'base_url' => rtrim(trim((string)($_POST['base_url'] ?? '')), '/'),
            ];
            if ($cfg['db_name'] === '' || $cfg['db_user'] === '') throw new RuntimeException('نام پایگاه داده و نام کاربری آن را وارد کنید.');
            if ($cfg['base_url'] !== '' && !preg_match('#^https://#', $cfg['base_url'])) throw new RuntimeException('نشانی سایت باید با https:// شروع شود.');
            // An unfinished installation can only be continued with the same database credentials.
            if (is_file(App::configFile())) {
                $prev = App::$config;
                if (($prev['db_name'] ?? '') !== $cfg['db_name'] || ($prev['db_user'] ?? '') !== $cfg['db_user'] || !hash_equals((string)($prev['db_pass'] ?? ''), $cfg['db_pass']) || ($prev['db_host'] ?? '') !== $cfg['db_host']) {
                    throw new RuntimeException('نصب نیمه‌کاره‌ای وجود دارد؛ برای ادامه، همان اطلاعات پایگاه داده‌ای را که قبلاً وارد شده بنویسید.');
                }
            }
            try {
                $pdo = Db::connect($cfg);
            } catch (PDOException $e) {
                throw new RuntimeException('اتصال به پایگاه داده برقرار نشد. نام، کاربر و رمز را بررسی کنید. (' . $e->getCode() . ')');
            }
            // An unfinished earlier attempt keeps its keys; only the database settings are refreshed.
            $old = is_file(App::configFile()) ? App::$config : [];
            $cfg['app_key'] = $old['app_key'] ?? base64_encode(random_bytes(32));
            $cfg['backup_key'] = $old['backup_key'] ?? base64_encode(random_bytes(32));
            $cfg['cron_key'] = $old['cron_key'] ?? bin2hex(random_bytes(16));
            $cfg['installed_at'] = gmdate('c');
            Installer::writeConfig($cfg);
            Db::use($pdo, $cfg['db_prefix']);
            Migrations::run();
            if (!is_file(App::$root . '/storage/keys/license-private.pem')) Crypto::generateKeys(App::$root . '/storage/keys');
            $_SESSION['step'] = $step = 3;
            $_SESSION['totp'] = Totp::secret();
        }

        if ($action === 'admin' && $step === 3) {
            Db::pdo();
            $user = trim((string)$_POST['admin_user']);
            $pass = (string)$_POST['admin_pass'];
            if (!preg_match('/^[A-Za-z][A-Za-z0-9_.]{3,31}$/', $user)) throw new RuntimeException('نام کاربری مدیر: ۴ تا ۳۲ حرف انگلیسی یا عدد، با حرف شروع شود.');
            if ($pass !== (string)$_POST['admin_pass2']) throw new RuntimeException('تکرار رمز با خود رمز یکی نیست.');
            if ($m = Installer::validateAdminPassword($pass)) throw new RuntimeException($m);
            $secret = (string)$_SESSION['totp'];
            $stepUsed = Totp::verify($secret, (string)$_POST['totp']);
            if ($stepUsed === null) throw new RuntimeException('کد شش‌رقمی برنامهٔ احراز هویت درست نیست؛ ساعت گوشی را بررسی کنید.');
            if ((int)Db::value("SELECT COUNT(*) FROM {admins} WHERE role = 'owner'") > 0) throw new RuntimeException('مدیر اصلی قبلاً ساخته شده است.');
            $id = Db::insert('admins', [
                'username' => $user, 'password_hash' => Crypto::passwordHash($pass), 'role' => 'owner',
                'totp_enc' => Crypto::encrypt($secret), 'totp_last_step' => $stepUsed, 'active' => 1, 'created_at' => Clock::sql(),
            ]);
            Audit::log('admin', $id, 'install_completed', 'system');
            Installer::lock();
            $_SESSION['step'] = $step = 4;
        }
    }
} catch (Throwable $e) {
    $error = $e instanceof RuntimeException || $e instanceof ApiError ? $e->getMessage() : 'خطای پیش‌بینی‌نشده: ' . $e->getMessage();
}

$base = App::baseUrl();
?><!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>نصب دیوان امتیاز</title>
<link rel="stylesheet" href="../assets/css/divan.css">
</head>
<body>
<div class="install">
  <div class="row" style="margin-bottom:12px">
    <div style="flex:0 0 56px"><img src="../assets/img/logo.png" alt="" style="width:56px;height:56px;border-radius:14px"></div>
    <div><h1 style="margin:0">نصب دیوان امتیاز</h1><div class="muted">سایت، API برنامه و پنل مدیریت</div></div>
  </div>
<?php if ($step > 0): ?>
  <div class="steps">
    <?php foreach (['بررسی هاست', 'پایگاه داده', 'مدیر اصلی', 'پایان'] as $i => $label): ?>
      <span class="<?= $step === $i + 1 ? 'on' : ($step > $i + 1 ? 'done' : '') ?>"><?= fa_digits($i + 1) ?>. <?= e($label) ?></span>
    <?php endforeach; ?>
  </div>
<?php endif; ?>
<?php if ($error): ?><div class="alert bad"><?= e($error) ?></div><?php endif; ?>

<?php if ($step === 0): ?>
  <div class="card">
    <h2>نصب انجام شده و قفل است</h2>
    <p>برای امنیت، نصب‌کننده دوباره اجرا نمی‌شود. برای نصب دوباره، فایل <code>storage/install.lock</code> را فقط در صورت اطمینان حذف کنید.</p>
    <p><a class="btn gold" href="../admin/">ورود به پنل مدیریت</a> <a class="btn ghost" href="../">صفحهٔ اصلی سایت</a></p>
  </div>

<?php elseif ($step === 1): ?>
  <div class="card">
    <h2>بررسی هاست</h2>
    <div class="table-wrap"><table>
      <thead><tr><th>مورد</th><th>وضعیت</th><th>توضیح</th></tr></thead>
      <tbody>
      <?php foreach (Installer::checks() as [$label, $ok, $detail, $req]): ?>
        <tr><td><?= e($label) ?></td><td><?= $ok ? '<span class="badge ok">درست</span>' : ($req ? '<span class="badge bad">لازم</span>' : '<span class="badge warn">اختیاری</span>') ?></td><td><?= e($detail) ?></td></tr>
      <?php endforeach; ?>
      </tbody>
    </table></div>
    <form method="post" style="margin-top:14px">
      <input type="hidden" name="csrf" value="<?= e(csrf()) ?>"><input type="hidden" name="action" value="requirements">
      <?php if (Installer::ready()): ?>
        <button class="btn gold">ادامه</button>
      <?php else: ?>
        <div class="alert warn">موارد «لازم» را در پنل هاست فعال کنید (بخش Select PHP Version / PHP Extensions) و صفحه را تازه کنید.</div>
      <?php endif; ?>
    </form>
  </div>

<?php elseif ($step === 2): ?>
  <div class="card">
    <h2>اتصال به پایگاه داده</h2>
    <p class="muted">در پنل هاست (بخش MySQL Databases) یک پایگاه داده و یک کاربر بسازید، کاربر را با «همهٔ دسترسی‌ها» به پایگاه داده اضافه کنید و اطلاعاتش را اینجا وارد کنید.</p>
    <form method="post" autocomplete="off">
      <input type="hidden" name="csrf" value="<?= e(csrf()) ?>"><input type="hidden" name="action" value="database">
      <div class="row">
        <div><label>نام پایگاه داده</label><input type="text" name="db_name" class="ltr" required value="<?= e($_POST['db_name'] ?? '') ?>"></div>
        <div><label>نام کاربری پایگاه داده</label><input type="text" name="db_user" class="ltr" required value="<?= e($_POST['db_user'] ?? '') ?>"></div>
      </div>
      <div class="row">
        <div><label>رمز کاربر پایگاه داده</label><input type="password" name="db_pass" class="ltr"></div>
        <div><label>میزبان</label><input type="text" name="db_host" class="ltr" value="<?= e($_POST['db_host'] ?? 'localhost') ?>"></div>
      </div>
      <div class="row">
        <div><label>درگاه</label><input type="number" name="db_port" class="ltr" value="<?= e($_POST['db_port'] ?? '3306') ?>"></div>
        <div><label>پیشوند جدول‌ها</label><input type="text" name="db_prefix" class="ltr" value="<?= e($_POST['db_prefix'] ?? 'dv_') ?>"><div class="hint">اگر پایگاه داده با برنامهٔ دیگری مشترک است، پیشوند جدا بدهید.</div></div>
      </div>
      <label>نشانی سایت</label>
      <input type="text" name="base_url" class="ltr" value="<?= e($_POST['base_url'] ?? $base) ?>">
      <div class="hint">برنامهٔ اندروید از این نشانی استفاده می‌کند. معمولاً همان پیشنهاد درست است.</div>
      <p style="margin-top:14px"><button class="btn gold">ساخت جدول‌ها و کلیدها</button></p>
    </form>
  </div>

<?php elseif ($step === 3): $secret = (string)$_SESSION['totp']; ?>
  <div class="card">
    <h2>ساخت مدیر اصلی با ورود دومرحله‌ای</h2>
    <form method="post" autocomplete="off">
      <input type="hidden" name="csrf" value="<?= e(csrf()) ?>"><input type="hidden" name="action" value="admin">
      <div class="row">
        <div><label>نام کاربری مدیر</label><input type="text" name="admin_user" class="ltr" required value="<?= e($_POST['admin_user'] ?? '') ?>"></div>
      </div>
      <div class="row">
        <div><label>رمز (دست‌کم ۱۲ نویسه)</label><input type="password" name="admin_pass" class="ltr" required></div>
        <div><label>تکرار رمز</label><input type="password" name="admin_pass2" class="ltr" required></div>
      </div>
      <div class="hint">رمز باید حرف کوچک، حرف بزرگ انگلیسی و عدد داشته باشد.</div>
      <h3 style="margin-top:16px">ورود دومرحله‌ای</h3>
      <p class="muted">یک برنامهٔ احراز هویت (مثل Google Authenticator، Microsoft Authenticator، Aegis یا FreeOTP) روی گوشی نصب کنید، این کد را اسکن کنید و کد شش‌رقمی را وارد کنید. این برنامه‌ها بدون اینترنت کار می‌کنند.</p>
      <div class="row" style="align-items:flex-start">
        <div style="flex:0 0 auto"><div class="qr" id="qr"></div></div>
        <div>
          <label>کلید دستی (اگر اسکن ممکن نبود)</label>
          <div class="secret" style="font-size:.9rem"><?= e(implode(' ', str_split($secret, 4))) ?></div>
          <label>کد شش‌رقمی</label>
          <input type="text" name="totp" class="ltr" inputmode="numeric" maxlength="6" required>
        </div>
      </div>
      <p style="margin-top:14px"><button class="btn gold">ساخت مدیر و پایان نصب</button></p>
    </form>
  </div>
  <script src="../assets/js/qrcode.js"></script>
  <script>
    var q = qrcode(0, 'M'); q.addData(<?= json_encode(Totp::uri($secret, 'admin@' . ($_SERVER['HTTP_HOST'] ?? 'divan'))) ?>); q.make();
    document.getElementById('qr').innerHTML = q.createSvgTag(5, 2);
  </script>

<?php elseif ($step === 4): ?>
  <div class="card">
    <h2>نصب کامل شد</h2>
    <div class="alert warn"><b>کلید پشتیبان را همین حالا جایی امن نگه دارید.</b> بدون آن، فایل‌های پشتیبان در هاست دیگری باز نمی‌شوند. این کلید بعداً هم از «پشتیبان‌گیری» در پنل (فقط مدیر اصلی) دیده می‌شود.</div>
    <div class="secret"><?= e((string)App::cfg('backup_key')) ?></div>
    <h3 style="margin-top:16px">نشانی‌ها</h3>
    <div class="table-wrap"><table>
      <tr><td>پنل مدیریت</td><td class="ltr"><a href="<?= e($base) ?>/admin/"><?= e($base) ?>/admin/</a></td></tr>
      <tr><td>API برنامه</td><td class="ltr"><?= e($base) ?>/api/index.php?r=v1/ping</td></tr>
      <tr><td>سایت</td><td class="ltr"><a href="<?= e($base) ?>/"><?= e($base) ?>/</a></td></tr>
      <tr><td>کرون روزانه (اختیاری)</td><td class="ltr" style="font-size:.8rem">php <?= e(App::$root) ?>/cron.php</td></tr>
    </table></div>
    <p style="margin-top:14px"><a class="btn gold" href="../admin/">ورود به پنل مدیریت</a></p>
    <p class="muted">نصب‌کننده اکنون قفل است و دوباره اجرا نمی‌شود.</p>
  </div>
<?php unset($_SESSION['step'], $_SESSION['totp']); endif; ?>
</div>
</body>
</html>
