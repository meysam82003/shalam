<?php
/** Divan Emtiaz — public site: introduction, plans, account, support, privacy policy and terms. */

declare(strict_types=1);

require __DIR__ . '/app/bootstrap.php';
if (!App::installed()) { header('Location: install/'); exit; }
Db::pdo();
Migrations::ensure();

$secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
session_name('divan_site');
session_set_cookie_params(['lifetime' => 0, 'path' => '/', 'secure' => $secure, 'httponly' => true, 'samesite' => 'Lax']);
session_start();
header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: same-origin');

$page = (string)($_GET['p'] ?? 'home');
$user = !empty($_SESSION['uid']) ? Users::byId((int)$_SESSION['uid']) : null;
if ($user !== null && $user['status'] !== 'active') { $user = null; unset($_SESSION['uid']); }
if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16));
$csrf = '<input type="hidden" name="csrf" value="' . e($_SESSION['csrf']) . '">';
$error = ''; $notice = ''; $secretKey = null;

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    try {
        if (!hash_equals($_SESSION['csrf'], (string)($_POST['csrf'] ?? ''))) throw new ApiError('csrf', 'نشست منقضی شده؛ دوباره تلاش کنید.');
        $act = (string)($_POST['act'] ?? '');
        if ($act === 'login') {
            $u = Users::login((string)($_POST['username'] ?? ''), (string)($_POST['password'] ?? ''));
            session_regenerate_id(true); $_SESSION['uid'] = (int)$u['id'];
            header('Location: index.php?p=account'); exit;
        }
        if ($act === 'register') {
            if (($_POST['password'] ?? '') !== ($_POST['password2'] ?? '')) throw new ApiError('mismatch', 'تکرار رمز با رمز یکی نیست.');
            [$u, $secretKey] = Users::register((string)($_POST['username'] ?? ''), (string)($_POST['password'] ?? ''), trim((string)($_POST['email'] ?? '')) ?: null);
            session_regenerate_id(true); $_SESSION['uid'] = (int)$u['id']; $user = $u;
            $page = 'account';
        }
        if ($act === 'recover') {
            [$u, $secretKey] = Users::recover((string)($_POST['username'] ?? ''), (string)($_POST['recovery_key'] ?? ''), (string)($_POST['new_password'] ?? ''));
            session_regenerate_id(true); $_SESSION['uid'] = (int)$u['id']; $user = $u;
            $page = 'account';
        }
        if ($act === 'newkey' && $user !== null) { $secretKey = Users::newRecoveryKey($user, (string)($_POST['password'] ?? '')); $page = 'account'; }
        if ($act === 'logout') { session_unset(); session_destroy(); header('Location: index.php'); exit; }
        if ($act === 'support') {
            $id = Support::create($user === null ? null : (int)$user['id'], $_POST + ['username' => $user['username'] ?? ($_POST['username'] ?? '')]);
            $notice = 'درخواست شمارهٔ ' . fa_digits($id) . ' ثبت شد. پاسخ از راه تماسی که نوشتید داده می‌شود.';
        }
    } catch (ApiError $e) {
        $error = $e->getMessage();
    }
}

$store = in_array($_GET['store'] ?? '', Plans::STORES, true) ? $_GET['store'] : 'bazaar';
$title = ['home' => 'دیوان امتیاز', 'account' => 'حساب من', 'login' => 'ورود', 'register' => 'ساخت حساب', 'recover' => 'بازیابی حساب', 'support' => 'پشتیبانی', 'privacy' => 'حریم خصوصی', 'terms' => 'شرایط استفاده'][$page] ?? 'دیوان امتیاز';
?><!doctype html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title><?= e($title) ?> — دیوان امتیاز</title><meta name="description" content="دیوان امتیاز؛ داور و دفتر امتیاز بازی‌های ورق ایرانی: شلم، منفی، هزارتایی و بیشتر.">
<link rel="icon" href="assets/img/logo.png"><link rel="stylesheet" href="assets/css/divan.css"></head>
<body>
<header class="site-head"><div class="wrap">
  <nav><a class="logo" href="index.php"><img src="assets/img/logo.png" alt="">دیوان امتیاز</a>
    <a href="index.php#plans">اشتراک Pro</a><a href="index.php?p=support">پشتیبانی</a>
    <?= $user ? '<a href="index.php?p=account">حساب من</a>' : '<a href="index.php?p=login">ورود</a>' ?></nav>
  <?php if ($page === 'home'): ?>
  <div class="hero"><div><h1>داور و دفتر امتیاز بازی‌های ورق</h1>
    <p>شلم، منفی، هزارتایی، دو لو گشنیز، راوی مافیا، پانتومیم و جاسوس؛ با ذخیرهٔ خودکار، تاریخچه، رنک‌بندی و لیگ. همه‌چیز بدون اینترنت کار می‌کند.</p>
    <p><a class="btn gold" href="#plans">آشنایی با Pro</a></p></div>
    <div style="text-align:center"><img src="assets/img/logo.png" alt="" style="width:180px;border-radius:40px;box-shadow:0 20px 50px rgba(0,0,0,.35)"></div></div>
  <?php else: ?><h1 style="color:#fff;margin-top:24px"><?= e($title) ?></h1><?php endif; ?>
</div></header>
<main class="site-main"><div class="wrap">
<?php if ($error): ?><div class="alert bad"><?= e($error) ?></div><?php endif; ?>
<?php if ($notice): ?><div class="alert ok"><?= e($notice) ?></div><?php endif; ?>
<?php if ($secretKey): ?><div class="card"><h2>کلید بازیابی شما</h2>
  <p>این کلید تنها راه بازگرداندن حساب در صورت فراموشی رمز است. آن را بنویسید یا در جای امنی ذخیره کنید؛ دوباره نمایش داده نمی‌شود.</p>
  <div class="secret"><?= e($secretKey) ?></div></div><?php endif; ?>

<?php switch ($page):
case 'account':
    if ($user === null) { echo '<div class="card"><p>برای دیدن اشتراک <a href="index.php?p=login">وارد شوید</a>.</p></div>'; break; }
    $sum = Entitlements::summary((int)$user['id']);
    $purchases = Purchases::forUser((int)$user['id']); ?>
  <div class="grid g2">
    <div class="card"><h2>اشتراک من</h2>
      <p>حساب: <b class="ltr"><?= e($user['username']) ?></b></p>
      <?php if ($sum['pro']): ?>
        <p><span class="badge gold">Pro فعال</span> <?= $sum['lifetime'] ? 'مادام‌العمر' : 'تا ' . e(jdate(Clock::sql((int)$sum['expires_at']))) . ' — ' . fa_digits((int)$sum['days_left']) . ' روز باقی‌مانده' ?></p>
        <p>برنامه در این مدت بدون تبلیغ است.</p>
      <?php else: ?><p><span class="badge">رایگان</span> اشتراک فعالی ندارید. خرید از داخل برنامه و با پرداخت بازار یا مایکت انجام می‌شود.</p><?php endif; ?>
      <form method="post"><?= $csrf ?><input type="hidden" name="act" value="logout"><button class="btn ghost sm">خروج</button></form>
    </div>
    <div class="card"><h2>کلید بازیابی تازه</h2>
      <p class="hint">اگر کلید بازیابی را گم کرده‌اید ولی رمز را دارید، کلید تازه بسازید؛ کلید قبلی باطل می‌شود.</p>
      <form method="post"><?= $csrf ?><input type="hidden" name="act" value="newkey"><label>رمز عبور</label><input type="password" name="password" required><p><button class="btn">ساخت کلید تازه</button></p></form>
    </div>
  </div>
  <div class="card"><h2>سابقهٔ خرید</h2><div class="table-wrap"><table><thead><tr><th>پلن</th><th>فروشگاه</th><th>شمارهٔ سفارش</th><th>مبلغ</th><th>وضعیت</th><th>تاریخ</th></tr></thead><tbody>
  <?php foreach ($purchases as $p): $v = Purchases::publicView($p); ?><tr><td><?= e($v['plan_title']) ?></td><td><?= e(['bazaar' => 'بازار', 'myket' => 'مایکت'][$p['store']] ?? $p['store']) ?></td><td class="ltr"><?= e($p['order_id'] ?? '—') ?></td><td><?= $p['amount_toman'] === null ? '—' : money($p['amount_toman']) ?></td>
    <td><?= e(['verified' => 'تأییدشده', 'signature_verified' => 'تأییدشده (در انتظار بررسی نهایی)', 'pending' => 'در انتظار تأیید', 'rejected' => 'ردشده', 'refunded' => 'بازپرداخت', 'revoked' => 'باطل'][$p['status']] ?? $p['status']) ?></td><td><?= e(jdate($p['created_at'])) ?></td></tr><?php endforeach;
  if (!$purchases) echo '<tr><td colspan="6" class="muted">خریدی ثبت نشده.</td></tr>'; ?>
  </tbody></table></div></div>
<?php break;
case 'login': ?>
  <div class="card" style="max-width:460px"><form method="post"><?= $csrf ?><input type="hidden" name="act" value="login">
    <label>نام کاربری</label><input type="text" name="username" class="ltr" required autocomplete="username">
    <label>رمز عبور</label><input type="password" name="password" required autocomplete="current-password">
    <p><button class="btn gold" style="width:100%">ورود</button></p></form>
    <p><a href="index.php?p=register">ساخت حساب</a> — <a href="index.php?p=recover">رمز را فراموش کرده‌ام</a></p></div>
<?php break;
case 'register': ?>
  <div class="card" style="max-width:520px"><p class="hint">برای استفادهٔ رایگان از برنامه حساب لازم نیست. حساب فقط برای خرید Pro و نگهداری آن روی گوشی‌های دیگر لازم است. شمارهٔ تلفن نمی‌خواهیم.</p>
    <form method="post"><?= $csrf ?><input type="hidden" name="act" value="register">
    <label>نام کاربری (حروف لاتین، رقم، نقطه یا زیرخط)</label><input type="text" name="username" class="ltr" required minlength="3" maxlength="32" autocomplete="username">
    <label>رمز عبور (دست‌کم ۸ نویسه با حرف و رقم)</label><input type="password" name="password" required minlength="8" autocomplete="new-password">
    <label>تکرار رمز</label><input type="password" name="password2" required minlength="8" autocomplete="new-password">
    <label>ایمیل (اختیاری)</label><input type="email" name="email" class="ltr">
    <p><button class="btn gold" style="width:100%">ساخت حساب</button></p></form></div>
<?php break;
case 'recover': ?>
  <div class="card" style="max-width:520px"><form method="post"><?= $csrf ?><input type="hidden" name="act" value="recover">
    <label>نام کاربری</label><input type="text" name="username" class="ltr" required>
    <label>کلید بازیابی</label><input type="text" name="recovery_key" class="ltr" required placeholder="XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX">
    <label>رمز تازه</label><input type="password" name="new_password" required minlength="8" autocomplete="new-password">
    <p><button class="btn gold" style="width:100%">بازیابی</button></p></form>
    <p class="hint">پس از بازیابی، از همهٔ گوشی‌ها خارج می‌شوید و کلید بازیابی تازه‌ای می‌گیرید. اگر کلید را هم ندارید، <a href="index.php?p=support">درخواست پشتیبانی</a> ثبت کنید و شمارهٔ سفارش خرید را بنویسید.</p></div>
<?php break;
case 'support': ?>
  <div class="card" style="max-width:640px"><p><?= e(Settings::get('support_text', '')) ?></p>
    <form method="post"><?= $csrf ?><input type="hidden" name="act" value="support">
    <div class="row"><div><label>موضوع</label><select name="kind"><?php foreach (Support::KINDS as $k => $l) echo '<option value="' . $k . '">' . $l . '</option>'; ?></select></div>
      <div><label>نام کاربری حساب</label><input type="text" name="username" class="ltr" value="<?= e($user['username'] ?? '') ?>"></div></div>
    <div class="row"><div><label>فروشگاه خرید</label><select name="store"><option value="">—</option><option value="bazaar">بازار</option><option value="myket">مایکت</option></select></div>
      <div><label>شمارهٔ سفارش (از سابقهٔ خرید فروشگاه)</label><input type="text" name="order_id" class="ltr"></div></div>
    <label>راه تماس (ایمیل یا شماره؛ فقط برای پاسخ)</label><input type="text" name="contact" required>
    <label>شرح</label><textarea name="message" required minlength="10"></textarea>
    <p><button class="btn gold">ثبت درخواست</button></p></form></div>
<?php break;
case 'privacy': ?>
  <div class="card"><h2>حریم خصوصی</h2>
    <p>دیوان امتیاز بدون حساب و بدون اینترنت کار می‌کند و امتیازها، بازیکنان، عکس‌ها و تاریخچهٔ بازی فقط روی گوشی شما ذخیره می‌شوند.</p>
    <p><b>حساب کاربری (اختیاری):</b> فقط نام کاربری، رمز عبور (به‌صورت درهم‌سازی‌شده) و در صورت تمایل ایمیل نگهداری می‌شود. شمارهٔ تلفن دریافت نمی‌شود. کلید بازیابی فقط به‌صورت درهم‌سازی‌شده ذخیره می‌شود.</p>
    <p><b>خرید:</b> برای تأیید و نگهداری اشتراک، شمارهٔ سفارش، شناسهٔ محصول، زمان و مبلغ خرید که بازار یا مایکت ارائه می‌کند ذخیره می‌شود. اطلاعات کارت بانکی هیچ‌وقت به ما نمی‌رسد.</p>
    <p><b>دستگاه‌ها:</b> برای نمایش فهرست نشست‌ها، نام دستگاه، نسخهٔ برنامه، فروشگاه و آخرین نشانی IP نگهداری می‌شود.</p>
    <p><b>تبلیغات:</b> فقط برای کاربران رایگان و فقط در جایگاه‌های غیرحساس نمایش داده می‌شود. شبکهٔ تبلیغاتی تپسل ممکن است طبق سیاست خود شناسهٔ تبلیغاتی دستگاه را به‌کار ببرد. برنامه تعداد نمایش و کلیک را بدون نام کاربر می‌شمارد.</p>
    <p><b>پشتیبان ابری (اختیاری):</b> اگر خودتان روشنش کنید، نسخهٔ رمزنگاری‌شدهٔ پشتیبان بازی‌ها روی سرور نگهداری می‌شود و با خاموش کردن آن حذف می‌شود.</p>
    <p>برای حذف حساب یا دریافت داده‌ها درخواست پشتیبانی ثبت کنید.</p></div>
<?php break;
case 'terms': ?>
  <div class="card"><h2>شرایط استفاده</h2>
    <p>اشتراک Pro از داخل برنامه و فقط با پرداخت فروشگاهی که برنامه را از آن نصب کرده‌اید (بازار یا مایکت) خریده می‌شود. قیمت نمایش‌داده همان مبلغی است که فروشگاه دریافت می‌کند.</p>
    <p>اشتراک‌های زمان‌دار به‌طور خودکار تمدید نمی‌شوند؛ خرید دوباره به انتهای اشتراک فعلی اضافه می‌شود. اشتراک مادام‌العمر یک بار پرداخت می‌شود.</p>
    <p>اشتراک به حساب کاربری شما متصل است و با ورود به همان حساب روی گوشی دیگر برمی‌گردد. بازپرداخت طبق قوانین فروشگاه انجام می‌شود و پس از بازپرداخت، اشتراک مربوط باطل می‌شود.</p>
    <p>برنامه بدون اشتراک هم قابل استفاده است. داده‌هایی که ساخته‌اید با پایان اشتراک پاک یا قفل نمی‌شوند.</p></div>
<?php break;
default:
    $offers = Plans::offers($store); ?>
  <div class="grid g3">
    <div class="card"><h3>داوری کامل</h3><p>محاسبهٔ خودکار امتیاز شلم، منفی و هزارتایی با ثبت، ویرایش و حذف دست.</p></div>
    <div class="card"><h3>بدون اینترنت</h3><p>همهٔ بازی‌ها و تاریخچه آفلاین کار می‌کنند. اشتراک Pro هم تا چند هفته بدون اینترنت معتبر می‌ماند.</p></div>
    <div class="card"><h3>حساب بدون شماره</h3><p>فقط نام کاربری و رمز؛ با کلید بازیابی، حساب هیچ‌وقت از دست نمی‌رود.</p></div>
  </div>
  <h2 id="plans" style="margin-top:20px">اشتراک Pro <small class="muted">— بدون تبلیغ و با همهٔ قابلیت‌ها</small></h2>
  <p class="row" style="flex:0"><a class="btn sm <?= $store === 'bazaar' ? '' : 'ghost' ?>" href="index.php?store=bazaar#plans">قیمت در بازار</a> <a class="btn sm <?= $store === 'myket' ? '' : 'ghost' ?>" href="index.php?store=myket#plans">قیمت در مایکت</a></p>
  <div class="grid g4">
  <?php foreach ($offers as $o): ?>
    <div class="card"><?php if ($o['badge_text']): ?><span class="badge" style="background:<?= e($o['badge_color'] ?: '#c39a4a') ?>;color:#fff;border:0"><?= e($o['badge_text']) ?></span><?php endif; ?>
      <h3><?= e($o['title']) ?></h3>
      <div class="price"><?= money($o['final_price']) ?><?php if ($o['discount_percent'] > 0): ?><del><?= money($o['base_price']) ?></del><?php endif; ?></div>
      <p class="muted"><?= e($o['description']) ?></p>
      <?php if (!$o['purchasable']): ?><p class="hint">به‌زودی در این فروشگاه</p><?php endif; ?>
    </div>
  <?php endforeach; ?>
  </div>
  <p class="hint">خرید فقط از داخل برنامه انجام می‌شود.</p>
<?php endswitch; ?>
</div></main>
<footer class="footer"><a href="index.php?p=privacy">حریم خصوصی</a> · <a href="index.php?p=terms">شرایط استفاده</a> · <a href="index.php?p=support">پشتیبانی</a><br>© دیوان امتیاز</footer>
</body></html>
