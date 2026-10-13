<?php
/** Admin pages: dashboard, users, subscriptions, transactions, support, gifts and revenue reports. */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

final class SalesPages
{
    private const PER = 30;

    private static function pg(): int { return max(1, (int)($_GET['pg'] ?? 1)); }
    private static function q(string $k): string { return trim((string)($_GET[$k] ?? '')); }

    // ================================================================ dashboard
    public static function dashboard(): void
    {
        $now = Clock::sql();
        $monthStart = gmdate('Y-m-01 00:00:00', Clock::now());
        $k = [
            'users' => (int)Db::value('SELECT COUNT(*) FROM {users}'),
            'users7' => (int)Db::value('SELECT COUNT(*) FROM {users} WHERE created_at >= ?', [Clock::sql(Clock::now() - 7 * 86400)]),
            'pro' => (int)Db::value("SELECT COUNT(DISTINCT user_id) FROM {entitlements} WHERE status = 'active' AND starts_at <= ? AND (expires_at IS NULL OR expires_at > ?)", [$now, $now]),
            'lifetime' => (int)Db::value("SELECT COUNT(DISTINCT user_id) FROM {entitlements} WHERE status = 'active' AND expires_at IS NULL"),
            'gifts' => (int)Db::value("SELECT COUNT(DISTINCT user_id) FROM {user_grants} WHERE feature_k = 'pro' AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)", [$now]),
            'month' => (int)Db::value("SELECT COALESCE(SUM(amount_toman),0) FROM {purchases} WHERE status IN ('verified','signature_verified') AND created_at >= ?", [$monthStart]),
            'pending' => (int)Db::value("SELECT COUNT(*) FROM {purchases} WHERE status IN ('pending','signature_verified')"),
            'refunds30' => (int)Db::value("SELECT COUNT(*) FROM {purchases} WHERE status IN ('refunded','revoked') AND updated_at >= ?", [Clock::sql(Clock::now() - 30 * 86400)]),
            'tickets' => (int)Db::value("SELECT COUNT(*) FROM {support_requests} WHERE status IN ('open','in_review')"),
            'recoverFail' => (int)Db::value("SELECT COUNT(*) FROM {security_events} WHERE kind = 'recover_failed' AND created_at >= ?", [Clock::sql(Clock::now() - 7 * 86400)]),
            'fake' => (int)Db::value("SELECT COUNT(*) FROM {security_events} WHERE kind IN ('receipt_bad_signature','receipt_reuse','receipt_mismatch','payload_mismatch') AND created_at >= ?", [Clock::sql(Clock::now() - 30 * 86400)]),
        ];
        $days = [];
        for ($i = 13; $i >= 0; $i--) $days[gmdate('Y-m-d', Clock::now() - $i * 86400)] = 0;
        foreach (Db::all("SELECT DATE(created_at) d, SUM(amount_toman) s FROM {purchases} WHERE status IN ('verified','signature_verified') AND created_at >= ? GROUP BY DATE(created_at)", [Clock::sql(Clock::now() - 14 * 86400)]) as $r) {
            if (isset($days[$r['d']])) $days[$r['d']] = (int)$r['s'];
        }
        $byPlan = Db::all("SELECT pl.title, p.store, COUNT(*) n, COALESCE(SUM(p.amount_toman),0) s FROM {purchases} p LEFT JOIN {plans} pl ON pl.id = p.plan_id WHERE p.status IN ('verified','signature_verified') GROUP BY pl.title, p.store ORDER BY s DESC");
        $warnings = self::warnings();

        Admin::layout('داشبورد', function () use ($k, $days, $byPlan, $warnings) {
            foreach ($warnings as [$kind, $msg]) echo '<div class="alert ' . $kind . '">' . $msg . '</div>';
            ?>
            <div class="grid g4">
              <div class="kpi"><div class="l">کاربران دارای حساب</div><div class="v"><?= fa_digits($k['users']) ?></div><div class="t muted"><?= fa_digits($k['users7']) ?> حساب در ۷ روز اخیر</div></div>
              <div class="kpi"><div class="l">Pro فعال</div><div class="v"><?= fa_digits($k['pro']) ?></div><div class="t muted"><?= fa_digits($k['lifetime']) ?> مادام‌العمر — <?= fa_digits($k['gifts']) ?> هدیه</div></div>
              <div class="kpi"><div class="l">فروش تأییدشدهٔ این ماه</div><div class="v"><?= money($k['month']) ?></div><div class="t"><span class="badge ok">واقعی</span> از تراکنش‌های تأییدشده</div></div>
              <div class="kpi"><div class="l">در انتظار تأیید فروشگاه</div><div class="v"><?= fa_digits($k['pending']) ?></div><div class="t muted"><?= fa_digits($k['refunds30']) ?> بازپرداخت/ابطال در ۳۰ روز</div></div>
            </div>
            <div class="grid g3" style="margin-top:14px">
              <div class="kpi"><div class="l">درخواست پشتیبانی باز</div><div class="v"><?= fa_digits($k['tickets']) ?></div><div class="t"><a href="index.php?p=support">مشاهده</a></div></div>
              <div class="kpi"><div class="l">بازیابی ناموفق (۷ روز)</div><div class="v"><?= fa_digits($k['recoverFail']) ?></div><div class="t"><a href="index.php?p=audit&tab=security&kind=recover_failed">پیگیری</a></div></div>
              <div class="kpi"><div class="l">رسید جعلی/تکراری (۳۰ روز)</div><div class="v"><?= fa_digits($k['fake']) ?></div><div class="t"><a href="index.php?p=audit&tab=security">گزارش امنیتی</a></div></div>
            </div>
            <div class="grid g2" style="margin-top:14px">
              <div class="card"><h3>فروش ۱۴ روز اخیر</h3>
                <?php $max = max(1, max($days)); ?>
                <div class="bars"><?php foreach ($days as $d => $v): ?><div class="b" title="<?= e(jdate($d . ' 00:00:00', false) . ' — ' . money($v)) ?>" style="height:<?= max(1, (int)round($v * 100 / $max)) ?>%"></div><?php endforeach; ?></div>
                <div class="bars-labels"><?php foreach (array_keys($days) as $d) echo '<span>' . e(mb_substr(jdate($d . ' 00:00:00', false), 8)) . '</span>'; ?></div>
              </div>
              <div class="card"><h3>فروش به تفکیک پلن و فروشگاه</h3>
                <?php if (!$byPlan): ?><p class="muted">هنوز فروشی ثبت نشده است.</p><?php else: ?>
                <div class="table-wrap"><table><thead><tr><th>پلن</th><th>فروشگاه</th><th>تعداد</th><th>مبلغ</th></tr></thead><tbody>
                <?php foreach ($byPlan as $r): ?><tr><td><?= e($r['title'] ?? '—') ?></td><td><?= Admin::storeName($r['store']) ?></td><td><?= fa_digits($r['n']) ?></td><td><?= money($r['s']) ?></td></tr><?php endforeach; ?>
                </tbody></table></div><?php endif; ?>
              </div>
            </div>
            <?php
        }, 'dashboard');
    }

    /** Problems the owner should fix, shown on the dashboard. */
    private static function warnings(): array
    {
        $w = [];
        foreach (Plans::STORES as $s) {
            $cfg = Settings::get($s, []);
            if (empty($cfg['enabled'])) continue;
            foreach (Plans::all() as $p) {
                if (!$p['active']) continue;
                $o = Plans::offer($p, $s);
                if ($o['state'] === 'no_sku') { $w[] = ['warn', 'برای «' . e($p['title']) . '» در ' . Admin::storeName($s) . ' شناسهٔ محصول ثبت نشده؛ در اپ قابل خرید نیست. <a href="index.php?p=plans">پلن‌ها</a>']; }
            }
            if (PaymentBridge::route($s) === 'bridge') {
                $bs = (array)Settings::get('bridge_status', []);
                if (!PaymentBridge::ready()) $w[] = ['bad', Admin::storeName($s) . ' از راه واسط ایران بررسی می‌شود ولی نشانی یا کلید واسط ثبت نشده. <a href="index.php?p=bridge">واسط پرداخت</a>'];
                elseif (($bs['last_error_at'] ?? 0) > ($bs['last_ok_at'] ?? 0)) $w[] = ['bad', 'آخرین تماس با واسط پرداخت ایران ناموفق بود: ' . e($bs['last_error'] ?? '') . ' <a href="index.php?p=bridge">بررسی</a>'];
                continue;
            }
            $verify = $s === 'myket' ? Settings::secret('myket_access_token') : (Settings::secret('bazaar_api_secret') ?: Settings::secret('bazaar_refresh_token'));
            if ($verify === '') $w[] = ['warn', 'دسترسی صحت‌سنجی ' . Admin::storeName($s) . ' ثبت نشده؛ خریدها فقط با امضا تأیید موقت می‌شوند. <a href="index.php?p=stores">تنظیم</a>'];
        }
        $last = (int)Settings::get('last_backup_at', 0);
        if ($last < Clock::now() - 2 * 86400) $w[] = ['bad', 'بیش از دو روز است پشتیبان گرفته نشده. <a href="index.php?p=backups">پشتیبان‌گیری</a>'];
        $err = (string)Settings::get('last_backup_error', '');
        if ($err !== '') $w[] = ['bad', 'آخرین پشتیبان خودکار خطا داشت: ' . e($err)];
        $w = array_slice($w, 0, 8);
        return $w;
    }

    // ================================================================ users
    public static function users(): void
    {
        $q = self::q('q'); $filter = self::q('f');
        $where = ['1=1']; $params = [];
        if ($q !== '') { $where[] = '(u.username_norm LIKE ? OR u.email LIKE ? OR u.id = ?)'; $params[] = '%' . strtolower($q) . '%'; $params[] = '%' . $q . '%'; $params[] = (int)$q; }
        $now = Clock::sql();
        if ($filter === 'pro') { $where[] = "EXISTS (SELECT 1 FROM {entitlements} e WHERE e.user_id = u.id AND e.status = 'active' AND e.starts_at <= ? AND (e.expires_at IS NULL OR e.expires_at > ?))"; $params[] = $now; $params[] = $now; }
        if ($filter === 'free') { $where[] = "NOT EXISTS (SELECT 1 FROM {entitlements} e WHERE e.user_id = u.id AND e.status = 'active' AND (e.expires_at IS NULL OR e.expires_at > ?))"; $params[] = $now; }
        if ($filter === 'disabled') $where[] = "u.status <> 'active'";
        if ($filter === 'locked') { $where[] = 'u.locked_until > ?'; $params[] = $now; }
        $w = implode(' AND ', $where);
        if (isset($_GET['csv'])) {
            $rows = Db::all("SELECT u.id, u.username, u.email, u.status, u.created_at, u.last_login_at FROM {users} u WHERE $w ORDER BY u.id DESC LIMIT 50000", $params);
            Admin::csv('users', ['id', 'username', 'email', 'status', 'created_at_utc', 'last_login_utc'], array_map('array_values', $rows));
        }
        $total = (int)Db::value("SELECT COUNT(*) FROM {users} u WHERE $w", $params);
        $rows = Db::all("SELECT u.* FROM {users} u WHERE $w ORDER BY u.id DESC LIMIT " . self::PER . ' OFFSET ' . ((self::pg() - 1) * self::PER), $params);
        Admin::layout('کاربران', function () use ($rows, $total, $q, $filter) {
            ?>
            <form class="filters" method="get"><input type="hidden" name="p" value="users">
              <div><label>جست‌وجو</label><input type="search" name="q" value="<?= e($q) ?>" placeholder="نام کاربری، ایمیل یا شناسه"></div>
              <div><label>وضعیت</label><select name="f">
                <?php foreach (['' => 'همه', 'pro' => 'Pro فعال', 'free' => 'رایگان', 'locked' => 'قفل موقت', 'disabled' => 'غیرفعال'] as $k => $l) echo '<option value="' . $k . '"' . ($filter === $k ? ' selected' : '') . '>' . $l . '</option>'; ?>
              </select></div>
              <div><button class="btn">اعمال</button> <a class="btn ghost" href="index.php?p=users&q=<?= e(urlencode($q)) ?>&f=<?= e($filter) ?>&csv=1">خروجی CSV</a></div>
            </form>
            <p class="muted"><?= fa_digits($total) ?> حساب</p>
            <div class="table-wrap"><table><thead><tr><th>#</th><th>نام کاربری</th><th>اشتراک</th><th>وضعیت</th><th>ثبت‌نام</th><th>آخرین ورود</th></tr></thead><tbody>
            <?php foreach ($rows as $u): $s = Entitlements::summary((int)$u['id']); ?>
              <tr><td><?= fa_digits($u['id']) ?></td><td><a href="index.php?p=user&id=<?= (int)$u['id'] ?>"><b class="ltr"><?= e($u['username']) ?></b></a></td>
                <td><?= $s['pro'] ? ($s['lifetime'] ? '<span class="badge gold">مادام‌العمر</span>' : '<span class="badge ok">Pro تا ' . e(jdate(Clock::sql((int)$s['expires_at']), false)) . '</span>') : '<span class="badge">رایگان</span>' ?></td>
                <td><?= $u['status'] === 'active' ? (($u['locked_until'] && Clock::ts($u['locked_until']) > Clock::now()) ? '<span class="badge warn">قفل موقت</span>' : '<span class="badge ok">فعال</span>') : '<span class="badge bad">غیرفعال</span>' ?></td>
                <td><?= e(jdate($u['created_at'], false)) ?></td><td><?= e(jdate($u['last_login_at'])) ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="6" class="muted">حسابی پیدا نشد.</td></tr>'; ?>
            </tbody></table></div>
            <?= Admin::pager($total, self::pg(), self::PER, 'p=users&q=' . urlencode($q) . '&f=' . $filter) ?>
            <?php
        }, 'users');
    }

    public static function user(): void
    {
        $id = (int)($_GET['id'] ?? 0);
        $u = Users::byId($id);
        if ($u === null) { Admin::flash('کاربر پیدا نشد.', 'bad'); Admin::redirect('p=users'); }
        if ($_SERVER['REQUEST_METHOD'] === 'POST') { self::userAction($u); return; }
        $sum = Entitlements::summary($id);
        $purchases = Purchases::forUser($id);
        $ents = Entitlements::all($id);
        $grants = Db::all('SELECT * FROM {user_grants} WHERE user_id = ? ORDER BY id DESC', [$id]);
        $sessions = Sessions::list($id);
        $sec = Db::all('SELECT * FROM {security_events} WHERE user_id = ? OR username = ? ORDER BY id DESC LIMIT 30', [$id, $u['username']]);
        $actions = Db::all('SELECT a.*, ad.username admin_name FROM {support_actions} a LEFT JOIN {admins} ad ON ad.id = a.admin_id WHERE a.user_id = ? ORDER BY a.id DESC LIMIT 30', [$id]);
        $tickets = Db::all('SELECT * FROM {support_requests} WHERE user_id = ? OR username = ? ORDER BY id DESC LIMIT 20', [$id, $u['username']]);
        $backup = Db::one('SELECT size, updated_at FROM {user_backups} WHERE user_id = ?', [$id]);
        $secret = $_SESSION['show_once'] ?? null; unset($_SESSION['show_once']);
        $features = Features::all();

        Admin::layout('حساب ' . $u['username'], function () use ($u, $sum, $purchases, $ents, $grants, $sessions, $sec, $actions, $tickets, $backup, $secret, $features) {
            if ($secret) echo '<div class="card"><h3>' . e($secret[0]) . '</h3><div class="secret">' . e($secret[1]) . '</div><p class="hint">فقط همین یک بار نمایش داده می‌شود؛ آن را از راه امن به صاحب حساب بدهید.</p></div>';
            ?>
            <div class="grid g4">
              <div class="kpi"><div class="l">اشتراک</div><div class="v" style="font-size:1.1rem"><?= $sum['pro'] ? ($sum['lifetime'] ? 'مادام‌العمر' : 'Pro فعال') : 'رایگان' ?></div><div class="t muted"><?= $sum['pro'] && !$sum['lifetime'] ? 'تا ' . e(jdate(Clock::sql((int)$sum['expires_at']))) . ' — ' . fa_digits((int)$sum['days_left']) . ' روز' : '' ?></div></div>
              <div class="kpi"><div class="l">وضعیت حساب</div><div class="v" style="font-size:1.1rem"><?= $u['status'] === 'active' ? 'فعال' : 'غیرفعال' ?></div><div class="t muted"><?= $u['locked_until'] && Clock::ts($u['locked_until']) > Clock::now() ? 'قفل موقت تا ' . e(jdate($u['locked_until'])) : fa_digits($u['failed_logins']) . ' ورود ناموفق' ?></div></div>
              <div class="kpi"><div class="l">ثبت‌نام</div><div class="v" style="font-size:1.1rem"><?= e(jdate($u['created_at'], false)) ?></div><div class="t muted">آخرین ورود: <?= e(jdate($u['last_login_at'])) ?></div></div>
              <div class="kpi"><div class="l">کلید بازیابی</div><div class="v" style="font-size:1.1rem"><?= $u['recovery_hash'] ? 'دارد' : 'ندارد' ?></div><div class="t muted">ساخته‌شده: <?= e(jdate($u['recovery_created_at'])) ?></div></div>
            </div>
            <div class="grid g2" style="margin-top:14px">
            <div class="card"><h3>اقدام‌های پشتیبانی</h3>
              <p class="hint">هر اقدام با نام شما و دلیل نوشته‌شده در گزارش تغییرات ثبت می‌شود. پیش از بازنشانی رمز، مالکیت حساب را با شمارهٔ سفارش خرید یا اطلاعاتی که فقط صاحب حساب می‌داند بررسی کنید.</p>
              <form method="post"><?= Admin::csrfField() ?>
                <label>اقدام</label>
                <select name="action" id="ua-action">
                  <option value="gift">هدیه (Pro کامل یا یک قابلیت)</option>
                  <option value="reset_password">بازنشانی رمز (رمز موقت + کلید بازیابی جدید)</option>
                  <option value="unlock">برداشتن قفل ورود</option>
                  <option value="revoke_sessions">خروج از همهٔ دستگاه‌ها</option>
                  <option value="<?= $u['status'] === 'active' ? 'disable' : 'enable' ?>"><?= $u['status'] === 'active' ? 'غیرفعال کردن حساب' : 'فعال کردن حساب' ?></option>
                  <option value="transfer">انتقال خریدها به حساب دیگر</option>
                  <option value="release_device">آزاد کردن گوشی اشتراک (فعال‌شدن روی گوشی بعدی)</option>
                  <option value="reset_moves">صفر کردن شمارش انتقال‌های گوشی</option>
                  <option value="note">یادداشت داخلی</option>
                </select>
                <div data-for="gift">
                  <label>چه چیزی هدیه شود</label>
                  <select name="feature"><option value="pro" selected>Pro کامل — همهٔ قابلیت‌ها و بدون تبلیغ</option>
                    <optgroup label="فقط یک قابلیت (اختیاری)"><?php foreach ($features as $f) echo '<option value="' . e($f['k']) . '">' . e($f['title']) . '</option>'; ?></optgroup></select>
                  <label>مدت</label>
                  <div class="row"><div><input type="number" name="days" min="1" max="3650" placeholder="تعداد روز"></div><div class="hint" style="flex:2">خالی = دائمی (مادام‌العمر)</div></div>
                </div>
                <div data-for="transfer" style="display:none">
                  <label>حساب مقصد</label>
                  <input type="text" name="target" class="ltr" list="ua-users" placeholder="نام کاربری را بنویسید یا از فهرست انتخاب کنید">
                  <datalist id="ua-users"><?php foreach (Db::all('SELECT username FROM {users} WHERE id <> ? ORDER BY id DESC LIMIT 3000', [$u['id']]) as $o) echo '<option value="' . e($o['username']) . '">'; ?></datalist>
                </div>
                <label>یادداشت (اختیاری)</label><textarea name="reason" placeholder="مثلاً: مالکیت با شمارهٔ سفارش بازار ۱۲۳۴ تأیید شد"></textarea>
                <p><button class="btn gold" data-confirm="این اقدام انجام شود؟">ثبت اقدام</button></p>
              </form>
            </div>
            <div class="card"><h3>اطلاعات حساب</h3>
              <p>نام کاربری: <b class="ltr"><?= e($u['username']) ?></b> — شناسه <?= fa_digits($u['id']) ?></p>
              <p>ایمیل (اختیاری): <span class="ltr"><?= e($u['email'] ?: '—') ?></span></p>
              <p>گوشی فعال اشتراک: <b><?= $u['pro_device'] ? e($u['pro_device_name'] ?: 'نامشخص') . '</b> از ' . e(jdate($u['pro_device_at'])) : 'هنوز به گوشی‌ای وصل نشده</b>' ?>
                — انتقال در ۳۰ روز اخیر: <?= fa_digits((Clock::ts($u['device_moves_since']) ?? 0) > Clock::now() - 30 * 86400 ? (int)$u['device_moves'] : 0) ?> از <?= fa_digits((int)Settings::get('device_moves_limit', 3)) ?></p>
              <p>پشتیبان ابری بازی‌ها: <?= $backup ? fa_digits(number_format((int)$backup['size'])) . ' بایت — ' . e(jdate($backup['updated_at'])) : 'ندارد' ?></p>
              <?php if ($u['note']): ?><p>یادداشت: <?= nl2br(e($u['note'])) ?></p><?php endif; ?>
              <h3 style="margin-top:14px">دستگاه‌ها و نشست‌ها</h3>
              <div class="table-wrap"><table><thead><tr><th>دستگاه</th><th>فروشگاه</th><th>آخرین فعالیت</th><th>وضعیت</th></tr></thead><tbody>
              <?php foreach ($sessions as $s): ?><tr><td><?= e(($s['device'] ?? '') ?: '—') ?> <small class="ltr"><?= e($s['app_version'] ?? '') ?></small></td><td><?= Admin::storeName($s['store'] ?? null) ?></td><td><?= e(jdate(Clock::sql((int)$s['last_seen_at']))) ?></td><td><span class="badge ok">فعال</span></td></tr><?php endforeach;
              if (!$sessions) echo '<tr><td colspan="4" class="muted">نشست فعالی ندارد.</td></tr>'; ?>
              </tbody></table></div>
            </div>
            </div>

            <div class="card"><h3>خریدها</h3><div class="table-wrap"><table><thead><tr><th>#</th><th>پلن / SKU</th><th>فروشگاه</th><th>شمارهٔ سفارش</th><th>مبلغ</th><th>وضعیت</th><th>تاریخ</th></tr></thead><tbody>
            <?php foreach ($purchases as $p): $v = Purchases::publicView($p); ?>
              <tr><td><a href="index.php?p=transaction&id=<?= (int)$p['id'] ?>"><?= fa_digits($p['id']) ?></a></td><td><?= e($v['plan_title']) ?></td><td><?= Admin::storeName($p['store']) ?></td><td class="ltr"><?= e($p['order_id'] ?? '—') ?></td><td><?= $p['amount_toman'] === null ? '—' : money($p['amount_toman']) ?></td><td><?= Admin::statusBadge($p['status']) ?></td><td><?= e(jdate($p['created_at'])) ?></td></tr>
            <?php endforeach; if (!$purchases) echo '<tr><td colspan="7" class="muted">خریدی ندارد.</td></tr>'; ?>
            </tbody></table></div></div>

            <div class="grid g2">
            <div class="card"><h3>دوره‌های اشتراک</h3><div class="table-wrap"><table><thead><tr><th>پلن</th><th>منبع</th><th>از</th><th>تا</th><th>وضعیت</th></tr></thead><tbody>
            <?php foreach ($ents as $en): ?><tr><td><?= e($en['plan_code']) ?></td><td><?= e(['purchase' => 'خرید', 'gift' => 'هدیه', 'transfer' => 'انتقال', 'store_sub' => 'اشتراک فروشگاه'][$en['source']] ?? $en['source']) ?></td><td><?= e(jdate($en['starts_at'], false)) ?></td><td><?= $en['expires_at'] === null ? 'دائمی' : e(jdate($en['expires_at'], false)) ?></td><td><?= Admin::statusBadge($en['status']) ?></td></tr><?php endforeach;
            if (!$ents) echo '<tr><td colspan="5" class="muted">—</td></tr>'; ?>
            </tbody></table></div>
            <h3 style="margin-top:14px">هدیه‌ها و دسترسی‌های ویژه</h3>
            <div class="table-wrap"><table><thead><tr><th>قابلیت</th><th>تا</th><th>دلیل</th><th></th></tr></thead><tbody>
            <?php foreach ($grants as $g): ?><tr><td><?= e($g['feature_k'] === 'pro' ? 'Pro کامل' : $g['feature_k']) ?></td><td><?= $g['expires_at'] === null ? 'دائمی' : e(jdate($g['expires_at'], false)) ?></td><td><?= e($g['reason']) ?></td>
              <td><?php if ($g['revoked_at'] === null): ?><form method="post" class="row" style="gap:4px"><?= Admin::csrfField() ?><input type="hidden" name="action" value="revoke_grant"><input type="hidden" name="grant" value="<?= (int)$g['id'] ?>"><input type="text" name="reason" placeholder="یادداشت (اختیاری)" style="min-width:110px"><button class="btn sm red">لغو</button></form><?php else: ?><span class="badge">لغوشده</span><?php endif; ?></td></tr><?php endforeach;
            if (!$grants) echo '<tr><td colspan="4" class="muted">—</td></tr>'; ?>
            </tbody></table></div></div>
            <div class="card"><h3>رویدادهای امنیتی حساب</h3><div class="table-wrap"><table><thead><tr><th>رویداد</th><th>IP</th><th>زمان</th></tr></thead><tbody>
            <?php foreach ($sec as $s): ?><tr><td><?= e(ConfigPages::eventName($s['kind'])) ?></td><td class="ltr"><?= e($s['ip']) ?></td><td><?= e(jdate($s['created_at'])) ?></td></tr><?php endforeach;
            if (!$sec) echo '<tr><td colspan="3" class="muted">—</td></tr>'; ?>
            </tbody></table></div>
            <h3 style="margin-top:14px">سابقهٔ اقدام‌های پشتیبانی</h3>
            <div class="table-wrap"><table><thead><tr><th>اقدام</th><th>دلیل</th><th>مدیر</th><th>زمان</th></tr></thead><tbody>
            <?php foreach ($actions as $a): ?><tr><td><?= e($a['action']) ?></td><td><?= e($a['reason']) ?></td><td><?= e($a['admin_name'] ?? '—') ?></td><td><?= e(jdate($a['created_at'])) ?></td></tr><?php endforeach;
            if (!$actions) echo '<tr><td colspan="4" class="muted">—</td></tr>'; ?>
            </tbody></table></div>
            <?php if ($tickets): ?><h3 style="margin-top:14px">درخواست‌های پشتیبانی</h3><ul><?php foreach ($tickets as $t) echo '<li><a href="index.php?p=ticket&id=' . (int)$t['id'] . '">#' . fa_digits($t['id']) . ' — ' . e(Support::KINDS[$t['kind']] ?? $t['kind']) . '</a> ' . Admin::statusBadge($t['status']) . '</li>'; ?></ul><?php endif; ?>
            </div></div>
            <?php
        }, 'users');
    }

    private static function userAction(array $u): void
    {
        $action = (string)($_POST['action'] ?? '');
        $reason = Admin::requireReason();
        $adminId = (int)Admin::$me['id'];
        $id = (int)$u['id'];
        $back = 'p=user&id=' . $id;
        $giftOnly = in_array(Admin::$me['role'], ['owner', 'manager'], true);
        switch ($action) {
            case 'reset_password': {
                $temp = 'Dv-' . substr(Crypto::token(9), 0, 12) . '7';
                $key = Crypto::recoveryKey();
                Db::update('users', [
                    'password_hash' => Crypto::passwordHash($temp), 'recovery_hash' => Crypto::passwordHash(Crypto::normalizeRecovery($key)),
                    'recovery_created_at' => Clock::sql(), 'failed_logins' => 0, 'locked_until' => null, 'updated_at' => Clock::sql(),
                ], 'id = ?', [$id]);
                Db::update('sessions', ['revoked_at' => Clock::sql()], 'user_id = ? AND revoked_at IS NULL', [$id]);
                $_SESSION['show_once'] = ['رمز موقت و کلید بازیابی جدید', "رمز موقت: $temp\nکلید بازیابی: $key"];
                Audit::security('support_password_reset', $id, $u['username'], 'admin ' . Admin::$me['username']);
                break;
            }
            case 'unlock': Db::update('users', ['failed_logins' => 0, 'locked_until' => null], 'id = ?', [$id]); RateLimit::clear('login-user:' . Users::normalize($u['username'])); break;
            case 'revoke_sessions': Db::update('sessions', ['revoked_at' => Clock::sql()], 'user_id = ? AND revoked_at IS NULL', [$id]); break;
            case 'disable': Db::update('users', ['status' => 'disabled', 'updated_at' => Clock::sql()], 'id = ?', [$id]); Db::update('sessions', ['revoked_at' => Clock::sql()], 'user_id = ? AND revoked_at IS NULL', [$id]); break;
            case 'enable': Db::update('users', ['status' => 'active', 'updated_at' => Clock::sql()], 'id = ?', [$id]); break;
            case 'gift':
            case 'gift_pro':
            case 'grant_feature': {
                if (!$giftOnly) throw new ApiError('role', 'هدیه دادن فقط برای مدیر اصلی و مدیر مجاز است.');
                $feature = $action === 'gift_pro' ? 'pro' : ((string)($_POST['feature'] ?? 'pro') ?: 'pro');
                if ($feature !== 'pro' && Db::value('SELECT k FROM {features} WHERE k = ?', [$feature]) === null) throw new ApiError('feature', 'قابلیت نامعتبر است.');
                $days = (int)($_POST['days'] ?? 0);
                self::grant($id, $feature, $days > 0 ? $days : null, $reason);
                break;
            }
            case 'revoke_grant': {
                $g = Db::one('SELECT * FROM {user_grants} WHERE id = ? AND user_id = ?', [(int)($_POST['grant'] ?? 0), $id]);
                if ($g === null) throw new ApiError('grant', 'دسترسی پیدا نشد.');
                Db::update('user_grants', ['revoked_at' => Clock::sql()], 'id = ?', [$g['id']]);
                break;
            }
            case 'transfer': {
                if (!$giftOnly) throw new ApiError('role', 'انتقال خرید فقط برای مدیر اصلی و مدیر مجاز است.');
                $target = Users::byName((string)($_POST['target'] ?? ''));
                if ($target === null || (int)$target['id'] === $id) throw new ApiError('target', 'حساب مقصد پیدا نشد.');
                Db::tx(function () use ($id, $target) {
                    Db::update('purchases', ['user_id' => $target['id'], 'updated_at' => Clock::sql()], 'user_id = ?', [$id]);
                    Db::update('entitlements', ['user_id' => $target['id'], 'updated_at' => Clock::sql()], 'user_id = ?', [$id]);
                    Db::update('user_grants', ['user_id' => $target['id']], 'user_id = ? AND revoked_at IS NULL', [$id]);
                });
                Support::action(null, (int)$target['id'], $adminId, 'transfer_in', $reason, ['from' => $u['username']]);
                break;
            }
            case 'release_device': Db::update('users', ['pro_device' => null, 'pro_device_name' => null, 'pro_device_at' => null], 'id = ?', [$id]); break;
            case 'reset_moves': Db::update('users', ['device_moves' => 0, 'device_moves_since' => null], 'id = ?', [$id]); break;
            case 'note': Db::update('users', ['note' => trim(($u['note'] ?? '') . "\n" . gmdate('Y-m-d') . ' ' . Admin::$me['username'] . ': ' . $reason)], 'id = ?', [$id]); break;
            default: throw new ApiError('action', 'اقدام نامعتبر است.');
        }
        Support::action(isset($_GET['ticket']) ? (int)$_GET['ticket'] : null, $id, $adminId, $action, $reason, ['feature' => $_POST['feature'] ?? null, 'days' => $_POST['days'] ?? null, 'target' => $_POST['target'] ?? null]);
        Admin::audit('user_' . $action, 'user', $id, $reason);
        Admin::flash('اقدام ثبت شد.');
        Admin::redirect($back);
    }

    public static function grant(int $userId, string $feature, ?int $days, string $reason): void
    {
        Db::insert('user_grants', [
            'user_id' => $userId, 'feature_k' => $feature, 'expires_at' => $days === null ? null : Clock::sql(Clock::now() + $days * 86400),
            'reason' => mb_substr($reason, 0, 300), 'admin_id' => (int)Admin::$me['id'], 'created_at' => Clock::sql(),
        ]);
    }

    // ================================================================ subscriptions
    public static function subscriptions(): void
    {
        $f = self::q('f') ?: 'active'; $store = self::q('store'); $q = self::q('q');
        $now = Clock::sql();
        $where = ['1=1']; $params = [];
        if ($f === 'active') { $where[] = "e.status = 'active' AND e.starts_at <= ? AND (e.expires_at IS NULL OR e.expires_at > ?)"; $params[] = $now; $params[] = $now; }
        if ($f === 'upcoming') { $where[] = "e.status = 'active' AND e.starts_at > ?"; $params[] = $now; }
        if ($f === 'expired') { $where[] = "e.status = 'active' AND e.expires_at <= ?"; $params[] = $now; }
        if ($f === 'expiring') { $where[] = "e.status = 'active' AND e.expires_at > ? AND e.expires_at <= ?"; $params[] = $now; $params[] = Clock::sql(Clock::now() + 7 * 86400); }
        if ($f === 'lifetime') $where[] = "e.status = 'active' AND e.expires_at IS NULL";
        if ($f === 'ended') $where[] = "e.status <> 'active'";
        if (in_array($store, Plans::STORES, true)) { $where[] = 'p.store = ?'; $params[] = $store; }
        if ($q !== '') { $where[] = 'u.username_norm LIKE ?'; $params[] = '%' . strtolower($q) . '%'; }
        $w = implode(' AND ', $where);
        $sql = "FROM {entitlements} e JOIN {users} u ON u.id = e.user_id LEFT JOIN {purchases} p ON p.id = e.purchase_id WHERE $w";
        if (isset($_GET['csv'])) {
            $rows = Db::all("SELECT e.id, u.username, e.plan_code, e.source, p.store, e.starts_at, e.expires_at, e.status $sql ORDER BY e.id DESC LIMIT 50000", $params);
            Admin::csv('subscriptions', ['id', 'username', 'plan', 'source', 'store', 'starts_utc', 'expires_utc', 'status'], array_map('array_values', $rows));
        }
        $total = (int)Db::value("SELECT COUNT(*) $sql", $params);
        $rows = Db::all("SELECT e.*, u.username, p.store $sql ORDER BY e.id DESC LIMIT " . self::PER . ' OFFSET ' . ((self::pg() - 1) * self::PER), $params);
        $giftsActive = Db::all("SELECT g.*, u.username FROM {user_grants} g JOIN {users} u ON u.id = g.user_id WHERE g.feature_k = 'pro' AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at > ?) ORDER BY g.id DESC LIMIT 20", [$now]);
        Admin::layout('اشتراک‌ها', function () use ($rows, $total, $f, $store, $q, $giftsActive) {
            $qs = 'p=subscriptions&f=' . $f . '&store=' . $store . '&q=' . urlencode($q);
            ?>
            <form class="filters" method="get"><input type="hidden" name="p" value="subscriptions">
              <div><label>وضعیت</label><select name="f"><?php foreach (['active' => 'فعال', 'expiring' => 'رو به پایان (۷ روز)', 'upcoming' => 'رزرو (پس از دورهٔ فعلی)', 'lifetime' => 'مادام‌العمر', 'expired' => 'منقضی', 'ended' => 'باطل/بازپرداخت', 'all' => 'همه'] as $k => $l) echo '<option value="' . $k . '"' . ($f === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select></div>
              <div><label>فروشگاه</label><select name="store"><option value="">همه</option><option value="bazaar"<?= $store === 'bazaar' ? ' selected' : '' ?>>بازار</option><option value="myket"<?= $store === 'myket' ? ' selected' : '' ?>>مایکت</option></select></div>
              <div><label>کاربر</label><input type="search" name="q" value="<?= e($q) ?>"></div>
              <div><button class="btn">اعمال</button> <a class="btn ghost" href="index.php?<?= e($qs) ?>&csv=1">خروجی CSV</a></div>
            </form>
            <p class="muted"><?= fa_digits($total) ?> دوره</p>
            <div class="table-wrap"><table><thead><tr><th>کاربر</th><th>پلن</th><th>منبع</th><th>فروشگاه</th><th>شروع</th><th>پایان</th><th>باقی‌مانده</th><th>وضعیت</th></tr></thead><tbody>
            <?php foreach ($rows as $r): $left = $r['expires_at'] === null ? null : (int)ceil((Clock::ts($r['expires_at']) - Clock::now()) / 86400); ?>
              <tr><td><a class="ltr" href="index.php?p=user&id=<?= (int)$r['user_id'] ?>"><?= e($r['username']) ?></a></td><td><?= e($r['plan_code']) ?></td><td><?= e($r['source']) ?></td><td><?= Admin::storeName($r['store']) ?></td>
                <td><?= e(jdate($r['starts_at'], false)) ?></td><td><?= $r['expires_at'] === null ? 'دائمی' : e(jdate($r['expires_at'], false)) ?></td>
                <td><?= $left === null ? '∞' : ($left > 0 ? fa_digits($left) . ' روز' : '—') ?></td><td><?= Admin::statusBadge($r['status']) ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="8" class="muted">موردی نیست.</td></tr>'; ?>
            </tbody></table></div>
            <?= Admin::pager($total, self::pg(), self::PER, $qs) ?>
            <?php if ($giftsActive): ?><div class="card" style="margin-top:16px"><h3>Pro هدیه (فعال)</h3><div class="table-wrap"><table><thead><tr><th>کاربر</th><th>تا</th><th>دلیل</th></tr></thead><tbody>
            <?php foreach ($giftsActive as $g) echo '<tr><td><a class="ltr" href="index.php?p=user&id=' . (int)$g['user_id'] . '">' . e($g['username']) . '</a></td><td>' . ($g['expires_at'] === null ? 'دائمی' : e(jdate($g['expires_at'], false))) . '</td><td>' . e($g['reason']) . '</td></tr>'; ?>
            </tbody></table></div></div><?php endif;
        }, 'subscriptions');
    }

    // ================================================================ transactions
    public static function transactions(): void
    {
        $status = self::q('status'); $store = self::q('store'); $q = self::q('q'); $from = self::q('from'); $to = self::q('to');
        $where = ['1=1']; $params = [];
        if ($status !== '') { $where[] = 'p.status = ?'; $params[] = $status; }
        if (in_array($store, Plans::STORES, true)) { $where[] = 'p.store = ?'; $params[] = $store; }
        if ($q !== '') { $where[] = '(u.username_norm LIKE ? OR p.order_id = ? OR p.sku = ?)'; $params[] = '%' . strtolower($q) . '%'; $params[] = $q; $params[] = $q; }
        if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $from)) { $where[] = 'p.created_at >= ?'; $params[] = $from . ' 00:00:00'; }
        if (preg_match('/^\d{4}-\d{2}-\d{2}$/', $to)) { $where[] = 'p.created_at <= ?'; $params[] = $to . ' 23:59:59'; }
        $w = implode(' AND ', $where);
        $sql = "FROM {purchases} p JOIN {users} u ON u.id = p.user_id LEFT JOIN {plans} pl ON pl.id = p.plan_id WHERE $w";
        if (isset($_GET['csv'])) {
            $rows = Db::all("SELECT p.id, u.username, p.store, p.sku, pl.code, p.order_id, p.amount_toman, p.status, p.verify_method, p.created_at, p.purchase_time $sql ORDER BY p.id DESC LIMIT 50000", $params);
            Admin::csv('transactions', ['id', 'username', 'store', 'sku', 'plan', 'order_id', 'amount_toman', 'status', 'verify_method', 'created_utc', 'purchase_time_utc'], array_map('array_values', $rows));
        }
        $total = (int)Db::value("SELECT COUNT(*) $sql", $params);
        $sum = (int)Db::value("SELECT COALESCE(SUM(CASE WHEN p.status IN ('verified','signature_verified') THEN p.amount_toman ELSE 0 END),0) $sql", $params);
        $rows = Db::all("SELECT p.*, u.username, pl.title plan_title $sql ORDER BY p.id DESC LIMIT " . self::PER . ' OFFSET ' . ((self::pg() - 1) * self::PER), $params);
        Admin::layout('پرداخت‌ها و تراکنش‌ها', function () use ($rows, $total, $sum, $status, $store, $q, $from, $to) {
            $qs = 'p=transactions&status=' . $status . '&store=' . $store . '&q=' . urlencode($q) . '&from=' . $from . '&to=' . $to;
            ?>
            <form class="filters" method="get"><input type="hidden" name="p" value="transactions">
              <div><label>وضعیت</label><select name="status"><option value="">همه</option><?php foreach (['verified', 'signature_verified', 'pending', 'rejected', 'refunded', 'revoked'] as $s) echo '<option value="' . $s . '"' . ($status === $s ? ' selected' : '') . '>' . strip_tags(Admin::statusBadge($s)) . '</option>'; ?></select></div>
              <div><label>فروشگاه</label><select name="store"><option value="">همه</option><option value="bazaar"<?= $store === 'bazaar' ? ' selected' : '' ?>>بازار</option><option value="myket"<?= $store === 'myket' ? ' selected' : '' ?>>مایکت</option></select></div>
              <div><label>کاربر / سفارش / SKU</label><input type="search" name="q" value="<?= e($q) ?>"></div>
              <div><label>از تاریخ (میلادی)</label><input type="date" name="from" value="<?= e($from) ?>"></div>
              <div><label>تا تاریخ</label><input type="date" name="to" value="<?= e($to) ?>"></div>
              <div><button class="btn">اعمال</button> <a class="btn ghost" href="index.php?<?= e($qs) ?>&csv=1">خروجی CSV</a></div>
            </form>
            <p class="muted"><?= fa_digits($total) ?> تراکنش — جمع تأییدشده: <b><?= money($sum) ?></b></p>
            <div class="table-wrap"><table><thead><tr><th>#</th><th>کاربر</th><th>پلن</th><th>فروشگاه</th><th>سفارش</th><th>مبلغ</th><th>روش تأیید</th><th>وضعیت</th><th>تاریخ</th></tr></thead><tbody>
            <?php foreach ($rows as $p): ?>
              <tr><td><a href="index.php?p=transaction&id=<?= (int)$p['id'] ?>"><?= fa_digits($p['id']) ?></a></td><td><a class="ltr" href="index.php?p=user&id=<?= (int)$p['user_id'] ?>"><?= e($p['username']) ?></a></td><td><?= e($p['plan_title'] ?? $p['sku']) ?></td><td><?= Admin::storeName($p['store']) ?></td>
                <td class="ltr"><?= e($p['order_id'] ?? '—') ?></td><td><?= $p['amount_toman'] === null ? '—' : money($p['amount_toman']) ?></td><td><?= e(['store_api' => 'API فروشگاه', 'signature' => 'امضای فروشگاه', 'manual' => 'دستی'][$p['verify_method'] ?? ''] ?? '—') ?></td><td><?= Admin::statusBadge($p['status']) ?></td><td><?= e(jdate($p['created_at'])) ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="9" class="muted">تراکنشی نیست.</td></tr>'; ?>
            </tbody></table></div>
            <?= Admin::pager($total, self::pg(), self::PER, $qs) ?>
            <?php
        }, 'transactions');
    }

    public static function transaction(): void
    {
        $id = (int)($_GET['id'] ?? 0);
        $p = Db::one('SELECT * FROM {purchases} WHERE id = ?', [$id]);
        if ($p === null) { Admin::flash('تراکنش پیدا نشد.', 'bad'); Admin::redirect('p=transactions'); }
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $reason = Admin::requireReason();
            $action = (string)($_POST['action'] ?? '');
            $before = ['status' => $p['status']];
            if ($action === 'recheck') {
                Db::update('purchases', ['next_check_at' => Clock::sql(Clock::now() - 1)], 'id = ?', [$id]);
                Purchases::recheck(1);
            } elseif ($action === 'approve') {
                if (!in_array(Admin::$me['role'], ['owner', 'manager'], true)) throw new ApiError('role', 'تأیید دستی فقط برای مدیر اصلی و مدیر مجاز است.');
                $plan = Plans::byId((int)$p['plan_id']);
                if ($plan === null) throw new ApiError('plan', 'پلن این خرید مشخص نیست.');
                Db::update('purchases', ['status' => 'verified', 'verify_method' => 'manual', 'last_verified_at' => Clock::sql(), 'next_check_at' => Clock::sql(Clock::now() + 7 * 86400), 'note' => $reason, 'updated_at' => Clock::sql()], 'id = ?', [$id]);
                Entitlements::grantForPurchase((int)$p['user_id'], $plan, $id);
            } elseif ($action === 'refunded' || $action === 'revoked') {
                if (!in_array(Admin::$me['role'], ['owner', 'manager'], true)) throw new ApiError('role', 'ابطال فقط برای مدیر اصلی و مدیر مجاز است.');
                Db::update('purchases', ['status' => $action, 'next_check_at' => null, 'note' => $reason, 'updated_at' => Clock::sql()], 'id = ?', [$id]);
                Entitlements::revokeForPurchase($id, $action, $reason);
            } else throw new ApiError('action', 'اقدام نامعتبر است.');
            $after = Db::one('SELECT status FROM {purchases} WHERE id = ?', [$id]);
            Support::action(null, (int)$p['user_id'], (int)Admin::$me['id'], 'purchase_' . $action, $reason, ['purchase' => $id]);
            Admin::audit('purchase_' . $action, 'purchase', $id, $reason, $before, $after);
            Admin::flash('انجام شد. وضعیت فعلی: ' . strip_tags(Admin::statusBadge((string)$after['status'])));
            Admin::redirect('p=transaction&id=' . $id);
        }
        $u = Users::byId((int)$p['user_id']);
        $plan = $p['plan_id'] ? Plans::byId((int)$p['plan_id']) : null;
        $raw = $p['raw_enc'] ? Crypto::decrypt($p['raw_enc']) : null;
        $ent = Db::one('SELECT * FROM {entitlements} WHERE purchase_id = ?', [$id]);
        $log = Db::all("SELECT * FROM {audit_log} WHERE entity = 'purchase' AND entity_id = ? ORDER BY id DESC", [(string)$id]);
        Admin::layout('تراکنش #' . fa_digits($id), function () use ($p, $u, $plan, $raw, $ent, $log) {
            ?>
            <div class="grid g2">
            <div class="card"><h3>جزئیات</h3>
              <p>کاربر: <a class="ltr" href="index.php?p=user&id=<?= (int)$p['user_id'] ?>"><?= e($u['username'] ?? '—') ?></a></p>
              <p>پلن: <?= e($plan['title'] ?? '—') ?> — SKU <code><?= e($p['sku']) ?></code> (<?= e($p['product_type']) ?>)</p>
              <p>فروشگاه: <?= Admin::storeName($p['store']) ?> — شمارهٔ سفارش: <code><?= e($p['order_id'] ?? '—') ?></code></p>
              <p>مبلغ: <?= $p['amount_toman'] === null ? '—' : money($p['amount_toman']) ?></p>
              <p>وضعیت: <?= Admin::statusBadge($p['status']) ?> — روش تأیید: <?= e($p['verify_method'] ?? '—') ?> — تلاش‌ها: <?= fa_digits($p['verify_attempts']) ?></p>
              <p>زمان خرید: <?= e(jdate($p['purchase_time'])) ?> — ثبت: <?= e(jdate($p['created_at'])) ?></p>
              <p>آخرین بررسی فروشگاه: <?= e(jdate($p['last_verified_at'])) ?> — بررسی بعدی: <?= e(jdate($p['next_check_at'])) ?></p>
              <p>مصرف‌شده در فروشگاه: <?= $p['consumed'] ? 'بله' : 'خیر' ?></p>
              <?php if ($p['note']): ?><div class="alert info"><?= e($p['note']) ?></div><?php endif; ?>
              <p>دورهٔ اشتراک: <?= $ent ? e(jdate($ent['starts_at'], false)) . ' تا ' . ($ent['expires_at'] === null ? 'دائمی' : e(jdate($ent['expires_at'], false))) . ' ' . Admin::statusBadge($ent['status']) : '—' ?></p>
            </div>
            <div class="card"><h3>اقدام</h3>
              <form method="post"><?= Admin::csrfField() ?>
                <select name="action">
                  <option value="recheck">بررسی دوباره با فروشگاه</option>
                  <option value="approve">تأیید دستی (فقط با مدرک پرداخت)</option>
                  <option value="refunded">ثبت بازپرداخت</option>
                  <option value="revoked">ابطال</option>
                </select>
                <label>یادداشت (اختیاری)</label><textarea name="reason"></textarea>
                <p><button class="btn gold" data-confirm="این اقدام روی خرید کاربر انجام شود؟">ثبت</button></p>
              </form>
              <h3>پاسخ خام فروشگاه</h3>
              <pre class="ltr" style="white-space:pre-wrap;max-height:260px;overflow:auto;background:#f6f4ee;padding:10px;border-radius:10px;font-size:.78rem"><?= e($raw ? json_encode(json_decode($raw, true), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) : '—') ?></pre>
            </div></div>
            <div class="card"><h3>سابقهٔ تغییرات این خرید</h3><div class="table-wrap"><table><thead><tr><th>اقدام</th><th>دلیل</th><th>توسط</th><th>زمان</th></tr></thead><tbody>
            <?php foreach ($log as $l) echo '<tr><td>' . e($l['action']) . '</td><td>' . e($l['reason']) . '</td><td>' . e($l['actor_type'] . ' #' . $l['actor_id']) . '</td><td>' . e(jdate($l['created_at'])) . '</td></tr>';
            if (!$log) echo '<tr><td colspan="4" class="muted">—</td></tr>'; ?>
            </tbody></table></div></div>
            <?php
        }, 'transactions');
    }

    // ================================================================ support
    public static function support(): void
    {
        $status = self::q('status') ?: 'open'; $kind = self::q('kind');
        $where = ['1=1']; $params = [];
        if ($status === 'open') $where[] = "status IN ('open','in_review')"; elseif ($status !== 'all') { $where[] = 'status = ?'; $params[] = $status; }
        if (isset(Support::KINDS[$kind])) { $where[] = 'kind = ?'; $params[] = $kind; }
        $w = implode(' AND ', $where);
        $total = (int)Db::value("SELECT COUNT(*) FROM {support_requests} WHERE $w", $params);
        $rows = Db::all("SELECT * FROM {support_requests} WHERE $w ORDER BY id DESC LIMIT " . self::PER . ' OFFSET ' . ((self::pg() - 1) * self::PER), $params);
        Admin::layout('پشتیبانی اشتراک‌ها', function () use ($rows, $total, $status, $kind) {
            ?>
            <div class="alert info">روند بازیابی حساب برای کسی که هم رمز و هم کلید بازیابی را گم کرده: مالکیت را با شمارهٔ سفارش بازار/مایکت (که فقط خریدار در حساب فروشگاهش می‌بیند) و تاریخ و مبلغ خرید تطبیق دهید؛ سپس در صفحهٔ حساب «بازنشانی رمز» را بزنید یا اگر کاربر حساب تازه ساخته، خریدها را به حساب تازه منتقل کنید.</div>
            <form class="filters" method="get"><input type="hidden" name="p" value="support">
              <div><label>وضعیت</label><select name="status"><?php foreach (['open' => 'باز', 'resolved' => 'حل‌شده', 'all' => 'همه'] as $k => $l) echo '<option value="' . $k . '"' . ($status === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select></div>
              <div><label>موضوع</label><select name="kind"><option value="">همه</option><?php foreach (Support::KINDS as $k => $l) echo '<option value="' . $k . '"' . ($kind === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select></div>
              <div><button class="btn">اعمال</button></div>
            </form>
            <div class="table-wrap"><table><thead><tr><th>#</th><th>موضوع</th><th>نام کاربری</th><th>فروشگاه / سفارش</th><th>متن</th><th>وضعیت</th><th>زمان</th></tr></thead><tbody>
            <?php foreach ($rows as $t): ?>
              <tr><td><a href="index.php?p=ticket&id=<?= (int)$t['id'] ?>"><?= fa_digits($t['id']) ?></a></td><td><?= e(Support::KINDS[$t['kind']] ?? $t['kind']) ?></td><td class="ltr"><?= e($t['username'] ?? '—') ?></td>
                <td><?= Admin::storeName($t['store']) ?> <code><?= e($t['order_id'] ?? '') ?></code></td><td><?= e(mb_substr($t['message'], 0, 80)) ?>…</td><td><?= Admin::statusBadge($t['status']) ?></td><td><?= e(jdate($t['created_at'])) ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="7" class="muted">درخواستی نیست.</td></tr>'; ?>
            </tbody></table></div>
            <?= Admin::pager($total, self::pg(), self::PER, 'p=support&status=' . $status . '&kind=' . $kind) ?>
            <?php
        }, 'support');
    }

    public static function ticket(): void
    {
        $id = (int)($_GET['id'] ?? 0);
        $t = Db::one('SELECT * FROM {support_requests} WHERE id = ?', [$id]);
        if ($t === null) { Admin::flash('درخواست پیدا نشد.', 'bad'); Admin::redirect('p=support'); }
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $reason = Admin::requireReason();
            $st = (string)($_POST['status'] ?? 'in_review');
            if (!in_array($st, ['open', 'in_review', 'resolved'], true)) $st = 'in_review';
            Db::update('support_requests', ['status' => $st, 'updated_at' => Clock::sql()], 'id = ?', [$id]);
            Support::action($id, $t['user_id'] === null ? null : (int)$t['user_id'], (int)Admin::$me['id'], 'ticket_' . $st, $reason);
            Admin::audit('ticket_' . $st, 'support', $id, $reason);
            Admin::flash('ثبت شد.');
            Admin::redirect('p=ticket&id=' . $id);
        }
        $u = $t['user_id'] ? Users::byId((int)$t['user_id']) : ($t['username'] ? Users::byName($t['username']) : null);
        $byOrder = $t['order_id'] ? Db::all('SELECT p.*, u.username FROM {purchases} p JOIN {users} u ON u.id = p.user_id WHERE p.order_id = ?', [$t['order_id']]) : [];
        $actions = Db::all('SELECT a.*, ad.username admin_name FROM {support_actions} a LEFT JOIN {admins} ad ON ad.id = a.admin_id WHERE a.request_id = ? ORDER BY a.id DESC', [$id]);
        Admin::layout('درخواست پشتیبانی #' . fa_digits($id), function () use ($t, $u, $byOrder, $actions) {
            ?>
            <div class="grid g2">
            <div class="card"><h3><?= e(Support::KINDS[$t['kind']] ?? $t['kind']) ?> <?= Admin::statusBadge($t['status']) ?></h3>
              <p>نام کاربری اعلام‌شده: <b class="ltr"><?= e($t['username'] ?? '—') ?></b> <?= $u ? '— <a href="index.php?p=user&id=' . (int)$u['id'] . '&ticket=' . (int)$t['id'] . '">باز کردن حساب</a>' : '<span class="badge warn">حسابی با این نام نیست</span>' ?></p>
              <p>راه تماس: <span class="ltr"><?= e($t['contact'] ?? '—') ?></span></p>
              <p>فروشگاه و سفارش: <?= Admin::storeName($t['store']) ?> <code><?= e($t['order_id'] ?? '—') ?></code></p>
              <div class="card" style="background:var(--surface-2)"><?= nl2br(e($t['message'])) ?></div>
              <p class="muted">ثبت: <?= e(jdate($t['created_at'])) ?></p>
            </div>
            <div class="card"><h3>تطبیق شمارهٔ سفارش</h3>
              <?php if (!$t['order_id']): ?><p class="muted">کاربر شمارهٔ سفارش ننوشته است.</p>
              <?php elseif (!$byOrder): ?><div class="alert warn">خریدی با این شماره در سرور ثبت نشده؛ اگر کاربر رسید فروشگاه دارد، در برنامه «بازیابی خریدها» را بزند یا از صفحهٔ تراکنش بررسی کنید.</div>
              <?php else: foreach ($byOrder as $p): ?><p><a href="index.php?p=transaction&id=<?= (int)$p['id'] ?>">خرید #<?= fa_digits($p['id']) ?></a> — حساب <b class="ltr"><?= e($p['username']) ?></b> — <?= money((int)$p['amount_toman']) ?> — <?= Admin::statusBadge($p['status']) ?> — <?= e(jdate($p['purchase_time'] ?? $p['created_at'])) ?></p><?php endforeach; endif; ?>
              <h3>تغییر وضعیت</h3>
              <form method="post"><?= Admin::csrfField() ?>
                <select name="status"><option value="in_review">در بررسی</option><option value="resolved">حل‌شده</option><option value="open">باز</option></select>
                <label>شرح اقدام (اختیاری)</label><textarea name="reason"></textarea>
                <p><button class="btn gold">ثبت</button></p>
              </form>
            </div></div>
            <div class="card"><h3>سابقه</h3><div class="table-wrap"><table><thead><tr><th>اقدام</th><th>دلیل</th><th>مدیر</th><th>زمان</th></tr></thead><tbody>
            <?php foreach ($actions as $a) echo '<tr><td>' . e($a['action']) . '</td><td>' . e($a['reason']) . '</td><td>' . e($a['admin_name'] ?? '—') . '</td><td>' . e(jdate($a['created_at'])) . '</td></tr>';
            if (!$actions) echo '<tr><td colspan="4" class="muted">—</td></tr>'; ?>
            </tbody></table></div></div>
            <?php
        }, 'support');
    }

    // ================================================================ gifts
    public static function gifts(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            if (!in_array(Admin::$me['role'], ['owner', 'manager'], true)) throw new ApiError('role', 'هدیه دادن فقط برای مدیر اصلی و مدیر مجاز است.');
            $reason = Admin::requireReason();
            $users = Admin::pickedUsers('users');
            if (!$users) throw new ApiError('users', 'دست‌کم یک حساب را از فهرست انتخاب کنید.');
            $feature = (string)($_POST['feature'] ?? 'pro') ?: 'pro';
            if ($feature !== 'pro' && Db::value('SELECT k FROM {features} WHERE k = ?', [$feature]) === null) throw new ApiError('feature', 'قابلیت نامعتبر است.');
            $days = (int)($_POST['days'] ?? 0);
            $ok = [];
            foreach ($users as $u) {
                self::grant((int)$u['id'], $feature, $days > 0 ? $days : null, $reason);
                Support::action(null, (int)$u['id'], (int)Admin::$me['id'], 'gift_' . $feature, $reason, ['days' => $days ?: null]);
                $ok[] = $u['username'];
            }
            Admin::audit('gift', 'user_grants', $feature, $reason, null, ['users' => $ok, 'days' => $days ?: null]);
            Admin::flash(fa_digits(count($ok)) . ' حساب هدیه گرفت. اپ هر کاربر با باز شدن برنامه آن را می‌گیرد.');
            Admin::redirect('p=gifts');
        }
        $rows = Db::all('SELECT g.*, u.username, a.username admin_name FROM {user_grants} g JOIN {users} u ON u.id = g.user_id LEFT JOIN {admins} a ON a.id = g.admin_id ORDER BY g.id DESC LIMIT 200');
        $features = Features::all();
        Admin::layout('هدیه و دسترسی ویژه', function () use ($rows, $features) {
            ?>
            <div class="card"><h3>هدیهٔ جدید</h3>
              <form method="post"><?= Admin::csrfField() ?>
                <label>حساب‌ها (جست‌وجو کنید و تیک بزنید؛ چند انتخاب ممکن است)</label>
                <?= Admin::userPicker('users') ?>
                <div class="row">
                  <div><label>هدیه</label><select name="feature"><option value="pro" selected>Pro کامل — همهٔ قابلیت‌ها و بدون تبلیغ</option><optgroup label="فقط یک قابلیت (اختیاری)"><?php foreach ($features as $f) echo '<option value="' . e($f['k']) . '">' . e($f['title']) . '</option>'; ?></optgroup></select></div>
                  <div><label>مدت به روز (خالی = دائمی)</label><input type="number" name="days" min="1" max="3650" placeholder="مثلاً ۳۰"></div>
                </div>
                <label>یادداشت (اختیاری)</label><input type="text" name="reason" placeholder="مثلاً: برندهٔ مسابقهٔ کانال">
                <p><button class="btn gold">ثبت هدیه</button></p>
              </form>
              <p class="hint">برای هدیه به کسانی که هنوز حساب ندارند یا برای یک کمپین عمومی، از «کد تخفیف و کد هدیه» یک کد هدیه بسازید.</p>
            </div>
            <div class="table-wrap"><table><thead><tr><th>کاربر</th><th>هدیه</th><th>تا</th><th>دلیل</th><th>توسط</th><th>وضعیت</th></tr></thead><tbody>
            <?php foreach ($rows as $g):
                $active = $g['revoked_at'] === null && ($g['expires_at'] === null || Clock::ts($g['expires_at']) > Clock::now()); ?>
              <tr><td><a class="ltr" href="index.php?p=user&id=<?= (int)$g['user_id'] ?>"><?= e($g['username']) ?></a></td><td><?= e($g['feature_k'] === 'pro' ? 'Pro کامل' : (Db::value('SELECT title FROM {features} WHERE k = ?', [$g['feature_k']]) ?? $g['feature_k'])) ?></td>
                <td><?= $g['expires_at'] === null ? 'دائمی' : e(jdate($g['expires_at'], false)) ?></td><td><?= e($g['reason']) ?></td><td><?= e($g['admin_name'] ?? '—') ?></td>
                <td><?= $g['revoked_at'] !== null ? '<span class="badge bad">لغوشده</span>' : ($active ? '<span class="badge ok">فعال</span>' : '<span class="badge">پایان‌یافته</span>') ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="6" class="muted">هنوز هدیه‌ای ثبت نشده.</td></tr>'; ?>
            </tbody></table></div>
            <p class="hint">برای لغو یک هدیه، صفحهٔ حساب همان کاربر را باز کنید.</p>
            <?php
        }, 'gifts');
    }

    // ================================================================ discount and gift codes
    public static function promos(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            if (!in_array(Admin::$me['role'], ['owner', 'manager'], true)) throw new ApiError('role', 'ساخت کد فقط برای مدیر اصلی و مدیر مجاز است.');
            $reason = Admin::requireReason();
            if (($_POST['action'] ?? '') === 'toggle') {
                $p = Db::one('SELECT * FROM {promo_codes} WHERE id = ?', [(int)($_POST['id'] ?? 0)]);
                if ($p === null) throw new ApiError('promo', 'کد پیدا نشد.');
                Db::update('promo_codes', ['active' => $p['active'] ? 0 : 1], 'id = ?', [$p['id']]);
                Admin::audit('promo_toggle', 'promo', $p['id'], $reason);
                Admin::redirect('p=promos');
            }
            $kind = (string)($_POST['kind'] ?? 'gift_pro');
            if (!isset(Promo::KINDS[$kind])) throw new ApiError('kind', 'نوع کد نامعتبر است.');
            $count = max(1, min(500, (int)($_POST['count'] ?? 1)));
            $custom = Promo::normalize((string)($_POST['code'] ?? ''));
            if ($custom !== '' && $count > 1) throw new ApiError('code', 'برای ساخت چند کد، کادر «کد دلخواه» را خالی بگذارید تا کدها خودکار ساخته شوند.');
            if ($custom !== '' && (strlen($custom) < 4 || strlen($custom) > 32)) throw new ApiError('code', 'کد دلخواه باید ۴ تا ۳۲ حرف لاتین یا رقم باشد.');
            $days = (int)($_POST['days'] ?? 0);
            $percent = (int)($_POST['percent'] ?? 0);
            $feature = (string)($_POST['feature'] ?? '');
            $plan = (string)($_POST['plan'] ?? '');
            if ($kind === 'discount' && ($percent < 1 || $percent > 90)) throw new ApiError('percent', 'درصد تخفیف باید بین ۱ تا ۹۰ باشد.');
            if ($kind === 'gift_feature' && Db::value('SELECT k FROM {features} WHERE k = ?', [$feature]) === null) throw new ApiError('feature', 'قابلیت را انتخاب کنید.');
            if ($plan !== '' && Plans::byCode($plan) === null) throw new ApiError('plan', 'پلن نامعتبر است.');
            $users = array_map(fn($u) => (string)$u['id'], Admin::pickedUsers('users'));
            $made = [];
            for ($i = 0; $i < $count; $i++) {
                $code = $custom !== '' ? $custom : Promo::generate((int)($_POST['length'] ?? 8) ?: 8);
                if (Db::value('SELECT id FROM {promo_codes} WHERE code = ?', [$code]) !== null) { if ($custom !== '') throw new ApiError('taken', 'این کد قبلاً ساخته شده است.'); $i--; continue; }
                Db::insert('promo_codes', [
                    'code' => $code, 'kind' => $kind, 'days' => $kind === 'discount' ? null : ($days > 0 ? $days : null),
                    'feature_k' => $kind === 'gift_feature' ? $feature : null, 'plan_code' => $kind === 'discount' && $plan !== '' ? $plan : null,
                    'percent' => $kind === 'discount' ? $percent : null, 'max_uses' => ($m = (int)($_POST['max_uses'] ?? 0)) > 0 ? $m : null,
                    'users' => $users ? implode(',', $users) : null, 'starts_at' => Admin::dtParse($_POST['starts_at'] ?? ''), 'ends_at' => Admin::dtParse($_POST['ends_at'] ?? ''),
                    'active' => 1, 'note' => mb_substr($reason, 0, 300), 'admin_id' => (int)Admin::$me['id'], 'created_at' => Clock::sql(),
                ]);
                $made[] = $code;
            }
            Admin::audit('promo_create', 'promo', implode(',', array_slice($made, 0, 5)), $reason, null, ['codes' => count($made), 'kind' => $kind]);
            $_SESSION['new_codes'] = $made;
            Admin::flash(fa_digits(count($made)) . ' کد ساخته شد.');
            if ($kind === 'discount' && !Promo::discountUsable('bazaar')) Admin::flash('برای کار کردن کد تخفیف، «کلید تخفیف پویا»ی بازار را در «بازار و مایکت» ثبت کنید. در مایکت کد تخفیف ممکن نیست؛ از کد هدیه استفاده کنید.', 'warn');
            Admin::redirect('p=promos');
        }
        if (isset($_GET['csv'])) {
            $rows = Db::all('SELECT c.code, c.kind, c.days, c.percent, c.plan_code, c.uses, c.max_uses, c.active, c.created_at FROM {promo_codes} c ORDER BY c.id DESC');
            Admin::csv('codes', ['code', 'kind', 'days', 'percent', 'plan', 'uses', 'max_uses', 'active', 'created_utc'], array_map('array_values', $rows));
        }
        $new = $_SESSION['new_codes'] ?? null; unset($_SESSION['new_codes']);
        $rows = Db::all('SELECT c.*, (SELECT GROUP_CONCAT(u.username SEPARATOR ", ") FROM {promo_redemptions} r JOIN {users} u ON u.id = r.user_id WHERE r.code_id = c.id) used_by FROM {promo_codes} c ORDER BY c.id DESC LIMIT 300');
        $features = Features::all();
        $plans = Plans::all();
        Admin::layout('کد تخفیف و کد هدیه', function () use ($rows, $features, $plans, $new) {
            if ($new) echo '<div class="card"><h3>کدهای تازه</h3><div class="secret" style="font-size:.95rem;text-align:right">' . e(implode("  ", $new)) . '</div></div>';
            ?>
            <div class="alert info">
              <b>کد هدیه</b> (Pro کامل یا یک قابلیت، با مدت یا دائمی) در هر دو نسخه کار می‌کند: کاربر در اپ ← دیوان Pro ← «کد تخفیف یا هدیه دارید؟» آن را وارد می‌کند و بلافاصله فعال می‌شود.
              <br><b>کد تخفیف</b> قیمت یک خرید را کم می‌کند و فقط در نسخهٔ کافه‌بازار ممکن است (با «کلید تخفیف پویا»؛ <?= Promo::discountUsable('bazaar') ? '<span class="badge ok">ثبت شده</span>' : '<span class="badge warn">ثبت نشده</span>' ?>). مایکت تغییر قیمت یک خرید را پشتیبانی نمی‌کند.
              <br>هر حساب از هر کد فقط یک بار استفاده می‌کند.
            </div>
            <div class="card"><h3>ساخت کد</h3>
              <form method="post"><?= Admin::csrfField() ?>
                <div class="row">
                  <div><label>نوع</label><select name="kind" id="pr-kind"><?php foreach (Promo::KINDS as $k => $l) echo '<option value="' . $k . '">' . $l . '</option>'; ?></select></div>
                  <div><label>کد دلخواه (خالی = خودکار)</label><input type="text" name="code" class="ltr" placeholder="NOWRUZ1405"></div>
                  <div><label>تعداد کد</label><input type="number" name="count" value="1" min="1" max="500"></div>
                </div>
                <div class="row">
                  <div data-kind="gift_pro gift_feature"><label>مدت هدیه به روز (خالی = دائمی)</label><input type="number" name="days" min="1" max="3650" placeholder="مثلاً ۳۰"></div>
                  <div data-kind="gift_feature"><label>قابلیت</label><select name="feature"><?php foreach ($features as $f) echo '<option value="' . e($f['k']) . '">' . e($f['title']) . '</option>'; ?></select></div>
                  <div data-kind="discount"><label>درصد تخفیف</label><input type="number" name="percent" min="1" max="90" placeholder="مثلاً ۲۰"></div>
                  <div data-kind="discount"><label>پلن</label><select name="plan"><option value="">همهٔ پلن‌ها</option><?php foreach ($plans as $p) echo '<option value="' . e($p['code']) . '">' . e($p['title']) . '</option>'; ?></select></div>
                </div>
                <div class="row">
                  <div><label>سقف کل استفاده (خالی = نامحدود)</label><input type="number" name="max_uses" min="1"></div>
                  <div><label>شروع (تهران)</label><input type="datetime-local" name="starts_at"></div>
                  <div><label>پایان</label><input type="datetime-local" name="ends_at"></div>
                </div>
                <details><summary>فقط برای حساب‌های خاص (اختیاری؛ اگر کسی را انتخاب نکنید، کد برای همه است)</summary>
                  <?= Admin::userPicker('users') ?>
                </details>
                <label>توضیح (اختیاری)</label><input type="text" name="reason" placeholder="مثلاً: کمپین نوروز کانال تلگرام">
                <p><button class="btn gold">ساخت کد</button></p>
              </form>
            </div>
            <p><a class="btn ghost sm" href="index.php?p=promos&csv=1">خروجی CSV کدها</a></p>
            <div class="table-wrap"><table><thead><tr><th>کد</th><th>نوع</th><th>جزئیات</th><th>استفاده</th><th>بازه</th><th>استفاده‌کنندگان</th><th></th></tr></thead><tbody>
            <?php foreach ($rows as $c): $d = Promo::describe($c, 'bazaar'); ?>
              <tr><td><code><?= e($c['code']) ?></code></td><td><?= e(Promo::KINDS[$c['kind']] ?? $c['kind']) ?></td><td><?= e($d['title']) ?><?= $c['users'] ? '<br><small class="muted">فقط ' . fa_digits(count(explode(',', $c['users']))) . ' حساب انتخاب‌شده</small>' : '' ?></td>
                <td><?= fa_digits($c['uses']) ?><?= $c['max_uses'] ? ' از ' . fa_digits($c['max_uses']) : '' ?></td>
                <td><small><?= e(jdate($c['starts_at'])) ?> تا <?= e(jdate($c['ends_at'])) ?></small></td>
                <td><small class="ltr"><?= e(mb_substr((string)$c['used_by'], 0, 120)) ?></small></td>
                <td><form method="post" style="display:inline"><?= Admin::csrfField() ?><input type="hidden" name="action" value="toggle"><input type="hidden" name="id" value="<?= (int)$c['id'] ?>"><input type="hidden" name="reason" value="<?= $c['active'] ? 'توقف کد از فهرست' : 'فعال‌سازی کد از فهرست' ?>"><button class="btn sm <?= $c['active'] ? 'red' : 'teal' ?>"><?= $c['active'] ? 'توقف' : 'فعال' ?></button></form></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="7" class="muted">هنوز کدی ساخته نشده.</td></tr>'; ?>
            </tbody></table></div>
            <?php
        }, 'promos');
    }

    // ================================================================ revenue and performance
    public static function reports(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            if (!in_array(Admin::$me['role'], ['owner', 'manager'], true)) throw new ApiError('role', 'ثبت درآمد فقط برای مدیر اصلی و مدیر مجاز است.');
            if (($_POST['action'] ?? '') === 'delete') {
                $reason = Admin::requireReason();
                $r = Db::one('SELECT * FROM {revenue} WHERE id = ?', [(int)($_POST['id'] ?? 0)]);
                if ($r !== null) { Db::run('DELETE FROM {revenue} WHERE id = ?', [$r['id']]); Admin::audit('revenue_delete', 'revenue', $r['id'], $reason, $r); }
            } else {
                $day = (string)($_POST['day'] ?? '');
                $label = (string)($_POST['label'] ?? 'manual');
                $source = (string)($_POST['source'] ?? 'tapsell');
                if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $day)) throw new ApiError('day', 'تاریخ را درست وارد کنید.');
                if (!in_array($label, ['real', 'estimated', 'manual'], true) || !in_array($source, ['tapsell', 'direct', 'sponsor', 'other'], true)) throw new ApiError('label', 'برچسب نامعتبر است.');
                $id = Db::insert('revenue', ['day' => $day, 'source' => $source, 'amount_toman' => max(0, (int)($_POST['amount'] ?? 0)), 'label' => $label, 'note' => mb_substr((string)($_POST['note'] ?? ''), 0, 300) ?: null, 'admin_id' => (int)Admin::$me['id'], 'created_at' => Clock::sql()]);
                Admin::audit('revenue_add', 'revenue', $id, (string)($_POST['note'] ?? ''));
            }
            Admin::flash('ثبت شد.');
            Admin::redirect('p=reports');
        }
        $days = max(7, min(365, (int)($_GET['days'] ?? 30)));
        $since = Clock::sql(Clock::now() - $days * 86400);
        $sales = Db::all("SELECT p.store, pl.title, COUNT(*) n, COALESCE(SUM(p.amount_toman),0) s FROM {purchases} p LEFT JOIN {plans} pl ON pl.id = p.plan_id WHERE p.status IN ('verified','signature_verified') AND p.created_at >= ? GROUP BY p.store, pl.title ORDER BY s DESC", [$since]);
        $refunds = (int)Db::value("SELECT COALESCE(SUM(amount_toman),0) FROM {purchases} WHERE status IN ('refunded','revoked') AND updated_at >= ?", [$since]);
        $adRows = Db::all('SELECT source, placement, SUM(requests) rq, SUM(fills) fl, SUM(impressions) im, SUM(clicks) cl, SUM(rewards) rw, SUM(errors) er FROM {ad_stats} WHERE day >= ? GROUP BY source, placement ORDER BY im DESC', [gmdate('Y-m-d', Clock::now() - $days * 86400)]);
        $adsById = Db::all('SELECT a.id, a.name, SUM(s.impressions) im, SUM(s.clicks) cl FROM {ad_stats} s JOIN {ads} a ON a.id = s.ad_id WHERE s.day >= ? GROUP BY a.id, a.name ORDER BY im DESC LIMIT 30', [gmdate('Y-m-d', Clock::now() - $days * 86400)]);
        $revenue = Db::all('SELECT r.*, a.username admin_name FROM {revenue} r LEFT JOIN {admins} a ON a.id = r.admin_id WHERE r.day >= ? ORDER BY r.day DESC, r.id DESC', [gmdate('Y-m-d', Clock::now() - $days * 86400)]);
        $conv = ['users' => (int)Db::value('SELECT COUNT(*) FROM {users} WHERE created_at >= ?', [$since]), 'intents' => (int)Db::value('SELECT COUNT(*) FROM {purchase_intents} WHERE created_at >= ?', [$since]), 'buyers' => (int)Db::value("SELECT COUNT(DISTINCT user_id) FROM {purchases} WHERE status IN ('verified','signature_verified') AND created_at >= ?", [$since])];
        if (isset($_GET['csv'])) {
            $out = [];
            foreach ($sales as $r) $out[] = ['sales', $r['store'], $r['title'], $r['n'], $r['s'], 'real'];
            foreach ($revenue as $r) $out[] = ['ad_revenue', $r['source'], $r['day'], '', $r['amount_toman'], $r['label']];
            foreach ($adRows as $r) $out[] = ['ad_stats', $r['source'], $r['placement'], $r['im'], $r['cl'], 'counted_by_app'];
            Admin::csv('report-' . $days . 'd', ['type', 'source', 'item', 'count', 'amount_or_clicks', 'label'], $out);
        }
        Admin::layout('درآمد و عملکرد', function () use ($days, $sales, $refunds, $adRows, $adsById, $revenue, $conv) {
            $labels = ['real' => ['ok', 'واقعی'], 'estimated' => ['warn', 'تخمینی'], 'manual' => ['info', 'ثبت دستی']];
            $totalSales = array_sum(array_column($sales, 's'));
            ?>
            <form class="filters" method="get"><input type="hidden" name="p" value="reports">
              <div><label>بازه</label><select name="days"><?php foreach ([7, 30, 90, 365] as $d) echo '<option value="' . $d . '"' . ($days === $d ? ' selected' : '') . '>' . fa_digits($d) . ' روز اخیر</option>'; ?></select></div>
              <div><button class="btn">اعمال</button> <a class="btn ghost" href="index.php?p=reports&days=<?= $days ?>&csv=1">خروجی CSV</a></div>
            </form>
            <div class="grid g4">
              <div class="kpi"><div class="l">فروش اشتراک</div><div class="v"><?= money($totalSales) ?></div><div class="t"><span class="badge ok">واقعی</span> مجموع تراکنش‌های تأییدشده</div></div>
              <div class="kpi"><div class="l">بازپرداخت و ابطال</div><div class="v"><?= money($refunds) ?></div><div class="t"><span class="badge ok">واقعی</span></div></div>
              <div class="kpi"><div class="l">درآمد تبلیغات ثبت‌شده</div><div class="v"><?= money(array_sum(array_column($revenue, 'amount_toman'))) ?></div><div class="t muted">ترکیبی از برچسب‌های جدول پایین</div></div>
              <div class="kpi"><div class="l">نرخ تبدیل</div><div class="v"><?= $conv['users'] ? fa_digits(round($conv['buyers'] * 100 / $conv['users'], 1)) . '٪' : '—' ?></div><div class="t muted"><?= fa_digits($conv['users']) ?> حساب تازه، <?= fa_digits($conv['intents']) ?> شروع خرید، <?= fa_digits($conv['buyers']) ?> خریدار</div></div>
            </div>
            <div class="card" style="margin-top:14px"><h3>فروش به تفکیک فروشگاه و پلن</h3><div class="table-wrap"><table><thead><tr><th>فروشگاه</th><th>پلن</th><th>تعداد</th><th>مبلغ</th></tr></thead><tbody>
            <?php foreach ($sales as $r) echo '<tr><td>' . Admin::storeName($r['store']) . '</td><td>' . e($r['title'] ?? '—') . '</td><td>' . fa_digits($r['n']) . '</td><td>' . money($r['s']) . '</td></tr>';
            if (!$sales) echo '<tr><td colspan="4" class="muted">فروشی در این بازه نیست.</td></tr>'; ?>
            </tbody></table></div><p class="hint">مبالغ، قیمت پنل در لحظهٔ خرید است؛ سهم فروشگاه و مالیات از آن کم نشده.</p></div>

            <div class="card"><h3>عملکرد جایگاه‌های تبلیغ</h3>
            <p class="hint">این اعداد را خود اپ می‌شمارد و می‌فرستد (<span class="badge info">شمارش اپ</span>)؛ ممکن است با آمار پنل تپسل کمی فرق داشته باشد. درآمد واقعی شبکه را از پنل تپسل بخوانید و در جدول پایین ثبت کنید.</p>
            <div class="table-wrap"><table><thead><tr><th>منبع</th><th>جایگاه</th><th>درخواست</th><th>پرشده</th><th>نمایش</th><th>کلیک</th><th>CTR</th><th>جایزه</th><th>خطا</th></tr></thead><tbody>
            <?php foreach ($adRows as $r) echo '<tr><td>' . ($r['source'] === 'network' ? 'تپسل' : 'مستقیم') . '</td><td>' . e($r['placement']) . '</td><td>' . fa_digits($r['rq']) . '</td><td>' . fa_digits($r['fl']) . '</td><td>' . fa_digits($r['im']) . '</td><td>' . fa_digits($r['cl']) . '</td><td>' . ($r['im'] ? fa_digits(round($r['cl'] * 100 / $r['im'], 2)) . '٪' : '—') . '</td><td>' . fa_digits($r['rw']) . '</td><td>' . fa_digits($r['er']) . '</td></tr>';
            if (!$adRows) echo '<tr><td colspan="9" class="muted">هنوز آماری نرسیده.</td></tr>'; ?>
            </tbody></table></div>
            <?php if ($adsById): ?><h3 style="margin-top:14px">تبلیغ‌های مستقیم</h3><div class="table-wrap"><table><thead><tr><th>تبلیغ</th><th>نمایش</th><th>کلیک</th><th>CTR</th></tr></thead><tbody>
            <?php foreach ($adsById as $r) echo '<tr><td><a href="index.php?p=ad&id=' . (int)$r['id'] . '">' . e($r['name']) . '</a></td><td>' . fa_digits($r['im']) . '</td><td>' . fa_digits($r['cl']) . '</td><td>' . ($r['im'] ? fa_digits(round($r['cl'] * 100 / $r['im'], 2)) . '٪' : '—') . '</td></tr>'; ?>
            </tbody></table></div><?php endif; ?>
            </div>

            <div class="card"><h3>درآمد تبلیغات و اسپانسر</h3>
              <form method="post" class="row"><?= Admin::csrfField() ?>
                <div><label>تاریخ (میلادی)</label><input type="date" name="day" value="<?= gmdate('Y-m-d') ?>" required></div>
                <div><label>منبع</label><select name="source"><option value="tapsell">تپسل</option><option value="direct">تبلیغ مستقیم</option><option value="sponsor">اسپانسر</option><option value="other">سایر</option></select></div>
                <div><label>مبلغ (تومان)</label><input type="number" name="amount" min="0" required></div>
                <div><label>برچسب</label><select name="label"><option value="real">واقعی (از صورتحساب)</option><option value="estimated">تخمینی</option><option value="manual">ثبت دستی</option></select></div>
                <div><label>توضیح</label><input type="text" name="note"></div>
                <div style="flex:0"><label>&nbsp;</label><button class="btn gold">افزودن</button></div>
              </form>
              <div class="table-wrap" style="margin-top:10px"><table><thead><tr><th>تاریخ</th><th>منبع</th><th>مبلغ</th><th>برچسب</th><th>توضیح</th><th>ثبت‌کننده</th><th></th></tr></thead><tbody>
              <?php foreach ($revenue as $r): [$lk, $ll] = $labels[$r['label']] ?? ['', $r['label']]; ?>
                <tr><td><?= e(jdate($r['day'] . ' 00:00:00', false)) ?></td><td><?= e($r['source']) ?></td><td><?= money($r['amount_toman']) ?></td><td><span class="badge <?= $lk ?>"><?= $ll ?></span></td><td><?= e($r['note'] ?? '') ?></td><td><?= e($r['admin_name'] ?? '—') ?></td>
                  <td><form method="post" class="row" style="gap:4px"><?= Admin::csrfField() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$r['id'] ?>"><input type="text" name="reason" placeholder="یادداشت (اختیاری)" style="min-width:100px"><button class="btn sm red">حذف</button></form></td></tr>
              <?php endforeach; if (!$revenue) echo '<tr><td colspan="7" class="muted">—</td></tr>'; ?>
              </tbody></table></div>
            </div>
            <?php
        }, 'reports');
    }
}
