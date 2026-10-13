<?php
/** Divan Emtiaz admin panel — session, two-step login, roles, CSRF, layout and small UI helpers. */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

final class Admin
{
    public static ?array $me = null;

    /** What each role may open or change. */
    public const ROLES = [
        'owner' => 'مدیر اصلی',
        'manager' => 'مدیر',
        'support' => 'پشتیبان',
        'analyst' => 'تحلیلگر',
    ];
    private const ACCESS = [
        'owner' => ['*'],
        'manager' => ['dashboard', 'promos', 'users', 'user', 'plans', 'subscriptions', 'transactions', 'transaction', 'features', 'campaigns', 'ads', 'ad', 'placements', 'media', 'reports', 'stores', 'bridge', 'paymethods', 'adnetwork', 'notices', 'gifts', 'support', 'ticket', 'audit', 'account', 'backups'],
        'support' => ['dashboard', 'users', 'user', 'subscriptions', 'transactions', 'transaction', 'gifts', 'support', 'ticket', 'account'],
        'analyst' => ['dashboard', 'reports', 'subscriptions', 'transactions', 'ads', 'placements', 'account'],
    ];
    /** Pages that only the owner may change (managers may still view). */
    public const OWNER_WRITE = ['admins', 'backups-restore', 'backup-key'];

    public static function start(): void
    {
        $secure = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https');
        session_name('divan_admin');
        session_set_cookie_params(['lifetime' => 0, 'path' => '/', 'secure' => $secure, 'httponly' => true, 'samesite' => 'Strict']);
        session_start();
        header('X-Frame-Options: DENY');
        header('Cache-Control: no-store');
        header("Content-Security-Policy: default-src 'self'; img-src 'self' data: blob:; media-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; frame-ancestors 'none'");
        $fp = hash('sha256', (string)($_SERVER['HTTP_USER_AGENT'] ?? ''));
        if (!empty($_SESSION['admin_id'])) {
            $idle = Clock::now() - (int)($_SESSION['seen'] ?? 0);
            if ($idle > 1800 || ($_SESSION['fp'] ?? '') !== $fp) { session_unset(); session_regenerate_id(true); }
            else {
                $a = Db::one('SELECT * FROM {admins} WHERE id = ? AND active = 1', [$_SESSION['admin_id']]);
                if ($a === null) session_unset(); else { self::$me = $a; $_SESSION['seen'] = Clock::now(); }
            }
        }
        $_SESSION['fp'] = $fp;
    }

    public static function can(string $page): bool
    {
        if (self::$me === null) return false;
        $acc = self::ACCESS[self::$me['role']] ?? [];
        return in_array('*', $acc, true) || in_array($page, $acc, true);
    }

    public static function owner(): bool { return (self::$me['role'] ?? '') === 'owner'; }

    public static function require(string $page): void
    {
        if (!self::can($page)) {
            http_response_code(403);
            self::layout('دسترسی ندارید', function () { echo '<div class="alert bad">نقش شما به این بخش دسترسی ندارد.</div>'; }, '');
            exit;
        }
    }

    /** Read-only roles cannot change anything. */
    public static function writable(): bool { return in_array(self::$me['role'] ?? '', ['owner', 'manager', 'support'], true); }

    public static function csrf(): string
    {
        if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16));
        return $_SESSION['csrf'];
    }

    public static function csrfField(): string { return '<input type="hidden" name="csrf" value="' . e(self::csrf()) . '">'; }

    public static function checkPost(bool $anyRole = false): void
    {
        if ($_SERVER['REQUEST_METHOD'] !== 'POST') return;
        if (!hash_equals((string)($_SESSION['csrf'] ?? ''), (string)($_POST['csrf'] ?? ''))) {
            http_response_code(400);
            exit('نشست منقضی شده است؛ صفحه را دوباره باز کنید.');
        }
        if (self::$me !== null && !$anyRole && !self::writable()) {
            http_response_code(403);
            exit('نقش شما فقط اجازهٔ مشاهده دارد.');
        }
    }

    public static function flash(string $msg, string $kind = 'ok'): void { $_SESSION['flash'][] = [$kind, $msg]; }

    public static function redirect(string $query): void
    {
        header('Location: index.php?' . $query);
        exit;
    }

    /** Logs a change made from the panel; financial and access changes need a reason. */
    public static function audit(string $action, string $entity, $id, string $reason = '', $before = null, $after = null): void
    {
        Audit::log('admin', (int)self::$me['id'], $action, $entity, $id, $reason, $before, $after);
    }

    public static function requireReason(): string
    {
        // Optional: the change is always logged with who made it and when; a note only adds context.
        $r = trim((string)($_POST['reason'] ?? ''));
        return $r !== '' ? mb_substr($r, 0, 300) : 'تغییر از پنل';
    }

    // ------------------------------------------------------------ login with password and TOTP
    public static function login(): void
    {
        $error = '';
        $stage = empty($_SESSION['pending_admin']) ? 'password' : 'totp';
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            if (!hash_equals((string)($_SESSION['csrf'] ?? ''), (string)($_POST['csrf'] ?? ''))) $error = 'نشست منقضی شده است.';
            elseif (!RateLimit::hit('admin-login:' . Http::ip(), 20, 900)) $error = 'تلاش‌های ورود زیاد است؛ ۱۵ دقیقه بعد دوباره تلاش کنید.';
            elseif ($stage === 'password') {
                $a = Db::one('SELECT * FROM {admins} WHERE username = ?', [trim((string)($_POST['username'] ?? ''))]);
                if ($a !== null && $a['locked_until'] !== null && Clock::ts($a['locked_until']) > Clock::now()) $error = 'حساب مدیر موقتاً قفل است.';
                elseif ($a === null || !$a['active'] || !password_verify((string)($_POST['password'] ?? ''), $a['password_hash'])) {
                    if ($a !== null) {
                        $f = (int)$a['failed'] + 1;
                        Db::update('admins', ['failed' => $f, 'locked_until' => $f >= 6 ? Clock::sql(Clock::now() + 900) : null], 'id = ?', [$a['id']]);
                    }
                    Audit::security('admin_login_failed', null, (string)($_POST['username'] ?? ''));
                    $error = 'نام کاربری یا رمز درست نیست.';
                } else {
                    $_SESSION['pending_admin'] = (int)$a['id'];
                    $_SESSION['pending_at'] = Clock::now();
                    $stage = 'totp';
                }
            } else {
                $a = Db::one('SELECT * FROM {admins} WHERE id = ?', [(int)$_SESSION['pending_admin']]);
                $secret = $a === null ? null : Crypto::decrypt($a['totp_enc']);
                $step = ($secret === null || Clock::now() - (int)($_SESSION['pending_at'] ?? 0) > 300) ? null : Totp::verify($secret, (string)($_POST['code'] ?? ''), (int)$a['totp_last_step']);
                if ($step === null) {
                    Audit::security('admin_totp_failed', null, $a['username'] ?? '');
                    $error = 'کد دومرحله‌ای درست نیست.';
                    if (Clock::now() - (int)($_SESSION['pending_at'] ?? 0) > 300) { unset($_SESSION['pending_admin']); $stage = 'password'; }
                } else {
                    session_regenerate_id(true);
                    unset($_SESSION['pending_admin'], $_SESSION['pending_at']);
                    $_SESSION['admin_id'] = (int)$a['id'];
                    $_SESSION['seen'] = Clock::now();
                    Db::update('admins', ['failed' => 0, 'locked_until' => null, 'last_login_at' => Clock::sql(), 'totp_last_step' => $step], 'id = ?', [$a['id']]);
                    Audit::log('admin', (int)$a['id'], 'admin_login', 'admin', $a['id']);
                    header('Location: index.php');
                    exit;
                }
            }
        }
        ?><!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex">
        <title>ورود به پنل دیوان امتیاز</title><link rel="stylesheet" href="../assets/css/divan.css"></head>
        <body><div class="install" style="max-width:420px;margin-top:8vh">
        <div style="text-align:center;margin-bottom:14px"><img src="../assets/img/logo.png" alt="" style="width:72px;height:72px;border-radius:18px"><h1 style="margin-top:8px">پنل مدیریت دیوان</h1></div>
        <div class="card">
        <?php if ($error): ?><div class="alert bad"><?= e($error) ?></div><?php endif; ?>
        <form method="post" autocomplete="off"><?= self::csrfField() ?>
        <?php if ($stage === 'password'): ?>
          <label>نام کاربری</label><input type="text" name="username" class="ltr" required autofocus>
          <label>رمز عبور</label><input type="password" name="password" class="ltr" required>
          <p><button class="btn gold" style="width:100%">ادامه</button></p>
        <?php else: ?>
          <p class="muted">کد شش‌رقمی برنامهٔ احراز هویت را وارد کنید.</p>
          <label>کد دومرحله‌ای</label><input type="text" name="code" class="ltr" inputmode="numeric" maxlength="6" required autofocus>
          <p><button class="btn gold" style="width:100%">ورود</button></p>
        <?php endif; ?>
        </form></div></div></body></html><?php
        exit;
    }

    // ------------------------------------------------------------ layout
    public const MENU = [
        ['', 'گزارش‌ها'],
        ['dashboard', 'داشبورد'],
        ['reports', 'درآمد و عملکرد'],
        ['', 'کاربران و فروش'],
        ['users', 'کاربران'],
        ['subscriptions', 'اشتراک‌ها'],
        ['transactions', 'پرداخت‌ها و تراکنش‌ها'],
        ['support', 'پشتیبانی اشتراک‌ها'],
        ['gifts', 'هدیه و دسترسی ویژه'],
        ['promos', 'کد تخفیف و کد هدیه'],
        ['', 'قیمت و دسترسی'],
        ['plans', 'پلن‌ها و قیمت‌ها'],
        ['campaigns', 'کمپین‌های تخفیف'],
        ['features', 'قابلیت‌های رایگان و Pro'],
        ['', 'تبلیغات'],
        ['ads', 'تبلیغات'],
        ['placements', 'جایگاه‌های تبلیغ'],
        ['media', 'رسانه‌ها'],
        ['adnetwork', 'شبکهٔ تبلیغاتی'],
        ['notices', 'اعلان‌های تبلیغاتی'],
        ['', 'تنظیمات'],
        ['stores', 'بازار و مایکت'],
        ['bridge', 'واسط پرداخت ایران'],
        ['paymethods', 'روش‌های پرداخت'],
        ['backups', 'پشتیبان‌گیری'],
        ['audit', 'امنیت و گزارش تغییرات'],
        ['admins', 'مدیران'],
        ['account', 'حساب من'],
    ];

    public static function layout(string $title, callable $body, string $active, array $actions = []): void
    {
        ?><!doctype html><html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="robots" content="noindex">
        <title><?= e($title) ?> — پنل دیوان امتیاز</title><link rel="stylesheet" href="../assets/css/divan.css"></head>
        <body><div class="shell">
        <aside class="side">
          <div class="brand"><img src="../assets/img/logo.png" alt=""><div><b>دیوان امتیاز</b><span>پنل مدیریت</span></div></div>
          <nav>
          <?php foreach (self::MENU as [$k, $label]):
              if ($k === '') { echo '<div class="group">' . e($label) . '</div>'; continue; }
              if (!self::can($k)) continue; ?>
            <a href="index.php?p=<?= e($k) ?>" class="<?= $active === $k ? 'on' : '' ?>"><?= e($label) ?></a>
          <?php endforeach; ?>
            <div class="group"><?= e(self::$me['username'] ?? '') ?> — <?= e(self::ROLES[self::$me['role'] ?? ''] ?? '') ?></div>
            <form method="post" action="index.php?p=logout" style="padding:0 12px"><?= self::csrfField() ?><button class="btn sm ghost" style="color:#dfe4ef;border-color:#3a4669">خروج</button></form>
          </nav>
        </aside>
        <main class="main">
          <div class="topbar"><button class="btn ghost sm menu-toggle" onclick="document.body.classList.toggle('menu')">☰</button><h1><?= e($title) ?></h1>
            <?php foreach ($actions as $a) echo $a; ?></div>
          <?php foreach ($_SESSION['flash'] ?? [] as [$k, $m]) echo '<div class="alert ' . e($k) . '">' . e($m) . '</div>'; unset($_SESSION['flash']); ?>
          <?php $body(); ?>
        </main></div>
        <script src="../assets/js/admin.js"></script>
        </body></html><?php
    }

    // ------------------------------------------------------------ helpers
    public static function statusBadge(string $s): string
    {
        $map = [
            'verified' => ['ok', 'تأییدشده'], 'signature_verified' => ['info', 'تأیید امضا (در انتظار API)'], 'pending' => ['warn', 'در انتظار تأیید'],
            'rejected' => ['bad', 'ردشده'], 'refunded' => ['bad', 'بازپرداخت'], 'revoked' => ['bad', 'باطل'], 'active' => ['ok', 'فعال'],
            'open' => ['warn', 'باز'], 'in_review' => ['info', 'در بررسی'], 'resolved' => ['ok', 'حل‌شده'], 'disabled' => ['bad', 'غیرفعال'],
            'ok' => ['ok', 'آماده'], 'price_sync_pending' => ['warn', 'در انتظار همگام‌سازی قیمت'], 'no_sku' => ['bad', 'شناسهٔ محصول ندارد'], 'off_in_store' => ['', 'در این فروشگاه عرضه نمی‌شود'], 'inactive' => ['', 'غیرفعال'], 'store_disabled' => ['', 'فروشگاه خاموش'],
            'free' => ['ok', 'رایگان'], 'pro' => ['gold', 'فقط Pro'], 'limited' => ['info', 'رایگان محدود'], 'gift' => ['info', 'فقط هدیه'],
        ];
        [$k, $l] = $map[$s] ?? ['', $s];
        return '<span class="badge ' . $k . '">' . e($l) . '</span>';
    }

    public static function storeName(?string $s): string { return ['bazaar' => 'بازار', 'myket' => 'مایکت', 'web' => 'وب'][$s ?? ''] ?? '—'; }

    public static function pager(int $total, int $page, int $per, string $query): string
    {
        $pages = max(1, (int)ceil($total / $per));
        if ($pages <= 1) return '';
        $h = '<div class="pager">';
        for ($i = max(1, $page - 3); $i <= min($pages, $page + 3); $i++) {
            $h .= '<a class="btn sm ' . ($i === $page ? '' : 'ghost') . '" href="index.php?' . e($query) . '&pg=' . $i . '">' . fa_digits($i) . '</a>';
        }
        return $h . '</div>';
    }

    public static function csv(string $name, array $header, array $rows): void
    {
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="' . $name . '-' . gmdate('Ymd') . '.csv"');
        $out = fopen('php://output', 'w');
        fwrite($out, "\xEF\xBB\xBF");
        fputcsv($out, $header);
        foreach ($rows as $r) {
            // Neutralise spreadsheet formulas in exported text.
            fputcsv($out, array_map(fn($v) => is_string($v) && preg_match('/^[=+\-@]/', $v) ? "'" . $v : $v, $r));
        }
        fclose($out);
        exit;
    }

    /**
     * Searchable multi-select list of accounts (no typing usernames by hand): search, filters,
     * «select all shown» and a counter. Posts the chosen ids as $name[].
     */
    public static function userPicker(string $name, array $selected = []): string
    {
        $now = Clock::sql();
        $rows = Db::all("SELECT u.id, u.username, u.created_at, u.last_login_at,
            EXISTS (SELECT 1 FROM {entitlements} e WHERE e.user_id = u.id AND e.status = 'active' AND e.starts_at <= ? AND (e.expires_at IS NULL OR e.expires_at > ?)) pro,
            EXISTS (SELECT 1 FROM {purchases} p WHERE p.user_id = u.id AND p.status IN ('verified','signature_verified')) buyer
            FROM {users} u WHERE u.status = 'active' ORDER BY u.id DESC LIMIT 5000", [$now, $now]);
        $week = Clock::now() - 7 * 86400;
        $sel = array_map('strval', $selected);
        $h = '<div class="picker" data-name="' . e($name) . '">';
        $h .= '<div class="row" style="gap:6px;margin-bottom:6px"><input type="search" class="pk-q" placeholder="جست‌وجوی نام کاربری…" style="flex:2">';
        $h .= '<select class="pk-f" style="flex:1"><option value="">همه</option><option value="free">رایگان</option><option value="pro">Pro فعال</option><option value="buyer">خریدار</option><option value="new">۷ روز اخیر</option><option value="active">فعال در ۷ روز اخیر</option><option value="sel">فقط انتخاب‌شده‌ها</option></select></div>';
        $h .= '<div class="row" style="gap:6px;margin-bottom:6px;flex:0"><button type="button" class="btn sm ghost pk-all">انتخاب همهٔ نمایش‌داده‌ها</button><button type="button" class="btn sm ghost pk-none">پاک کردن انتخاب</button><span class="muted pk-count"></span></div>';
        $h .= '<div class="pk-list" style="max-height:260px;overflow:auto;border:1px solid var(--line);border-radius:10px;padding:6px;background:#fff">';
        foreach ($rows as $r) {
            $tags = [];
            if ($r['pro']) $tags[] = 'pro'; else $tags[] = 'free';
            if ($r['buyer']) $tags[] = 'buyer';
            if (Clock::ts($r['created_at']) > $week) $tags[] = 'new';
            if ($r['last_login_at'] !== null && Clock::ts($r['last_login_at']) > $week) $tags[] = 'active';
            $h .= '<label class="check pk-item" data-u="' . e(strtolower($r['username'])) . '" data-t="' . implode(' ', $tags) . '"><input type="checkbox" name="' . e($name) . '[]" value="' . (int)$r['id'] . '"' . (in_array((string)$r['id'], $sel, true) ? ' checked' : '') . '> <span class="ltr">' . e($r['username']) . '</span>'
                . ($r['pro'] ? ' <span class="badge gold">Pro</span>' : '') . ($r['buyer'] ? ' <span class="badge ok">خریدار</span>' : '') . '</label>';
        }
        if (!$rows) $h .= '<p class="muted">هنوز حسابی ساخته نشده است.</p>';
        return $h . '</div></div>';
    }

    /** Ids posted by a [userPicker], checked against the database. */
    public static function pickedUsers(string $name): array
    {
        $ids = array_values(array_unique(array_filter(array_map('intval', (array)($_POST[$name] ?? [])))));
        if (!$ids) return [];
        return Db::all('SELECT * FROM {users} WHERE id IN (' . implode(',', array_fill(0, count($ids), '?')) . ')', $ids);
    }

    public static function dtInput(?string $sql): string
    {
        return $sql === null ? '' : gmdate('Y-m-d\TH:i', (int)Clock::ts($sql) + 12600);
    }

    /** datetime-local (Tehran time) → UTC SQL. */
    public static function dtParse(?string $v): ?string
    {
        $v = trim((string)$v);
        if ($v === '') return null;
        $ts = strtotime($v . ' UTC');
        return $ts === false ? null : Clock::sql($ts - 12600);
    }
}
