<?php
/** Admin pages: plans, campaigns, features, ads, placements, media, ad network, stores, backups, logs, admins. */

declare(strict_types=1);

if (!defined('DIVAN')) { http_response_code(403); exit; }

final class ConfigPages
{
    private static function managerOnly(): void
    {
        if (!in_array(Admin::$me['role'], ['owner', 'manager'], true)) throw new ApiError('role', 'این تغییر فقط برای مدیر اصلی و مدیر مجاز است.');
    }

    private static function ownerOnly(): void
    {
        if (!Admin::owner()) throw new ApiError('role', 'این بخش فقط برای مدیر اصلی است.');
    }

    private static function post(string $k, string $d = ''): string { return trim((string)($_POST[$k] ?? $d)); }
    private static function intOrNull(string $k): ?int { $v = self::post($k); return $v === '' ? null : (int)$v; }

    public static function eventName(string $k): string
    {
        return [
            'login_failed' => 'ورود ناموفق', 'login_throttled' => 'محدودیت ورود', 'register' => 'ثبت‌نام', 'recover_failed' => 'بازیابی ناموفق',
            'recover_throttled' => 'محدودیت بازیابی', 'recovered' => 'بازیابی موفق', 'recovery_key_rotated' => 'کلید بازیابی تازه', 'password_changed' => 'تغییر رمز',
            'sessions_revoked' => 'خروج از دستگاه‌ها', 'receipt_bad_signature' => 'رسید با امضای نادرست', 'receipt_reuse' => 'استفادهٔ دوباره از رسید',
            'receipt_mismatch' => 'رسید ناهمخوان', 'payload_mismatch' => 'رسید متعلق به حساب دیگر', 'receipt_without_intent' => 'رسید بدون شروع خرید',
            'admin_login_failed' => 'ورود ناموفق مدیر', 'device_transfer' => 'انتقال اشتراک به گوشی دیگر', 'device_transfer_blocked' => 'انتقال گوشی رد شد (سقف)', 'admin_totp_failed' => 'کد دومرحله‌ای نادرست مدیر', 'support_password_reset' => 'بازنشانی رمز توسط پشتیبانی',
        ][$k] ?? $k;
    }

    // ================================================================ plans
    public static function plans(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $plan = Plans::byId((int)($_POST['id'] ?? 0));
            if ($plan === null) throw new ApiError('plan', 'پلن پیدا نشد.');
            if (($_POST['action'] ?? '') === 'myket_push') {
                $o = Plans::offer($plan, 'myket');
                if ($plan['type_myket'] !== 'consumable') throw new ApiError('type', 'مایکت تغییر قیمت از راه API را فقط برای محصول مصرفی مستند کرده؛ قیمت این محصول را در پنل مایکت تغییر دهید و اینجا ثبت کنید.');
                (new MyketVerifier())->setPrice((string)$plan['sku_myket'], (int)$o['final_price']);
                Db::update('plans', ['store_price_myket' => (int)$o['final_price'], 'updated_at' => Clock::sql()], 'id = ?', [$plan['id']]);
                Admin::audit('plan_myket_price_pushed', 'plan', $plan['id'], $reason, ['store_price_myket' => $plan['store_price_myket']], ['store_price_myket' => (int)$o['final_price']]);
                Admin::flash('قیمت در مایکت به‌روز شد.');
                Admin::redirect('p=plans');
            }
            $price = (int)self::post('base_price_toman');
            if ($price < 1000) throw new ApiError('price', 'قیمت باید دست‌کم ۱٬۰۰۰ تومان باشد.');
            $color = self::post('badge_color');
            if ($color !== '' && !preg_match('/^#[0-9a-fA-F]{6}$/', $color)) throw new ApiError('color', 'رنگ برچسب باید به شکل #RRGGBB باشد.');
            $sku = fn($k) => preg_match('/^[a-z0-9_.]{0,64}$/', self::post($k)) ? (self::post($k) ?: null) : null;
            $type = fn($k) => in_array(self::post($k), ['consumable', 'permanent', 'subscription'], true) ? self::post($k) : 'consumable';
            $row = [
                'title' => mb_substr(self::post('title'), 0, 80) ?: $plan['title'],
                'description' => mb_substr(self::post('description'), 0, 400),
                'base_price_toman' => $price,
                'sort' => (int)self::post('sort', '0'),
                'active' => isset($_POST['active']) ? 1 : 0,
                'badge_text' => mb_substr(self::post('badge_text'), 0, 40) ?: null,
                'badge_color' => $color ?: null,
                'renewal_note' => mb_substr(self::post('renewal_note'), 0, 300) ?: null,
                'purchase_limit' => self::intOrNull('purchase_limit'),
                'sku_bazaar' => $sku('sku_bazaar'), 'sku_myket' => $sku('sku_myket'),
                'type_bazaar' => $type('type_bazaar'), 'type_myket' => $plan['duration_days'] === null ? 'permanent' : 'consumable',
                'store_price_bazaar' => self::intOrNull('store_price_bazaar'), 'store_price_myket' => self::intOrNull('store_price_myket'),
                'updated_at' => Clock::sql(),
            ];
            if ($plan['duration_days'] === null && $row['type_bazaar'] === 'subscription') $row['type_bazaar'] = 'permanent';
            $before = array_intersect_key($plan, $row);
            Db::update('plans', $row, 'id = ?', [$plan['id']]);
            Admin::audit('plan_update', 'plan', $plan['id'], $reason, $before, $row);
            Admin::flash('پلن ذخیره شد.' . ((int)$plan['base_price_toman'] !== $price ? ' قیمت عوض شد؛ تا قیمت فروشگاه‌ها با آن یکی نشود، این پلن در اپ «در انتظار همگام‌سازی قیمت» می‌ماند.' : ''));
            Admin::redirect('p=plans');
        }
        $plans = Plans::all();
        Admin::layout('پلن‌ها و قیمت‌ها', function () use ($plans) {
            ?>
            <div class="alert info">قیمت نمایش‌داده در اپ همیشه همان قیمتی است که فروشگاه می‌گیرد. اگر قیمت پنل با قیمت ثبت‌شده در بازار یا مایکت یکی نباشد، دکمهٔ خرید آن پلن در همان فروشگاه غیرفعال می‌شود تا کاربر قیمت متفاوتی نبیند. بعد از تغییر قیمت در پنل فروشگاه، همان عدد را در «قیمت تأییدشده در فروشگاه» وارد کنید (یا برای مایکت دکمهٔ ارسال قیمت را بزنید).</div>
            <div class="alert warn">بازار به‌طور پیش‌فرض قیمت محصول درون‌برنامه‌ای را تا ۱۰۰٬۰۰۰ تومان می‌پذیرد؛ برای پلن مادام‌العمر ۲۲۰٬۰۰۰ تومانی باید از پشتیبانی بازار افزایش سقف را درخواست کنید.</div>
            <?php foreach ($plans as $p):
                $ob = Plans::offer($p, 'bazaar'); $om = Plans::offer($p, 'myket'); ?>
            <div class="card">
              <h3><?= e($p['title']) ?> <small class="muted">(<?= e($p['code']) ?> — <?= $p['duration_days'] === null ? 'مادام‌العمر' : fa_digits($p['duration_days']) . ' روز' ?>)</small>
                <?= $p['badge_text'] ? '<span class="badge" style="background:' . e($p['badge_color'] ?: '#eee') . ';color:#fff;border:0">' . e($p['badge_text']) . '</span>' : '' ?></h3>
              <p>بازار: <?= Admin::statusBadge($ob['state']) ?> قیمت نهایی <?= money($ob['final_price']) ?><?= $ob['dynamic_price'] ? ' <span class="badge info">تخفیف پویای بازار</span>' : '' ?> — مایکت: <?= Admin::statusBadge($om['state']) ?> قیمت نهایی <?= money($om['final_price']) ?></p>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= (int)$p['id'] ?>">
                <div class="row">
                  <div><label>عنوان</label><input type="text" name="title" value="<?= e($p['title']) ?>" maxlength="80"></div>
                  <div><label>قیمت پایه (تومان)</label><input type="number" name="base_price_toman" value="<?= (int)$p['base_price_toman'] ?>" min="1000"></div>
                  <div><label>ترتیب نمایش</label><input type="number" name="sort" value="<?= (int)$p['sort'] ?>"></div>
                  <div><label>سقف خرید برای هر حساب</label><input type="number" name="purchase_limit" value="<?= e($p['purchase_limit']) ?>" min="1" placeholder="نامحدود"></div>
                </div>
                <label>توضیح</label><input type="text" name="description" value="<?= e($p['description']) ?>" maxlength="400">
                <div class="row">
                  <div><label>متن برچسب</label><input type="text" name="badge_text" value="<?= e($p['badge_text']) ?>" maxlength="40" placeholder="مثلاً: محبوب‌ترین"></div>
                  <div><label>رنگ برچسب</label><input type="text" name="badge_color" value="<?= e($p['badge_color']) ?>" class="ltr" placeholder="#C9A24B"></div>
                  <div><label>متن تمدید</label><input type="text" name="renewal_note" value="<?= e($p['renewal_note']) ?>" maxlength="300"></div>
                </div>
                <div class="row">
                  <div><label>SKU بازار</label><input type="text" name="sku_bazaar" value="<?= e($p['sku_bazaar']) ?>" class="ltr"></div>
                  <div><label>نوع محصول بازار</label><select name="type_bazaar"><?php foreach (($p['duration_days'] === null ? ['permanent' => 'دائمی (مصرف‌نشدنی)'] : ['consumable' => 'مصرفی (دوره در سرور)', 'subscription' => 'اشتراک بازار']) as $k => $l) echo '<option value="' . $k . '"' . ($p['type_bazaar'] === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select></div>
                  <div><label>قیمت تأییدشده در بازار (تومان)</label><input type="number" name="store_price_bazaar" value="<?= e($p['store_price_bazaar']) ?>" placeholder="هنوز ثبت نشده"></div>
                </div>
                <div class="row">
                  <div><label>SKU مایکت</label><input type="text" name="sku_myket" value="<?= e($p['sku_myket']) ?>" class="ltr"></div>
                  <div><label>نوع محصول مایکت</label><input type="text" value="<?= $p['duration_days'] === null ? 'دائمی (مصرف‌نشدنی)' : 'مصرفی (مایکت اشتراک ندارد)' ?>" disabled></div>
                  <div><label>قیمت تأییدشده در مایکت (تومان)</label><input type="number" name="store_price_myket" value="<?= e($p['store_price_myket']) ?>" placeholder="هنوز ثبت نشده"></div>
                </div>
                <label class="check"><input type="checkbox" name="active" <?= $p['active'] ? 'checked' : '' ?>> فعال (در اپ نمایش داده شود)</label>
                <div class="row" style="margin-top:8px">
                  <div style="flex:3"><input type="text" name="reason" placeholder="دلیل تغییر (اجباری، در گزارش ثبت می‌شود)" required minlength="5"></div>
                  <div style="flex:0"><button class="btn gold">ذخیره</button></div>
                  <?php if ($p['duration_days'] !== null && $p['sku_myket']): ?><div style="flex:0"><button class="btn ghost" name="action" value="myket_push" data-confirm="قیمت نهایی این پلن در مایکت تنظیم شود؟">ارسال قیمت به مایکت</button></div><?php endif; ?>
                </div>
              </form>
            </div>
            <?php endforeach;
        }, 'plans');
    }

    // ================================================================ campaigns
    public static function campaigns(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $id = (int)($_POST['id'] ?? 0);
            if (($_POST['action'] ?? '') === 'toggle') {
                $c = Db::one('SELECT * FROM {campaigns} WHERE id = ?', [$id]);
                if ($c === null) throw new ApiError('campaign', 'کمپین پیدا نشد.');
                Db::update('campaigns', ['active' => $c['active'] ? 0 : 1, 'updated_at' => Clock::sql()], 'id = ?', [$id]);
                Admin::audit('campaign_toggle', 'campaign', $id, $reason, ['active' => $c['active']], ['active' => $c['active'] ? 0 : 1]);
                Admin::redirect('p=campaigns');
            }
            $plan = Plans::byId((int)($_POST['plan_id'] ?? 0));
            if ($plan === null) throw new ApiError('plan', 'پلن را انتخاب کنید.');
            $price = (int)self::post('price_toman');
            // A discount must be real: lower than the regular price that buyers otherwise pay.
            if ($price < 1000 || $price >= (int)$plan['base_price_toman']) throw new ApiError('price', 'قیمت کمپین باید کمتر از قیمت عادی (' . money($plan['base_price_toman']) . ') و دست‌کم ۱٬۰۰۰ تومان باشد؛ تخفیف ساختگی مجاز نیست.');
            $starts = Admin::dtParse(self::post('starts_at')); $ends = Admin::dtParse(self::post('ends_at'));
            if ($ends !== null && $starts !== null && Clock::ts($ends) <= Clock::ts($starts)) throw new ApiError('dates', 'پایان کمپین باید بعد از شروع آن باشد.');
            $color = self::post('label_color');
            $row = [
                'name' => mb_substr(self::post('name'), 0, 80) ?: 'کمپین', 'plan_id' => $plan['id'],
                'store' => in_array(self::post('store'), ['all', 'bazaar', 'myket'], true) ? self::post('store') : 'all',
                'price_toman' => $price, 'label' => mb_substr(self::post('label'), 0, 40) ?: null,
                'label_color' => preg_match('/^#[0-9a-fA-F]{6}$/', $color) ? $color : null,
                'starts_at' => $starts, 'ends_at' => $ends, 'active' => isset($_POST['active']) ? 1 : 0, 'updated_at' => Clock::sql(),
            ];
            if ($id > 0) { Db::update('campaigns', $row, 'id = ?', [$id]); Admin::audit('campaign_update', 'campaign', $id, $reason, null, $row); }
            else { $id = Db::insert('campaigns', $row + ['created_at' => Clock::sql()]); Admin::audit('campaign_create', 'campaign', $id, $reason, null, $row); }
            Admin::flash('کمپین ذخیره شد. وضعیت خرید هر فروشگاه را در فهرست ببینید.');
            Admin::redirect('p=campaigns');
        }
        $edit = isset($_GET['id']) ? Db::one('SELECT * FROM {campaigns} WHERE id = ?', [(int)$_GET['id']]) : null;
        $rows = Db::all('SELECT c.*, p.title plan_title, p.base_price_toman FROM {campaigns} c JOIN {plans} p ON p.id = c.plan_id ORDER BY c.id DESC');
        $plans = Plans::all();
        Admin::layout('کمپین‌های تخفیف', function () use ($rows, $plans, $edit) {
            ?>
            <div class="alert info">
              تخفیف فقط وقتی در اپ نمایش داده می‌شود که کاربر واقعاً همان قیمت کمتر را بپردازد:
              <br>• <b>بازار:</b> اگر «کلید تخفیف پویا» در تنظیمات بازار ثبت شده باشد، سرور برای هر خرید یک توکن امضاشده می‌سازد و بازار همان قیمت کمتر را می‌گیرد (بازار ۱۳٫۳ یا جدیدتر). در غیر این صورت قیمت محصول را در پنل بازار کم کنید و قیمت تأییدشده را در صفحهٔ پلن‌ها ثبت کنید.
              <br>• <b>مایکت:</b> قیمت را با دکمهٔ «ارسال قیمت به مایکت» در صفحهٔ پلن‌ها یا در پنل مایکت تغییر دهید. پس از پایان کمپین، قیمت عادی را برگردانید.
              <br>تا قیمت فروشگاه با قیمت کمپین یکی نشود، آن پلن در همان فروشگاه غیرقابل خرید نشان داده می‌شود.
            </div>
            <div class="card"><h3><?= $edit ? 'ویرایش کمپین' : 'کمپین جدید' ?></h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= (int)($edit['id'] ?? 0) ?>">
                <div class="row">
                  <div><label>نام</label><input type="text" name="name" value="<?= e($edit['name'] ?? '') ?>" required></div>
                  <div><label>پلن</label><select name="plan_id"><?php foreach ($plans as $p) echo '<option value="' . (int)$p['id'] . '"' . (($edit['plan_id'] ?? 0) == $p['id'] ? ' selected' : '') . '>' . e($p['title']) . ' — ' . money($p['base_price_toman']) . '</option>'; ?></select></div>
                  <div><label>فروشگاه</label><select name="store"><?php foreach (['all' => 'هر دو', 'bazaar' => 'بازار', 'myket' => 'مایکت'] as $k => $l) echo '<option value="' . $k . '"' . (($edit['store'] ?? 'all') === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select></div>
                  <div><label>قیمت کمپین (تومان)</label><input type="number" name="price_toman" value="<?= e($edit['price_toman'] ?? '') ?>" required></div>
                </div>
                <div class="row">
                  <div><label>برچسب</label><input type="text" name="label" value="<?= e($edit['label'] ?? '') ?>" placeholder="مثلاً: تخفیف نوروزی"></div>
                  <div><label>رنگ برچسب</label><input type="text" name="label_color" value="<?= e($edit['label_color'] ?? '#C23B48') ?>" class="ltr"></div>
                  <div><label>شروع (به وقت تهران)</label><input type="datetime-local" name="starts_at" value="<?= e(Admin::dtInput($edit['starts_at'] ?? null)) ?>"></div>
                  <div><label>پایان</label><input type="datetime-local" name="ends_at" value="<?= e(Admin::dtInput($edit['ends_at'] ?? null)) ?>"></div>
                </div>
                <label class="check"><input type="checkbox" name="active" <?= ($edit['active'] ?? 1) ? 'checked' : '' ?>> فعال</label>
                <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل (اجباری)" required minlength="5"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
              </form>
            </div>
            <div class="table-wrap"><table><thead><tr><th>نام</th><th>پلن</th><th>فروشگاه</th><th>قیمت</th><th>تخفیف</th><th>بازه</th><th>وضعیت در بازار / مایکت</th><th></th></tr></thead><tbody>
            <?php foreach ($rows as $c):
                $plan = Plans::byId((int)$c['plan_id']);
                $states = [];
                foreach (Plans::STORES as $s) {
                    if ($c['store'] !== 'all' && $c['store'] !== $s) continue;
                    $o = Plans::offer($plan, $s);
                    $running = Plans::campaign($plan, $s);
                    $states[] = Admin::storeName($s) . ': ' . ($running && (int)$running['id'] === (int)$c['id'] ? Admin::statusBadge($o['state']) : '<span class="badge">اجرا نمی‌شود</span>');
                } ?>
              <tr><td><?= e($c['name']) ?></td><td><?= e($c['plan_title']) ?></td><td><?= e(['all' => 'هر دو', 'bazaar' => 'بازار', 'myket' => 'مایکت'][$c['store']] ?? $c['store']) ?></td><td><?= money($c['price_toman']) ?></td>
                <td><?= fa_digits((int)round(((int)$c['base_price_toman'] - (int)$c['price_toman']) * 100 / max(1, (int)$c['base_price_toman']))) ?>٪</td>
                <td><?= e(jdate($c['starts_at'])) ?> تا <?= e(jdate($c['ends_at'])) ?></td><td><?= implode('<br>', $states) ?></td>
                <td><a class="btn sm ghost" href="index.php?p=campaigns&id=<?= (int)$c['id'] ?>">ویرایش</a>
                  <form method="post" style="display:inline"><?= Admin::csrfField() ?><input type="hidden" name="action" value="toggle"><input type="hidden" name="id" value="<?= (int)$c['id'] ?>"><input type="hidden" name="reason" value="<?= $c['active'] ? 'توقف کمپین از فهرست' : 'فعال‌سازی کمپین از فهرست' ?>"><button class="btn sm <?= $c['active'] ? 'red' : 'teal' ?>"><?= $c['active'] ? 'توقف' : 'فعال' ?></button></form></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="8" class="muted">کمپینی ثبت نشده.</td></tr>'; ?>
            </tbody></table></div>
            <?php
        }, 'campaigns');
    }

    // ================================================================ features
    public static function features(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $changes = [];
            foreach (Features::all() as $f) {
                $mode = (string)($_POST['mode'][$f['k']] ?? $f['mode']);
                if (!in_array($mode, Features::MODES, true)) continue;
                $limit = trim((string)($_POST['limit'][$f['k']] ?? ''));
                $limit = $limit === '' ? null : max(0, (int)$limit);
                $items = array_map('strval', (array)($_POST['items'][$f['k']] ?? []));
                foreach (preg_split('/[\s,،]+/u', (string)($_POST['extra'][$f['k']] ?? '')) as $x) if (preg_match('/^[A-Za-z0-9_.-]{1,64}$/', $x)) $items[] = $x;
                $items = implode(',', array_values(array_unique(array_filter($items, fn($x) => preg_match('/^[A-Za-z0-9_.-]{1,64}$/', $x)))));
                $old = (string)($f['pro_items'] ?? '');
                if ($mode !== $f['mode'] || $limit !== ($f['limit_value'] === null ? null : (int)$f['limit_value']) || $items !== $old) {
                    Db::update('features', ['mode' => $mode, 'limit_value' => $limit, 'pro_items' => $items ?: null, 'updated_at' => Clock::sql()], 'k = ?', [$f['k']]);
                    $changes[$f['k']] = [$f['mode'] . '/' . $f['limit_value'] . '/' . $old, $mode . '/' . $limit . '/' . $items];
                }
            }
            if ($changes) Admin::audit('features_update', 'features', null, $reason, null, $changes);
            Admin::flash($changes ? fa_digits(count($changes)) . ' مورد تغییر کرد؛ اپ‌ها حداکثر چند دقیقه بعد (با باز شدن برنامه) آن را می‌گیرند.' : 'تغییری نبود.');
            Admin::redirect('p=features');
        }
        $rows = Features::all();
        $groups = [];
        foreach ($rows as $f) $groups[$f['section'] ?? 'general'][] = $f;
        Admin::layout('قابلیت‌های رایگان و Pro', function () use ($groups) {
            ?>
            <div class="alert info">
              <b>رایگان:</b> برای همه باز است. <b>فقط Pro:</b> کاربر رایگان صفحهٔ معرفی Pro را می‌بیند. <b>رایگان محدود:</b> کاربر رایگان تا «محدودیت» (مثلاً تعداد لیگ یا عکس) استفاده می‌کند.
              <b>غیرفعال:</b> برای همه بسته است. <b>فقط هدیه:</b> فقط کسانی که دسترسی ویژه گرفته‌اند.
              <br>برای سناریوهای مافیا، کارت‌های حرکت آخر، نوع مسابقهٔ پانتومیم و سبک‌های ظاهری می‌توانید <b>هر مورد را جدا</b> Pro کنید (بخش را «رایگان» بگذارید و موردها را تیک بزنید).
              <br>بازی‌ای که کاربر شروع کرده و داده‌ای که ساخته هیچ‌وقت قفل یا پاک نمی‌شود؛ قانون فقط هنگام ورود به بخش یا شروع مورد تازه اعمال می‌شود.
            </div>
            <form method="post"><?= Admin::csrfField() ?>
            <?php foreach (Features::SECTIONS as $sec => $secTitle): if (empty($groups[$sec])) continue; ?>
            <div class="card"><h3><?= e($secTitle) ?></h3>
            <div class="table-wrap"><table><thead><tr><th>قابلیت</th><th>حالت</th><th>محدودیت رایگان</th></tr></thead><tbody>
            <?php foreach ($groups[$sec] as $f): $catalog = Catalog::items($f['k']); $sel = array_filter(explode(',', (string)($f['pro_items'] ?? ''))); ?>
              <tr><td><b><?= e($f['title']) ?></b><br><small class="muted"><?= e($f['description']) ?></small> <code><?= e($f['k']) ?></code>
                <?php if ($catalog): $extra = array_diff($sel, array_keys($catalog)); ?>
                  <details style="margin-top:6px"<?= $sel ? ' open' : '' ?>><summary>موردهای فقط Pro (<?= fa_digits(count($sel)) ?> از <?= fa_digits(count($catalog)) ?>)</summary>
                    <div class="grid g3" style="gap:2px;margin-top:6px">
                    <?php foreach ($catalog as $id => $title) echo '<label class="check"><input type="checkbox" name="items[' . e($f['k']) . '][]" value="' . e($id) . '"' . (in_array($id, $sel, true) ? ' checked' : '') . '> ' . e($title) . '</label>'; ?>
                    </div>
                    <input type="text" name="extra[<?= e($f['k']) ?>]" value="<?= e(implode(', ', $extra)) ?>" class="ltr" placeholder="شناسهٔ موردهای دیگر (برای نسخه‌های تازه‌تر اپ)" style="margin-top:6px">
                  </details>
                <?php endif; ?></td>
                <td><select name="mode[<?= e($f['k']) ?>]"><?php foreach (Features::MODES as $m) echo '<option value="' . $m . '"' . ($f['mode'] === $m ? ' selected' : '') . '>' . strip_tags(Admin::statusBadge($m)) . '</option>'; ?></select></td>
                <td><input type="number" name="limit[<?= e($f['k']) ?>]" value="<?= e($f['limit_value']) ?>" min="0" style="max-width:110px"></td></tr>
            <?php endforeach; ?>
            </tbody></table></div></div>
            <?php endforeach; ?>
            <div class="row" style="margin-top:12px"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل تغییر (اجباری)" required minlength="5"></div><div style="flex:0"><button class="btn gold" data-confirm="تغییر دسترسی‌ها برای همهٔ کاربران اعمال شود؟">ذخیره</button></div></div>
            </form>
            <?php
        }, 'features');
    }

    // ================================================================ ads
    public static function ads(): void
    {
        $rows = Db::all('SELECT a.*, m.path, m.kind mkind FROM {ads} a LEFT JOIN {media} m ON m.id = a.media_id ORDER BY a.active DESC, a.priority DESC, a.id DESC');
        $stats = [];
        foreach (Db::all('SELECT ad_id, SUM(impressions) im, SUM(clicks) cl FROM {ad_stats} WHERE ad_id > 0 GROUP BY ad_id') as $s) $stats[(int)$s['ad_id']] = $s;
        $on = (bool)Settings::get('ads_enabled', false);
        Admin::layout('تبلیغات', function () use ($rows, $stats, $on) {
            ?>
            <?php if (!$on): ?><div class="alert warn">نمایش تبلیغ در اپ خاموش است. از «شبکهٔ تبلیغاتی» روشنش کنید. کاربران Pro در هر حال هیچ تبلیغی نمی‌بینند.</div><?php endif; ?>
            <div class="card"><h3>آنچه اپ یک کاربر رایگان همین حالا دریافت می‌کند</h3>
              <?php foreach (Plans::STORES as $st): $c = Ads::configFor($st, false); ?>
                <p><b><?= Admin::storeName($st) ?>:</b>
                <?php if (empty($c['enabled'])): ?><span class="badge bad">هیچ تبلیغی (نمایش خاموش)</span>
                <?php else: $live = array_map(fn($x) => $x['title'] ?: ('#' . $x['id']), $c['ads']); $sticky = array_filter($c['placements'], fn($p) => $p['position'] === 'sticky' && $p['active']); ?>
                  <span class="badge ok"><?= fa_digits(count($c['ads'])) ?> تبلیغ مستقیم</span> <?= e(implode('، ', $live)) ?>
                  <?= $sticky ? ' — نوار ثابت: ' . e(implode('، ', array_map(fn($p) => $p['key'], $sticky))) : '' ?>
                  <?= !empty($c['network']['enabled']) ? ' — <span class="badge info">تپسل روشن</span>' : '' ?>
                <?php endif; ?></p>
              <?php endforeach; ?>
              <p class="hint">اپ این فهرست را هنگام باز شدن و حداکثر هر ۱۰ دقیقه یک بار می‌گیرد. تصویر و ویدئو اول دانلود می‌شود و بعد نمایش داده می‌شود.</p>
              <?php foreach ($rows as $a) { $pr = self::adProblems($a); if ($pr) echo '<div class="alert warn"><b>' . e($a['name']) . ':</b> ' . e(implode(' ', $pr)) . '</div>'; } ?>
            </div>
            <div class="alert info">تبلیغ هیچ‌وقت روی صفحهٔ داوری، تایمر، شب و رأی‌گیری مافیا، نمایش نقش یا هنگام ثبت امتیاز نشان داده نمی‌شود و همیشه برچسب «تبلیغات» دارد.</div>
            <div class="table-wrap"><table><thead><tr><th>تبلیغ</th><th>قالب</th><th>جایگاه‌ها</th><th>زمان‌بندی</th><th>نمایش / کلیک</th><th>اولویت</th><th>وضعیت</th></tr></thead><tbody>
            <?php foreach ($rows as $a): $s = $stats[(int)$a['id']] ?? ['im' => 0, 'cl' => 0];
                $live = $a['active'] && ($a['starts_at'] === null || Clock::ts($a['starts_at']) <= Clock::now()) && ($a['ends_at'] === null || Clock::ts($a['ends_at']) > Clock::now()); ?>
              <tr><td><a href="index.php?p=ad&id=<?= (int)$a['id'] ?>"><b><?= e($a['name']) ?></b></a><br><small class="muted"><?= e($a['title']) ?></small></td>
                <td><?= e(Ads::FORMATS[$a['format']] ?? $a['format']) ?></td><td><small><?= e(str_replace(',', '، ', $a['placements'])) ?></small></td>
                <td><small><?= e(jdate($a['starts_at'])) ?> تا <?= e(jdate($a['ends_at'])) ?></small></td>
                <td><?= fa_digits($s['im']) ?> / <?= fa_digits($s['cl']) ?><?= $a['total_cap'] ? '<br><small class="muted">سقف کل ' . fa_digits($a['total_cap']) . '</small>' : '' ?></td>
                <td><?= fa_digits($a['priority']) ?></td><td><?= $live ? '<span class="badge ok">در حال نمایش</span>' : ($a['active'] ? '<span class="badge warn">خارج از بازه</span>' : '<span class="badge">خاموش</span>') ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="7" class="muted">هنوز تبلیغی ساخته نشده.</td></tr>'; ?>
            </tbody></table></div>
            <?php
        }, 'ads', [Admin::writable() ? '<a class="btn gold" href="index.php?p=ad">تبلیغ جدید</a>' : '']);
    }

    /** Reasons an ad would not reach the app, in plain words. */
    public static function adProblems(array $a): array
    {
        $w = [];
        if (!Settings::get('ads_enabled', false)) $w[] = 'نمایش تبلیغ خاموش است؛ در «شبکهٔ تبلیغاتی» تیک «نمایش تبلیغ برای کاربران رایگان روشن باشد» را بزنید، وگرنه هیچ تبلیغی به اپ نمی‌رسد.';
        if (empty($a['active'])) $w[] = 'این تبلیغ غیرفعال است (تیک «فعال»).';
        if (!empty($a['starts_at']) && Clock::ts($a['starts_at']) > Clock::now()) $w[] = 'زمان شروع این تبلیغ هنوز نرسیده است.';
        if (!empty($a['ends_at']) && Clock::ts($a['ends_at']) <= Clock::now()) $w[] = 'زمان این تبلیغ تمام شده است.';
        if (!empty($a['total_cap']) && (int)($a['impressions_total'] ?? 0) >= (int)$a['total_cap']) $w[] = 'سقف نمایش کل این تبلیغ پر شده است.';
        $active = array_column(Db::all('SELECT k, active FROM {placements}'), 'active', 'k');
        $off = array_filter(explode(',', (string)$a['placements']), fn($k) => empty($active[$k]));
        if ($off) $w[] = 'این جایگاه‌ها خاموش‌اند: ' . implode('، ', $off) . ' (صفحهٔ «جایگاه‌های تبلیغ»).';
        if (Settings::get('ads_priority', 'direct_first') === 'network_only') $w[] = 'ترتیب منبع روی «فقط شبکه» است؛ تبلیغ مستقیم نمایش داده نمی‌شود.';
        return $w;
    }

    public static function ad(): void
    {
        $id = (int)($_GET['id'] ?? 0);
        $ad = $id ? Db::one('SELECT * FROM {ads} WHERE id = ?', [$id]) : null;
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            if (($_POST['action'] ?? '') === 'delete' && $ad !== null) {
                Db::run('DELETE FROM {ads} WHERE id = ?', [$id]);
                Admin::audit('ad_delete', 'ad', $id, $reason, $ad);
                Admin::flash('تبلیغ حذف شد.');
                Admin::redirect('p=ads');
            }
            $format = self::post('format');
            if (!isset(Ads::FORMATS[$format])) throw new ApiError('format', 'قالب تبلیغ نامعتبر است.');
            $mediaId = self::intOrNull('media_id');
            if (!empty($_FILES['upload']['name'])) $mediaId = Media::store($_FILES['upload']);
            if ($mediaId !== null && Db::value('SELECT id FROM {media} WHERE id = ?', [$mediaId]) === null) $mediaId = null;
            if (in_array($format, ['image', 'video', 'corner', 'rewarded'], true) && $mediaId === null) throw new ApiError('media', 'این قالب به تصویر یا ویدئو نیاز دارد.');
            $url = self::post('target_app') !== '' ? self::post('target_app') : self::post('target_url');
            if (!safe_target_url($url)) throw new ApiError('url', 'نشانی مقصد باید http یا https و عمومی باشد، یا یکی از صفحه‌های داخل اپ.');
            $valid = array_column(Ads::placements(), 'formats', 'k');
            $places = array_values(array_filter((array)($_POST['placements'] ?? []), fn($p) => isset($valid[$p]) && in_array($format, explode(',', $valid[$p]), true)));
            if (!$places) throw new ApiError('placements', 'دست‌کم یک جایگاه سازگار با این قالب انتخاب کنید.');
            $stores = array_values(array_intersect((array)($_POST['stores'] ?? []), Plans::STORES));
            if (!$stores) throw new ApiError('stores', 'دست‌کم یک فروشگاه را انتخاب کنید.');
            $style = [];
            foreach (['bg', 'fg', 'accent'] as $k) { $v = self::post('style_' . $k); if (preg_match('/^#[0-9a-fA-F]{6}$/', $v)) $style[$k] = $v; }
            $starts = Admin::dtParse(self::post('starts_at')); $ends = Admin::dtParse(self::post('ends_at'));
            $row = [
                'name' => mb_substr(self::post('name'), 0, 80) ?: 'تبلیغ', 'title' => mb_substr(self::post('title'), 0, 80) ?: null,
                'body' => mb_substr(self::post('body'), 0, 300) ?: null, 'format' => $format, 'media_id' => $mediaId,
                'target_url' => $url ?: null, 'cta_text' => mb_substr(self::post('cta_text'), 0, 30) ?: null,
                'style_json' => $style ? json_encode($style) : null, 'closable' => isset($_POST['closable']) || $format === 'interstitial' ? 1 : 0,
                'starts_at' => $starts, 'ends_at' => $ends, 'total_cap' => self::intOrNull('total_cap'), 'daily_cap' => self::intOrNull('daily_cap'),
                'min_interval_sec' => max(0, (int)self::post('min_interval_sec', '0')), 'priority' => (int)self::post('priority', '0'),
                'placements' => implode(',', $places), 'stores' => implode(',', $stores), 'active' => isset($_POST['active']) ? 1 : 0, 'updated_at' => Clock::sql(),
            ];
            if ($ad !== null) { Db::update('ads', $row, 'id = ?', [$id]); Admin::audit('ad_update', 'ad', $id, $reason, $ad, $row); }
            else { $id = Db::insert('ads', $row + ['created_at' => Clock::sql()]); Admin::audit('ad_create', 'ad', $id, $reason, null, $row); }
            Admin::flash('تبلیغ ذخیره شد. اپ‌ها حداکثر چند دقیقه بعد (با باز شدن برنامه) آن را می‌گیرند.');
            foreach (self::adProblems($row) as $warn) Admin::flash($warn, 'warn');
            Admin::redirect('p=ad&id=' . $id);
        }
        $media = Db::all('SELECT * FROM {media} ORDER BY id DESC LIMIT 200');
        $placements = Ads::placements();
        $a = $ad ?? ['name' => '', 'title' => '', 'body' => '', 'format' => 'card', 'media_id' => null, 'target_url' => '', 'cta_text' => 'مشاهده', 'style_json' => null, 'closable' => 1, 'starts_at' => null, 'ends_at' => null, 'total_cap' => null, 'daily_cap' => 3, 'min_interval_sec' => 0, 'priority' => 0, 'placements' => 'home_inline', 'stores' => 'bazaar,myket', 'active' => 1];
        $style = json_decode((string)$a['style_json'], true) ?: [];
        $base = App::baseUrl();
        Admin::layout($ad ? 'ویرایش تبلیغ' : 'تبلیغ جدید', function () use ($a, $ad, $media, $placements, $style, $base) {
            $sel = explode(',', $a['placements']);
            ?>
            <div class="grid g2" style="grid-template-columns:minmax(0,1.2fr) minmax(0,1fr)">
            <div class="card">
              <form method="post" enctype="multipart/form-data" id="ad-form"><?= Admin::csrfField() ?>
                <div class="row">
                  <div><label>نام داخلی</label><input type="text" name="name" value="<?= e($a['name']) ?>" required></div>
                  <div><label>قالب</label><select name="format" id="f-format"><?php foreach (Ads::FORMATS as $k => $l) echo '<option value="' . $k . '"' . ($a['format'] === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select></div>
                </div>
                <label>عنوان</label><input type="text" name="title" id="f-title" value="<?= e($a['title']) ?>" maxlength="80">
                <label>متن</label><input type="text" name="body" id="f-body" value="<?= e($a['body']) ?>" maxlength="300">
                <div class="row">
                  <div><label>متن دکمه</label><input type="text" name="cta_text" id="f-cta" value="<?= e($a['cta_text']) ?>" maxlength="30"></div>
                  <div><label>مقصد داخل اپ (دکمه کجا برود)</label><select name="target_app" id="f-target-app"><option value="">— لینک وب (کادر بعدی) —</option>
                    <?php foreach (Ads::APP_TARGETS as $k => $l) echo '<option value="' . e($k) . '"' . ($a['target_url'] === $k ? ' selected' : '') . '>' . e($l) . '</option>'; ?></select></div>
                </div>
                <label>یا نشانی وب</label><input type="url" name="target_url" value="<?= e(strpos((string)$a['target_url'], 'divan://') === 0 ? '' : $a['target_url']) ?>" class="ltr" placeholder="https://">
                <label>تصویر یا ویدئو</label>
                <select name="media_id" id="f-media"><option value="">— بدون رسانه —</option>
                  <?php foreach ($media as $m) echo '<option value="' . (int)$m['id'] . '" data-url="' . e($base . '/media/' . $m['path']) . '" data-kind="' . e($m['kind']) . '"' . ((int)$a['media_id'] === (int)$m['id'] ? ' selected' : '') . '>#' . fa_digits($m['id']) . ' ' . e($m['original_name']) . ' (' . e($m['kind']) . ($m['width'] ? ' ' . fa_digits($m['width'] . '×' . $m['height']) : '') . ')</option>'; ?>
                </select>
                <div class="hint">یا فایل تازه: <input type="file" name="upload" id="f-upload" accept="image/jpeg,image/png,image/webp,image/gif,video/mp4"> (تصویر تا ۲ و ویدئو تا ۱۵ مگابایت)</div>
                <div class="row">
                  <div><label>رنگ زمینه</label><input type="text" name="style_bg" id="f-bg" value="<?= e($style['bg'] ?? '#1C2A4A') ?>" class="ltr"></div>
                  <div><label>رنگ متن</label><input type="text" name="style_fg" id="f-fg" value="<?= e($style['fg'] ?? '#FFFFFF') ?>" class="ltr"></div>
                  <div><label>رنگ دکمه</label><input type="text" name="style_accent" id="f-accent" value="<?= e($style['accent'] ?? '#C39A4A') ?>" class="ltr"></div>
                </div>
                <label>جایگاه‌ها (فقط جایگاه‌های سازگار با قالب ذخیره می‌شوند)</label>
                <div class="grid g2" style="gap:2px">
                <?php foreach ($placements as $p) echo '<label class="check" data-formats="' . e($p['formats']) . '"><input type="checkbox" name="placements[]" value="' . e($p['k']) . '"' . (in_array($p['k'], $sel, true) ? ' checked' : '') . '> ' . e($p['title']) . '</label>'; ?>
                </div>
                <div class="row">
                  <div><label>شروع (تهران)</label><input type="datetime-local" name="starts_at" value="<?= e(Admin::dtInput($a['starts_at'])) ?>"></div>
                  <div><label>پایان</label><input type="datetime-local" name="ends_at" value="<?= e(Admin::dtInput($a['ends_at'])) ?>"></div>
                </div>
                <div class="row">
                  <div><label>سقف نمایش روزانه برای هر کاربر</label><input type="number" name="daily_cap" value="<?= e($a['daily_cap']) ?>" min="1"></div>
                  <div><label>سقف نمایش کل</label><input type="number" name="total_cap" value="<?= e($a['total_cap']) ?>" min="1"></div>
                  <div><label>فاصلهٔ کمینه (ثانیه)</label><input type="number" name="min_interval_sec" value="<?= (int)$a['min_interval_sec'] ?>" min="0"></div>
                  <div><label>اولویت</label><input type="number" name="priority" value="<?= (int)$a['priority'] ?>"></div>
                </div>
                <div class="row">
                  <label class="check"><input type="checkbox" name="stores[]" value="bazaar" <?= strpos($a['stores'], 'bazaar') !== false ? 'checked' : '' ?>> نسخهٔ بازار</label>
                  <label class="check"><input type="checkbox" name="stores[]" value="myket" <?= strpos($a['stores'], 'myket') !== false ? 'checked' : '' ?>> نسخهٔ مایکت</label>
                  <label class="check"><input type="checkbox" name="closable" <?= $a['closable'] ? 'checked' : '' ?>> قابل بستن</label>
                  <label class="check"><input type="checkbox" name="active" <?= $a['active'] ? 'checked' : '' ?>> فعال</label>
                </div>
                <div class="row" style="margin-top:8px"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل (اجباری)" required minlength="5"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
              </form>
              <?php if ($ad): ?><form method="post" style="margin-top:10px" class="row"><?= Admin::csrfField() ?><input type="hidden" name="action" value="delete"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل حذف" required minlength="5"></div><div style="flex:0"><button class="btn red" data-confirm="این تبلیغ حذف شود؟">حذف تبلیغ</button></div></form><?php endif; ?>
            </div>
            <div>
              <div class="card"><h3>پیش‌نمایش</h3>
                <p class="hint">نمای تقریبی در اپ؛ شکل نهایی با سبک ظاهری انتخابی کاربر هماهنگ می‌شود.</p>
                <div class="phones" id="ad-preview"></div>
              </div>
            </div>
            </div>
            <?php
        }, 'ads');
    }

    // ================================================================ placements
    public static function placements(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $defaults = [];
            foreach (Ads::defaultPlacements() as $d) $defaults[$d[0]] = [$d[4], $d[5]];
            foreach (Ads::placements() as $p) {
                $active = isset($_POST['active'][$p['k']]) ? 1 : 0;
                // Formats can be narrowed but never widened past the safe defaults of the slot.
                $allowed = explode(',', $defaults[$p['k']][0] ?? $p['formats']);
                $formats = array_values(array_intersect((array)($_POST['formats'][$p['k']] ?? []), $allowed));
                $row = ['active' => $active, 'formats' => implode(',', $formats ?: $allowed)];
                if ($p['position'] === 'sticky') $row['screens'] = implode(',', array_values(array_intersect((array)($_POST['screens'][$p['k']] ?? []), array_keys(Ads::ZONES))));
                if ($row['active'] !== (int)$p['active'] || $row['formats'] !== $p['formats'] || ($row['screens'] ?? $p['screens']) !== $p['screens']) {
                    Db::update('placements', $row, 'k = ?', [$p['k']]);
                    Admin::audit('placement_update', 'placement', $p['k'], $reason, ['active' => $p['active'], 'formats' => $p['formats']], $row);
                }
            }
            Admin::flash('جایگاه‌ها ذخیره شد.');
            Admin::redirect('p=placements');
        }
        $rows = Ads::placements();
        $defaults = [];
        foreach (Ads::defaultPlacements() as $d) $defaults[$d[0]] = explode(',', $d[4]);
        Admin::layout('جایگاه‌های تبلیغ', function () use ($rows, $defaults) {
            $on = [];
            foreach ($rows as $p) if ($p['active']) $on[$p['k']] = true;
            $slot = fn($k, $label, $cls = '') => '<div class="slot ' . $cls . (isset($on[$k]) ? ' on' : '') . '"><span class="ad-label">تبلیغات</span>' . e($label) . '</div>';
            ?>
            <div class="card"><h3>نقشهٔ جایگاه‌ها</h3>
              <p class="hint">کادرهای طلایی پررنگ جایگاه‌های روشن هستند. صفحه‌های داوری، تایمر و مافیا هیچ جایگاهی ندارند و قابل افزودن هم نیستند. نوار ثابت بالا یا پایین در صفحه‌هایی که تیک بزنید همیشه سر جایش می‌ماند و فضای خودش را دارد، پس روی دکمه‌ها نمی‌افتد.</p>
              <div class="phones">
                <div class="phone"><div class="screen"><div class="blk">دیوان امتیاز — خانه</div><div class="blk">ادامهٔ داوری</div><?= $slot('home_inline', 'میان بخش‌ها') ?><div class="blk">بازی‌ها</div><?= $slot('home_bottom', 'پایین صفحه') ?><?= $slot('home_corner', 'گوشه', 'corner') ?><div class="nav"></div></div></div>
                <div class="phone light"><div class="screen"><div class="blk">تاریخچه</div><div class="blk">امروز — ۳ بازی</div><?= $slot('history_inline', 'میان فهرست') ?><div class="blk">دیروز</div><div class="blk">رنک‌بندی</div><?= $slot('ranking_inline', 'پایین رنک‌بندی') ?><div class="nav"></div></div></div>
                <div class="phone"><div class="screen"><div class="blk">نتیجهٔ بازی (پایان‌یافته)</div><div class="blk">برنده: تیم الف</div><?= $slot('result_bottom', 'پایین نتیجه') ?><div class="blk">مافیا / پانتومیم / جاسوس</div><?= $slot('hub_inline', 'صفحهٔ بازی‌ها') ?><?= $slot('settings_bottom', 'پایین تنظیمات') ?><div class="nav"></div></div></div>
                <div class="phone"><div class="screen"><?= $slot('sticky_top', 'نوار ثابت بالا') ?><div class="blk">هر صفحهٔ مجاز</div><div class="blk">محتوای صفحه بالا و پایین می‌رود؛ نوار ثابت می‌ماند و چیزی را نمی‌پوشاند</div><div class="blk">…</div><div style="margin-top:auto"></div><?= $slot('sticky_bottom', 'نوار ثابت پایین') ?><div class="nav" style="margin-top:6px"></div></div></div>
                <div class="phone light"><div class="screen" style="justify-content:center"><?= $slot('between_games', 'تمام‌صفحه بین دو فعالیت — حداکثر هر ۱۰ دقیقه، قابل بستن') ?><?= $slot('rewarded_offer', 'ویدئوی جایزه‌ای — فقط با انتخاب کاربر، یک ساعت بدون تبلیغ') ?></div></div>
              </div>
            </div>
            <form method="post"><?= Admin::csrfField() ?>
            <div class="table-wrap"><table><thead><tr><th>جایگاه</th><th>صفحه / محل</th><th>قالب‌های مجاز تبلیغ مستقیم</th><th>قالب شبکه</th><th>روشن</th></tr></thead><tbody>
            <?php foreach ($rows as $p): $cur = explode(',', $p['formats']); ?>
              <tr><td><b><?= e($p['title']) ?></b><br><code><?= e($p['k']) ?></code></td><td><?= e($p['screen']) ?> / <?= e($p['position']) ?></td>
                <td><?php foreach ($defaults[$p['k']] ?? $cur as $f) echo '<label class="check" style="display:inline-flex;margin-left:8px"><input type="checkbox" name="formats[' . e($p['k']) . '][]" value="' . e($f) . '"' . (in_array($f, $cur, true) ? ' checked' : '') . '> ' . e(Ads::FORMATS[$f] ?? $f) . '</label>'; ?>
                  <?php if ($p['position'] === 'sticky'): $zs = explode(',', (string)$p['screens']); ?>
                    <div style="margin-top:6px"><b class="muted">در این صفحه‌ها:</b><br><?php foreach (Ads::ZONES as $z => $zl) echo '<label class="check" style="display:inline-flex;margin-left:8px"><input type="checkbox" name="screens[' . e($p['k']) . '][]" value="' . e($z) . '"' . (in_array($z, $zs, true) ? ' checked' : '') . '> ' . e($zl) . '</label>'; ?></div>
                  <?php endif; ?></td>
                <td><?= e($p['network_formats'] ?: '—') ?></td>
                <td><input type="checkbox" name="active[<?= e($p['k']) ?>]" <?= $p['active'] ? 'checked' : '' ?>></td></tr>
            <?php endforeach; ?>
            </tbody></table></div>
            <div class="row" style="margin-top:12px"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل (اجباری)" required minlength="5"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
            </form>
            <?php
        }, 'placements');
    }

    // ================================================================ media
    public static function media(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            if (($_POST['action'] ?? '') === 'delete') {
                $reason = Admin::requireReason();
                $id = (int)($_POST['id'] ?? 0);
                Media::delete($id);
                Admin::audit('media_delete', 'media', $id, $reason);
                Admin::flash('فایل حذف شد.');
            } else {
                $id = Media::store($_FILES['file'] ?? []);
                Admin::audit('media_upload', 'media', $id, 'upload');
                Admin::flash('فایل بارگذاری شد.');
            }
            Admin::redirect('p=media');
        }
        $rows = Db::all('SELECT m.*, (SELECT COUNT(*) FROM {ads} a WHERE a.media_id = m.id) used FROM {media} m ORDER BY m.id DESC LIMIT 300');
        $base = App::baseUrl();
        Admin::layout('رسانه‌ها', function () use ($rows, $base) {
            ?>
            <div class="card"><form method="post" enctype="multipart/form-data" class="row"><?= Admin::csrfField() ?>
              <div><label>فایل تازه (JPG، PNG، WebP، GIF تا ۲ مگابایت؛ MP4 تا ۱۵ مگابایت)</label><input type="file" name="file" accept="image/jpeg,image/png,image/webp,image/gif,video/mp4" required></div>
              <div style="flex:0"><label>&nbsp;</label><button class="btn gold">بارگذاری</button></div>
            </form><p class="hint">نوع واقعی فایل بررسی می‌شود و نام آن تصادفی ذخیره می‌شود. پیشنهاد اندازه: بنر افقی ۱۰۸۰×۲۷۰، کارت ۱۰۸۰×۵۶۰، گوشه ۳۰۰×۳۰۰، تمام‌صفحه ۱۰۸۰×۱۹۲۰.</p></div>
            <div class="grid g4">
            <?php foreach ($rows as $m): $url = $base . '/media/' . $m['path']; ?>
              <div class="card" style="padding:10px">
                <?php if ($m['kind'] === 'video'): ?><video src="<?= e($url) ?>" style="width:100%;border-radius:10px" muted controls preload="metadata"></video>
                <?php else: ?><img src="<?= e($url) ?>" alt="" style="width:100%;border-radius:10px;max-height:160px;object-fit:contain;background:#f1eee6"><?php endif; ?>
                <div style="font-size:.8rem;margin-top:6px">#<?= fa_digits($m['id']) ?> <?= e($m['original_name']) ?><br><span class="muted"><?= fa_digits(number_format((int)round($m['size'] / 1024))) ?> KB<?= $m['width'] ? ' — ' . fa_digits($m['width'] . '×' . $m['height']) : '' ?> — <?= $m['used'] ? 'در ' . fa_digits($m['used']) . ' تبلیغ' : 'استفاده‌نشده' ?></span></div>
                <?php if (!$m['used']): ?><form method="post" class="row" style="gap:4px;margin-top:6px"><?= Admin::csrfField() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$m['id'] ?>"><input type="text" name="reason" placeholder="دلیل حذف" required minlength="5"><button class="btn sm red" style="flex:0">حذف</button></form><?php endif; ?>
              </div>
            <?php endforeach; ?>
            </div>
            <?php if (!$rows) echo '<p class="muted">هنوز فایلی بارگذاری نشده.</p>';
        }, 'media');
    }

    // ================================================================ ad network and global ad rules
    public static function adnetwork(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $before = ['ads_enabled' => Settings::get('ads_enabled'), 'ads_priority' => Settings::get('ads_priority'), 'tapsell' => Settings::get('tapsell')];
            Settings::set('ads_enabled', isset($_POST['ads_enabled']));
            Settings::set('ads_priority', in_array(self::post('ads_priority'), ['direct_first', 'network_first', 'direct_only', 'network_only'], true) ? self::post('ads_priority') : 'direct_first');
            Settings::set('ads_interstitial_min_minutes', max(3, (int)self::post('ads_interstitial_min_minutes', '10')));
            Settings::set('ads_daily_interstitial_cap', max(0, min(20, (int)self::post('ads_daily_interstitial_cap', '3'))));
            Settings::set('rewarded_ad_free_minutes', max(0, min(1440, (int)self::post('rewarded_ad_free_minutes', '60'))));
            $zones = [];
            foreach (['banner', 'native', 'interstitial', 'rewarded'] as $z) {
                $v = self::post('zone_' . $z);
                $zones[$z] = preg_match('/^[A-Za-z0-9_-]{0,64}$/', $v) ? $v : '';
            }
            Settings::set('tapsell', ['enabled' => isset($_POST['tapsell_enabled']), 'zones' => $zones, 'test' => isset($_POST['tapsell_test'])]);
            Admin::audit('ads_settings', 'settings', 'ads', $reason, $before, ['ads_enabled' => Settings::get('ads_enabled'), 'ads_priority' => Settings::get('ads_priority'), 'tapsell' => Settings::get('tapsell')]);
            Admin::flash('تنظیمات تبلیغات ذخیره شد.');
            Admin::redirect('p=adnetwork');
        }
        $t = Settings::get('tapsell', []);
        Admin::layout('شبکهٔ تبلیغاتی', function () use ($t) {
            ?>
            <form method="post"><?= Admin::csrfField() ?>
            <div class="grid g2">
            <div class="card"><h3>قواعد کلی</h3>
              <label class="check"><input type="checkbox" name="ads_enabled" <?= Settings::get('ads_enabled', false) ? 'checked' : '' ?>> نمایش تبلیغ برای کاربران رایگان روشن باشد</label>
              <p class="hint">کاربران Pro هرگز تبلیغ نمی‌بینند و کتابخانهٔ تبلیغاتی برایشان اصلاً راه‌اندازی نمی‌شود.</p>
              <label>ترتیب منبع تبلیغ</label>
              <select name="ads_priority"><?php foreach (['direct_first' => 'اول تبلیغ مستقیم، اگر نبود شبکه', 'network_first' => 'اول شبکه، اگر پر نشد مستقیم', 'direct_only' => 'فقط تبلیغ مستقیم', 'network_only' => 'فقط شبکه'] as $k => $l) echo '<option value="' . $k . '"' . (Settings::get('ads_priority', 'direct_first') === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select>
              <div class="row">
                <div><label>فاصلهٔ کمینهٔ تمام‌صفحه (دقیقه)</label><input type="number" name="ads_interstitial_min_minutes" value="<?= (int)Settings::get('ads_interstitial_min_minutes', 10) ?>" min="3"></div>
                <div><label>سقف تمام‌صفحه در روز</label><input type="number" name="ads_daily_interstitial_cap" value="<?= (int)Settings::get('ads_daily_interstitial_cap', 3) ?>" min="0" max="20"></div>
                <div><label>دقیقهٔ بدون تبلیغ پس از ویدئوی جایزه‌ای</label><input type="number" name="rewarded_ad_free_minutes" value="<?= (int)Settings::get('rewarded_ad_free_minutes', 60) ?>" min="0"></div>
              </div>
            </div>
            <div class="card"><h3>تپسل (Tapsell Mediation)</h3>
              <label class="check"><input type="checkbox" name="tapsell_enabled" <?= !empty($t['enabled']) ? 'checked' : '' ?>> استفاده از تپسل</label>
              <label class="check"><input type="checkbox" name="tapsell_test" <?= !empty($t['test']) ? 'checked' : '' ?>> حالت آزمایشی (فقط برای بررسی پیش از انتشار)</label>
              <?php foreach (['banner' => 'شناسهٔ تبلیغ‌گاه بنر', 'native' => 'شناسهٔ تبلیغ‌گاه همسان (Native)', 'interstitial' => 'شناسهٔ تبلیغ‌گاه تمام‌صفحه', 'rewarded' => 'شناسهٔ تبلیغ‌گاه ویدئوی جایزه‌ای'] as $z => $l): ?>
                <label><?= $l ?></label><input type="text" name="zone_<?= $z ?>" value="<?= e($t['zones'][$z] ?? '') ?>" class="ltr">
              <?php endforeach; ?>
              <p class="hint">کلید اپ (App ID) تپسل هنگام ساخت APK در برنامه قرار می‌گیرد؛ شناسهٔ تبلیغ‌گاه‌ها از همین‌جا قابل تغییر است. تپسل API گزارش درآمد عمومی ندارد؛ درآمد را از پنل تپسل بخوانید و در «درآمد و عملکرد» با برچسب «واقعی» ثبت کنید.</p>
            </div>
            </div>
            <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل (اجباری)" required minlength="5"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
            </form>
            <?php
        }, 'adnetwork');
    }

    // ================================================================ stores
    public static function stores(): void
    {
        $result = $_SESSION['store_result'] ?? null; unset($_SESSION['store_result']);
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $action = (string)($_POST['action'] ?? 'save');
            if ($action === 'test_bazaar' || $action === 'test_myket') {
                $store = $action === 'test_bazaar' ? 'bazaar' : 'myket';
                $v = Stores::get($store)->verify('divan_connectivity_probe', 'probe-' . bin2hex(random_bytes(6)), 'consumable');
                $_SESSION['store_result'] = [$store, $v->state === StoreVerdict::INVALID
                    ? ['ok', 'ارتباط با ' . Admin::storeName($store) . ' برقرار است و کلید پذیرفته شد (رسید آزمایشی را نشناخت که درست است).']
                    : ['bad', 'نتیجه: ' . $v->state . ' — ' . $v->message]];
                Admin::redirect('p=stores');
            }
            if ($action === 'myket_products') {
                $list = (new MyketVerifier())->products();
                $_SESSION['store_result'] = ['myket', ['info', 'محصولات مایکت: ' . json_encode($list, JSON_UNESCAPED_UNICODE)]];
                Admin::redirect('p=stores');
            }
            self::ownerOrManagerSecrets();
            $reason = Admin::requireReason();
            $before = ['bazaar' => Settings::get('bazaar'), 'myket' => Settings::get('myket'), 'cross_store' => Settings::get('cross_store'), 'offline_days' => Settings::get('offline_days')];
            $pkg = fn($k) => preg_match('/^[a-zA-Z][a-zA-Z0-9_.]{2,100}$/', self::post($k)) ? self::post($k) : 'com.meysam.divanemtiaz';
            $key = fn($k) => preg_replace('/[^A-Za-z0-9+\/=]/', '', self::post($k));
            Settings::set('bazaar', ['enabled' => isset($_POST['bazaar_enabled']), 'package' => $pkg('bazaar_package'), 'public_key' => $key('bazaar_public_key'), 'client_id' => self::post('bazaar_client_id')]);
            Settings::set('myket', ['enabled' => isset($_POST['myket_enabled']), 'package' => $pkg('myket_package'), 'public_key' => $key('myket_public_key')]);
            // Secrets: an empty field keeps the stored value; "-" clears it.
            foreach (['bazaar_api_secret', 'bazaar_client_secret', 'bazaar_refresh_token', 'bazaar_discount_key', 'myket_access_token'] as $s) {
                $v = self::post($s);
                if ($v === '-') Settings::setSecret($s, '');
                elseif ($v !== '') Settings::setSecret($s, $v);
            }
            Settings::set('cross_store', isset($_POST['cross_store']));
            Settings::set('offline_days', max(1, min(90, (int)self::post('offline_days', '21'))));
            Settings::set('renew_grace_days', max(0, min(14, (int)self::post('renew_grace_days', '3'))));
            Settings::set('lifetime_offline_days', max(30, min(3650, (int)self::post('lifetime_offline_days', '365'))));
            Settings::set('single_device', isset($_POST['single_device']));
            Settings::set('device_moves_limit', max(0, min(50, (int)self::post('device_moves_limit', '3'))));
            Settings::set('device_offline_days', max(1, min(90, (int)self::post('device_offline_days', '7'))));
            Settings::set('device_offline_days_lifetime', max(1, min(365, (int)self::post('device_offline_days_lifetime', '30'))));
            Settings::set('support_text', mb_substr(self::post('support_text'), 0, 500));
            Admin::audit('store_settings', 'settings', 'stores', $reason, $before, ['bazaar' => Settings::get('bazaar'), 'myket' => Settings::get('myket'), 'cross_store' => Settings::get('cross_store'), 'offline_days' => Settings::get('offline_days'), 'secrets_changed' => array_values(array_filter(['bazaar_api_secret', 'bazaar_client_secret', 'bazaar_refresh_token', 'bazaar_discount_key', 'myket_access_token'], fn($s) => self::post($s) !== ''))]);
            Admin::flash('تنظیمات فروشگاه‌ها ذخیره شد.');
            Admin::redirect('p=stores');
        }
        $b = Settings::get('bazaar', []); $m = Settings::get('myket', []);
        $has = fn($s) => Settings::secret($s) !== '' ? '<span class="badge ok">ثبت شده</span>' : '<span class="badge warn">ثبت نشده</span>';
        Admin::layout('بازار و مایکت', function () use ($b, $m, $has, $result) {
            if ($result) echo '<div class="alert ' . e($result[1][0]) . '">' . e($result[1][1]) . '</div>';
            ?>
            <div class="alert info">هر نسخهٔ اپ فقط با فروشگاه خودش پرداخت می‌کند: نسخهٔ بازار با پرداخت درون‌برنامه‌ای بازار و نسخهٔ مایکت با پرداخت مایکت. سرور هر رسید را فقط از همان فروشگاهی که آن را صادر کرده بررسی می‌کند. کلیدهای محرمانه رمزنگاری‌شده در پایگاه داده ذخیره می‌شوند و دیگر نمایش داده نمی‌شوند.</div>
            <form method="post"><?= Admin::csrfField() ?>
            <div class="grid g2">
            <div class="card"><h3>کافه‌بازار</h3>
              <label class="check"><input type="checkbox" name="bazaar_enabled" <?= !empty($b['enabled']) ? 'checked' : '' ?>> فروش در نسخهٔ بازار فعال است</label>
              <label>نام بستهٔ برنامه</label><input type="text" name="bazaar_package" value="<?= e($b['package'] ?? 'com.meysam.divanemtiaz') ?>" class="ltr">
              <label>کلید عمومی RSA پرداخت درون‌برنامه‌ای</label><textarea name="bazaar_public_key" class="ltr" style="min-height:70px" placeholder="MIHNMA0GCSqGSIb3DQEBAQUAA..."><?= e($b['public_key'] ?? '') ?></textarea>
              <div class="hint">پنل توسعه‌دهندگان بازار ← برنامه ← پرداخت درون‌برنامه‌ای ← کلید RSA</div>
              <label>کلید API پیشخوان (CAFEBAZAAR-PISHKHAN-API-SECRET) <?= $has('bazaar_api_secret') ?></label><input type="password" name="bazaar_api_secret" class="ltr" autocomplete="new-password" placeholder="خالی = بدون تغییر، - = پاک کردن">
              <details><summary class="muted">روش قدیمی OAuth (فقط اگر کلید API ندارید)</summary>
                <label>Client ID</label><input type="text" name="bazaar_client_id" value="<?= e($b['client_id'] ?? '') ?>" class="ltr">
                <label>Client Secret <?= $has('bazaar_client_secret') ?></label><input type="password" name="bazaar_client_secret" class="ltr" autocomplete="new-password">
                <label>Refresh Token <?= $has('bazaar_refresh_token') ?></label><input type="password" name="bazaar_refresh_token" class="ltr" autocomplete="new-password">
              </details>
              <label>کلید تخفیف پویا <?= $has('bazaar_discount_key') ?></label><input type="password" name="bazaar_discount_key" class="ltr" autocomplete="new-password">
              <div class="hint">برای اجرای کمپین بدون تغییر قیمت در پنل بازار (نیازمند بازار ۱۳٫۳ یا جدیدتر).</div>
              <p><button class="btn ghost sm" name="action" value="test_bazaar" formnovalidate>آزمایش ارتباط سرور با بازار</button></p>
            </div>
            <div class="card"><h3>مایکت</h3>
              <label class="check"><input type="checkbox" name="myket_enabled" <?= !empty($m['enabled']) ? 'checked' : '' ?>> فروش در نسخهٔ مایکت فعال است</label>
              <label>نام بستهٔ برنامه</label><input type="text" name="myket_package" value="<?= e($m['package'] ?? 'com.meysam.divanemtiaz') ?>" class="ltr">
              <label>کلید عمومی RSA</label><textarea name="myket_public_key" class="ltr" style="min-height:70px"><?= e($m['public_key'] ?? '') ?></textarea>
              <div class="hint">پنل توسعه‌دهندگان مایکت ← برنامه ← پرداخت درون‌برنامه‌ای</div>
              <label>توکن دسترسی API (X-Access-Token) <?= $has('myket_access_token') ?></label><input type="password" name="myket_access_token" class="ltr" autocomplete="new-password" placeholder="خالی = بدون تغییر، - = پاک کردن">
              <p class="row" style="gap:6px"><button class="btn ghost sm" name="action" value="test_myket" formnovalidate>آزمایش ارتباط با مایکت</button><button class="btn ghost sm" name="action" value="myket_products" formnovalidate>دریافت فهرست محصولات و قیمت‌ها</button></p>
              <p class="hint">مایکت اشتراک دوره‌ای ندارد؛ پلن‌های زمان‌دار به‌صورت محصول مصرفی فروخته می‌شوند و سرور دوره را حساب می‌کند. مادام‌العمر محصول مصرف‌نشدنی است.</p>
            </div>
            </div>
            <div class="card"><h3>قواعد دسترسی</h3>
              <label class="check"><input type="checkbox" name="cross_store" <?= Settings::get('cross_store', true) ? 'checked' : '' ?>> خرید از یک فروشگاه در نسخهٔ فروشگاه دیگر هم معتبر باشد (با همان حساب)</label>
              <div class="row">
                <div><label>اعتبار آفلاین گواهی (روز)</label><input type="number" name="offline_days" value="<?= (int)Settings::get('offline_days', 21) ?>" min="1" max="90"></div>
                <div><label>مهلت تمدید اشتراک فروشگاه (روز)</label><input type="number" name="renew_grace_days" value="<?= (int)Settings::get('renew_grace_days', 3) ?>" min="0" max="14"></div>
                <div><label>اعتبار آفلاین مادام‌العمر (روز)</label><input type="number" name="lifetime_offline_days" value="<?= (int)Settings::get('lifetime_offline_days', 365) ?>" min="30"></div>
              </div>
              <label class="check"><input type="checkbox" name="single_device" <?= Settings::get('single_device', true) ? 'checked' : '' ?>> هر اشتراک فقط روی یک گوشی فعال باشد</label>
              <div class="row">
                <div><label>سقف انتقال به گوشی دیگر (در ۳۰ روز)</label><input type="number" name="device_moves_limit" value="<?= (int)Settings::get('device_moves_limit', 3) ?>" min="0" max="50"></div>
                <div><label>اعتبار آفلاین با قانون تک‌گوشی (روز)</label><input type="number" name="device_offline_days" value="<?= (int)Settings::get('device_offline_days', 7) ?>" min="1" max="90"></div>
                <div><label>اعتبار آفلاین مادام‌العمر با قانون تک‌گوشی (روز)</label><input type="number" name="device_offline_days_lifetime" value="<?= (int)Settings::get('device_offline_days_lifetime', 30) ?>" min="1" max="365"></div>
              </div>
              <p class="hint">اشتراک روی گوشی خرید (یا اولین گوشی‌ای که Pro را می‌گیرد) فعال می‌شود. روی گوشی دیگر کاربر می‌تواند با رمز عبور، اشتراک را منتقل کند؛ گوشی قبلی در اولین اتصال یا حداکثر پس از پایان اعتبار آفلاین، Pro را از دست می‌دهد. هرچه اعتبار آفلاین کوتاه‌تر باشد، استفادهٔ هم‌زمان کمتر ممکن است.</p>
              <p class="hint">اگر کاربر تا این مدت اینترنت نداشته باشد هم Pro او کار می‌کند؛ پس از اتصال، گواهی تازه می‌گیرد. اشتراک زمان‌دار هیچ‌وقت بعد از تاریخ پایانش آفلاین تمدید نمی‌شود.</p>
              <label>متن راهنمای پشتیبانی در اپ</label><input type="text" name="support_text" value="<?= e(Settings::get('support_text', '')) ?>">
            </div>
            <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="دلیل تغییر (اجباری)" minlength="5"></div><div style="flex:0"><button class="btn gold" name="action" value="save">ذخیره</button></div></div>
            </form>
            <?php
        }, 'stores');
    }

    private static function ownerOrManagerSecrets(): void
    {
        self::managerOnly();
    }

    // ================================================================ backups
    public static function backups(): void
    {
        $show = $_SESSION['show_once'] ?? null; unset($_SESSION['show_once']);
        if (isset($_GET['download'])) {
            self::ownerOnly();
            $b = Db::one('SELECT * FROM {backups} WHERE id = ?', [(int)$_GET['download']]);
            $path = $b ? Backup::dir() . '/' . basename($b['file']) : '';
            if ($b === null || !is_file($path)) throw new ApiError('missing', 'فایل پشتیبان پیدا نشد.');
            Admin::audit('backup_download', 'backup', $b['id'], 'download');
            header('Content-Type: application/octet-stream');
            header('Content-Disposition: attachment; filename="' . basename($b['file']) . '"');
            header('Content-Length: ' . filesize($path));
            readfile($path);
            exit;
        }
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $action = (string)($_POST['action'] ?? '');
            if ($action === 'create') {
                $r = Backup::create('manual', self::post('note'));
                Backup::prune();
                Admin::audit('backup_create', 'backup', $r['id'], self::post('note') ?: 'manual');
                Admin::flash('پشتیبان ساخته شد.');
            } elseif ($action === 'show_key') {
                self::ownerOnly();
                if (!password_verify(self::post('password'), Admin::$me['password_hash'])) throw new ApiError('password', 'رمز عبور درست نیست.');
                $_SESSION['show_once'] = ['کلید پشتیبان', (string)App::cfg('backup_key', '')];
                Admin::audit('backup_key_viewed', 'backup', null, 'owner viewed backup key');
            } elseif ($action === 'restore') {
                self::ownerOnly();
                $reason = Admin::requireReason();
                if (!password_verify(self::post('password'), Admin::$me['password_hash'])) throw new ApiError('password', 'رمز عبور درست نیست.');
                $f = $_FILES['file'] ?? [];
                if (($f['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || !is_uploaded_file($f['tmp_name'])) throw new ApiError('upload', 'فایل پشتیبان بارگذاری نشد.');
                Backup::create('pre-restore', 'قبل از بازگردانی');
                $added = Backup::restore((string)file_get_contents($f['tmp_name']), isset($_POST['adopt_keys']), self::post('backup_key') ?: null);
                Admin::audit('backup_restore', 'backup', null, $reason, null, $added);
                $parts = [];
                foreach ($added as $t => $n) if ($n) $parts[] = $t . ': ' . $n;
                Admin::flash('بازگردانی انجام شد. ' . ($parts ? implode('، ', $parts) : 'ردیف تازه‌ای نبود.') . ' هیچ دادهٔ موجودی بازنویسی نشد.');
            }
            Admin::redirect('p=backups');
        }
        $rows = Backup::list();
        $cron = App::baseUrl() . '/cron.php?key=' . App::cfg('cron_key', '');
        Admin::layout('پشتیبان‌گیری', function () use ($rows, $show, $cron) {
            if ($show) echo '<div class="card"><h3>' . e($show[0]) . '</h3><div class="secret">' . e($show[1]) . '</div><p class="hint">این کلید را جدا از فایل‌های پشتیبان و بیرون از هاست نگه دارید؛ بدون آن هیچ پشتیبانی باز نمی‌شود.</p></div>';
            $err = (string)Settings::get('last_backup_error', '');
            ?>
            <?php if ($err): ?><div class="alert bad">آخرین پشتیبان خودکار: <?= e($err) ?></div><?php endif; ?>
            <div class="grid g2">
            <div class="card"><h3>پشتیبان‌گیری خودکار</h3>
              <p>آخرین پشتیبان: <b><?= e(Settings::get('last_backup_at') ? jdate(Clock::sql((int)Settings::get('last_backup_at'))) : 'هنوز گرفته نشده') ?></b></p>
              <p>هر روز یک پشتیبان رمزنگاری‌شده (AES-256-GCM) ساخته می‌شود و ۱۴ نسخهٔ آخر نگه داشته می‌شود. بهتر است در cPanel یک Cron Job روزانه بسازید:</p>
              <div class="secret" style="font-size:.78rem">curl -s "<?= e($cron) ?>" &gt;/dev/null</div>
              <p class="hint">اگر Cron نسازید، هر بار که مدیری وارد پنل شود و از آخرین اجرا بیش از یک ساعت گذشته باشد، همین کار انجام می‌شود.</p>
              <form method="post" class="row"><?= Admin::csrfField() ?><input type="hidden" name="action" value="create"><div style="flex:3"><input type="text" name="note" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ساخت پشتیبان همین حالا</button></div></form>
            </div>
            <?php if (Admin::owner()): ?>
            <div class="card"><h3>بازگردانی (فقط مدیر اصلی)</h3>
              <p class="hint">بازگردانی هیچ ردیف موجودی را بازنویسی نمی‌کند: خریدها و حساب‌هایی که بعد از پشتیبان ثبت شده‌اند دست نمی‌خورند و فقط ردیف‌های گم‌شده برمی‌گردند. پیش از بازگردانی خودکار یک پشتیبان تازه گرفته می‌شود.</p>
              <form method="post" enctype="multipart/form-data"><?= Admin::csrfField() ?><input type="hidden" name="action" value="restore">
                <label>فایل پشتیبان (.dvbk)</label><input type="file" name="file" required>
                <label>کلید پشتیبان (اگر فایل از نصب دیگری است)</label><input type="password" name="backup_key" class="ltr" autocomplete="off">
                <label class="check"><input type="checkbox" name="adopt_keys"> انتقال کلیدهای امضا و رمزنگاری (فقط روی نصب تازه و خالی؛ برای جابه‌جایی هاست)</label>
                <label>رمز عبور شما</label><input type="password" name="password" required autocomplete="current-password">
                <label>دلیل</label><input type="text" name="reason" required minlength="5">
                <p><button class="btn red" data-confirm="بازگردانی انجام شود؟">بازگردانی</button></p>
              </form>
              <form method="post" class="row" style="margin-top:10px"><?= Admin::csrfField() ?><input type="hidden" name="action" value="show_key"><div style="flex:3"><input type="password" name="password" placeholder="رمز عبور شما" required></div><div style="flex:0"><button class="btn ghost">نمایش کلید پشتیبان</button></div></form>
            </div>
            <?php endif; ?>
            </div>
            <div class="table-wrap"><table><thead><tr><th>#</th><th>زمان</th><th>نوع</th><th>حجم</th><th>SHA-256</th><th>یادداشت</th><th></th></tr></thead><tbody>
            <?php foreach ($rows as $b): ?>
              <tr><td><?= fa_digits($b['id']) ?></td><td><?= e(jdate($b['created_at'])) ?></td><td><?= e(['auto' => 'خودکار', 'manual' => 'دستی', 'pre-restore' => 'پیش از بازگردانی'][$b['kind']] ?? $b['kind']) ?></td><td class="ltr"><?= e(number_format((int)$b['size'] / 1024, 1)) ?> KB</td><td><code><?= e(substr($b['sha256'], 0, 16)) ?>…</code></td><td><?= e($b['note'] ?? '') ?></td>
                <td><?= Admin::owner() ? '<a class="btn sm ghost" href="index.php?p=backups&download=' . (int)$b['id'] . '">دانلود</a>' : '' ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="7" class="muted">هنوز پشتیبانی نیست.</td></tr>'; ?>
            </tbody></table></div>
            <?php
        }, 'backups');
    }

    // ================================================================ audit and security logs
    public static function audit(): void
    {
        $tab = ($_GET['tab'] ?? 'changes') === 'security' ? 'security' : 'changes';
        $kind = trim((string)($_GET['kind'] ?? ''));
        $q = trim((string)($_GET['q'] ?? ''));
        $pg = max(1, (int)($_GET['pg'] ?? 1));
        if ($tab === 'security') {
            $where = ['1=1']; $params = [];
            if ($kind !== '') { $where[] = 'kind = ?'; $params[] = $kind; }
            if ($q !== '') { $where[] = '(username LIKE ? OR ip = ?)'; $params[] = '%' . $q . '%'; $params[] = $q; }
            $w = implode(' AND ', $where);
            if (isset($_GET['csv'])) Admin::csv('security', ['id', 'kind', 'user_id', 'username', 'ip', 'details', 'created_utc'], array_map('array_values', Db::all("SELECT id, kind, user_id, username, ip, details, created_at FROM {security_events} WHERE $w ORDER BY id DESC LIMIT 50000", $params)));
            $total = (int)Db::value("SELECT COUNT(*) FROM {security_events} WHERE $w", $params);
            $rows = Db::all("SELECT * FROM {security_events} WHERE $w ORDER BY id DESC LIMIT 50 OFFSET " . (($pg - 1) * 50), $params);
            $kinds = array_column(Db::all('SELECT DISTINCT kind FROM {security_events}'), 'kind');
        } else {
            $where = ['1=1']; $params = [];
            if ($q !== '') { $where[] = '(l.action LIKE ? OR l.entity_id = ? OR l.reason LIKE ?)'; $params[] = '%' . $q . '%'; $params[] = $q; $params[] = '%' . $q . '%'; }
            $w = implode(' AND ', $where);
            if (isset($_GET['csv'])) Admin::csv('audit', ['id', 'actor_type', 'actor_id', 'action', 'entity', 'entity_id', 'reason', 'ip', 'created_utc'], array_map('array_values', Db::all("SELECT l.id, l.actor_type, l.actor_id, l.action, l.entity, l.entity_id, l.reason, l.ip, l.created_at FROM {audit_log} l WHERE $w ORDER BY l.id DESC LIMIT 50000", $params)));
            $total = (int)Db::value("SELECT COUNT(*) FROM {audit_log} l WHERE $w", $params);
            $rows = Db::all("SELECT l.*, a.username admin_name FROM {audit_log} l LEFT JOIN {admins} a ON l.actor_type = 'admin' AND a.id = l.actor_id WHERE $w ORDER BY l.id DESC LIMIT 50 OFFSET " . (($pg - 1) * 50), $params);
            $kinds = [];
        }
        Admin::layout('امنیت و گزارش تغییرات', function () use ($tab, $rows, $total, $kind, $kinds, $q, $pg) {
            $qs = 'p=audit&tab=' . $tab . '&kind=' . urlencode($kind) . '&q=' . urlencode($q);
            ?>
            <div class="row" style="margin-bottom:12px;flex:0"><a class="btn <?= $tab === 'changes' ? '' : 'ghost' ?>" href="index.php?p=audit">گزارش تغییرات</a> <a class="btn <?= $tab === 'security' ? '' : 'ghost' ?>" href="index.php?p=audit&tab=security">رویدادهای امنیتی</a></div>
            <form class="filters" method="get"><input type="hidden" name="p" value="audit"><input type="hidden" name="tab" value="<?= $tab ?>">
              <?php if ($tab === 'security'): ?><div><label>نوع</label><select name="kind"><option value="">همه</option><?php foreach ($kinds as $k) echo '<option value="' . e($k) . '"' . ($kind === $k ? ' selected' : '') . '>' . e(self::eventName($k)) . '</option>'; ?></select></div><?php endif; ?>
              <div><label>جست‌وجو</label><input type="search" name="q" value="<?= e($q) ?>"></div>
              <div><button class="btn">اعمال</button> <a class="btn ghost" href="index.php?<?= e($qs) ?>&csv=1">خروجی CSV</a></div>
            </form>
            <p class="muted"><?= fa_digits($total) ?> رویداد</p>
            <div class="table-wrap"><table>
            <?php if ($tab === 'security'): ?>
              <thead><tr><th>رویداد</th><th>کاربر</th><th>IP</th><th>جزئیات</th><th>زمان</th></tr></thead><tbody>
              <?php foreach ($rows as $r) echo '<tr><td>' . e(self::eventName($r['kind'])) . '</td><td>' . ($r['user_id'] ? '<a class="ltr" href="index.php?p=user&id=' . (int)$r['user_id'] . '">' . e($r['username']) . '</a>' : '<span class="ltr">' . e($r['username']) . '</span>') . '</td><td class="ltr">' . e($r['ip']) . '</td><td><small>' . e($r['details']) . '</small></td><td>' . e(jdate($r['created_at'])) . '</td></tr>'; ?>
            <?php else: ?>
              <thead><tr><th>اقدام</th><th>موضوع</th><th>دلیل</th><th>انجام‌دهنده</th><th>IP</th><th>زمان</th></tr></thead><tbody>
              <?php foreach ($rows as $r) echo '<tr><td><code>' . e($r['action']) . '</code></td><td>' . e($r['entity'] . ($r['entity_id'] !== null ? ' #' . $r['entity_id'] : '')) . '</td><td>' . e($r['reason']) . ($r['after_json'] ? '<details><summary class="muted">جزئیات</summary><pre class="ltr" style="white-space:pre-wrap;font-size:.72rem">' . e(mb_substr((string)$r['before_json'], 0, 1500)) . "\n→\n" . e(mb_substr((string)$r['after_json'], 0, 1500)) . '</pre></details>' : '') . '</td><td>' . e($r['actor_type'] === 'admin' ? ($r['admin_name'] ?? 'مدیر #' . $r['actor_id']) : ($r['actor_type'] === 'system' ? 'سیستم' : 'کاربر #' . $r['actor_id'])) . '</td><td class="ltr">' . e($r['ip']) . '</td><td>' . e(jdate($r['created_at'])) . '</td></tr>'; ?>
            <?php endif; if (!$rows) echo '<tr><td colspan="6" class="muted">—</td></tr>'; ?>
            </tbody></table></div>
            <?= Admin::pager($total, $pg, 50, $qs) ?>
            <?php
        }, 'audit');
    }

    // ================================================================ admins (owner only)
    public static function admins(): void
    {
        $show = $_SESSION['new_admin'] ?? null; unset($_SESSION['new_admin']);
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::ownerOnly();
            $reason = Admin::requireReason();
            $action = (string)($_POST['action'] ?? '');
            if ($action === 'create') {
                $username = self::post('username');
                if (!preg_match('/^[A-Za-z][A-Za-z0-9_.]{2,31}$/', $username)) throw new ApiError('username', 'نام کاربری مدیر باید ۳ تا ۳۲ حرف لاتین، رقم، نقطه یا زیرخط باشد.');
                $role = self::post('role');
                if (!isset(Admin::ROLES[$role]) || $role === 'owner') throw new ApiError('role', 'نقش نامعتبر است.');
                $pass = self::post('password');
                if (($err = Installer::validateAdminPassword($pass)) !== null) throw new ApiError('password', $err);
                if (Db::value('SELECT id FROM {admins} WHERE username = ?', [$username]) !== null) throw new ApiError('taken', 'این نام کاربری مدیر وجود دارد.');
                $secret = Totp::secret();
                $id = Db::insert('admins', ['username' => $username, 'password_hash' => Crypto::passwordHash($pass), 'role' => $role, 'totp_enc' => Crypto::encrypt($secret), 'totp_last_step' => 0, 'failed' => 0, 'active' => 1, 'created_at' => Clock::sql()]);
                $_SESSION['new_admin'] = [$username, $secret];
                Admin::audit('admin_create', 'admin', $id, $reason, null, ['username' => $username, 'role' => $role]);
            } else {
                $a = Db::one('SELECT * FROM {admins} WHERE id = ?', [(int)($_POST['id'] ?? 0)]);
                if ($a === null || (int)$a['id'] === (int)Admin::$me['id']) throw new ApiError('admin', 'این مدیر قابل تغییر نیست.');
                if ($action === 'toggle') Db::update('admins', ['active' => $a['active'] ? 0 : 1], 'id = ?', [$a['id']]);
                elseif ($action === 'role' && isset(Admin::ROLES[self::post('role')]) && self::post('role') !== 'owner') Db::update('admins', ['role' => self::post('role')], 'id = ?', [$a['id']]);
                elseif ($action === 'reset_totp') {
                    $secret = Totp::secret();
                    Db::update('admins', ['totp_enc' => Crypto::encrypt($secret), 'totp_last_step' => 0], 'id = ?', [$a['id']]);
                    $_SESSION['new_admin'] = [$a['username'], $secret];
                } else throw new ApiError('action', 'اقدام نامعتبر است.');
                Admin::audit('admin_' . $action, 'admin', $a['id'], $reason);
            }
            Admin::flash('انجام شد.');
            Admin::redirect('p=admins');
        }
        $rows = Db::all('SELECT * FROM {admins} ORDER BY id');
        Admin::layout('مدیران', function () use ($rows, $show) {
            if ($show) {
                $uri = Totp::uri($show[1], $show[0] . '@' . ($_SERVER['HTTP_HOST'] ?? 'divan'));
                echo '<div class="card"><h3>کد دومرحله‌ای «' . e($show[0]) . '»</h3><p>این QR را با Google Authenticator، Microsoft Authenticator یا Aegis اسکن کنید. فقط همین یک بار نمایش داده می‌شود.</p><div class="qr" id="qr" data-uri="' . e($uri) . '"></div><div class="secret" style="margin-top:8px">' . e($show[1]) . '</div></div>';
            }
            ?>
            <div class="table-wrap"><table><thead><tr><th>نام کاربری</th><th>نقش</th><th>آخرین ورود</th><th>وضعیت</th><th>اقدام</th></tr></thead><tbody>
            <?php foreach ($rows as $a): ?>
              <tr><td class="ltr"><?= e($a['username']) ?></td><td><?= e(Admin::ROLES[$a['role']] ?? $a['role']) ?></td><td><?= e(jdate($a['last_login_at'])) ?></td><td><?= $a['active'] ? '<span class="badge ok">فعال</span>' : '<span class="badge bad">غیرفعال</span>' ?></td>
                <td><?php if ((int)$a['id'] !== (int)Admin::$me['id'] && Admin::owner()): ?>
                  <form method="post" class="row" style="gap:4px"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= (int)$a['id'] ?>">
                    <select name="role" style="max-width:110px"><?php foreach (Admin::ROLES as $k => $l) if ($k !== 'owner') echo '<option value="' . $k . '"' . ($a['role'] === $k ? ' selected' : '') . '>' . $l . '</option>'; ?></select>
                    <input type="text" name="reason" placeholder="دلیل" required minlength="5" style="min-width:100px">
                    <button class="btn sm ghost" name="action" value="role">تغییر نقش</button>
                    <button class="btn sm ghost" name="action" value="reset_totp">کد دومرحله‌ای تازه</button>
                    <button class="btn sm <?= $a['active'] ? 'red' : 'teal' ?>" name="action" value="toggle"><?= $a['active'] ? 'غیرفعال' : 'فعال' ?></button>
                  </form><?php else: echo '<span class="muted">—</span>'; endif; ?></td></tr>
            <?php endforeach; ?>
            </tbody></table></div>
            <?php if (Admin::owner()): ?>
            <div class="card" style="margin-top:16px"><h3>مدیر تازه</h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="create">
                <div class="row">
                  <div><label>نام کاربری</label><input type="text" name="username" class="ltr" required></div>
                  <div><label>نقش</label><select name="role"><option value="manager">مدیر — همه‌چیز جز مدیران و بازگردانی</option><option value="support">پشتیبان — کاربران، خریدها، هدیه</option><option value="analyst">تحلیلگر — فقط مشاهدهٔ گزارش‌ها</option></select></div>
                  <div><label>رمز (۱۲+ نویسه با حرف بزرگ، کوچک و رقم)</label><input type="password" name="password" class="ltr" required autocomplete="new-password"></div>
                </div>
                <label>دلیل</label><input type="text" name="reason" required minlength="5">
                <p><button class="btn gold">ساخت مدیر</button></p>
              </form>
            </div>
            <?php endif; ?>
            <div class="card"><h3>دسترسی نقش‌ها</h3>
              <p><b>مدیر اصلی:</b> همه‌چیز، از جمله مدیران، دانلود و بازگردانی پشتیبان و کلید پشتیبان.<br>
              <b>مدیر:</b> پلن، قیمت، کمپین، قابلیت، تبلیغات، تنظیمات فروشگاه، هدیه، ابطال یا تأیید دستی خرید.<br>
              <b>پشتیبان:</b> دیدن کاربران و خریدها، بازنشانی رمز، باز کردن قفل، بررسی دوبارهٔ خرید و پاسخ به درخواست‌ها (بدون هدیه و ابطال).<br>
              <b>تحلیلگر:</b> فقط مشاهدهٔ داشبورد، گزارش‌ها، اشتراک‌ها و تراکنش‌ها.</p>
            </div>
            <script src="../assets/js/qrcode.js"></script>
            <?php
        }, 'admins');
    }

    // ================================================================ own account
    public static function account(): void
    {
        $show = $_SESSION['new_admin'] ?? null; unset($_SESSION['new_admin']);
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            $me = Admin::$me;
            if (!password_verify(self::post('password'), $me['password_hash'])) throw new ApiError('password', 'رمز فعلی درست نیست.');
            $step = Totp::verify((string)Crypto::decrypt($me['totp_enc']), self::post('code'), (int)$me['totp_last_step']);
            if ($step === null) throw new ApiError('totp', 'کد دومرحله‌ای درست نیست.');
            Db::update('admins', ['totp_last_step' => $step], 'id = ?', [$me['id']]);
            if (($_POST['action'] ?? '') === 'password') {
                if (($err = Installer::validateAdminPassword(self::post('new_password'))) !== null) throw new ApiError('password', $err);
                Db::update('admins', ['password_hash' => Crypto::passwordHash(self::post('new_password'))], 'id = ?', [$me['id']]);
                Admin::audit('admin_password_changed', 'admin', $me['id'], 'self');
                Admin::flash('رمز عوض شد.');
            } else {
                $secret = Totp::secret();
                Db::update('admins', ['totp_enc' => Crypto::encrypt($secret), 'totp_last_step' => 0], 'id = ?', [$me['id']]);
                $_SESSION['new_admin'] = [$me['username'], $secret];
                Admin::audit('admin_totp_reset', 'admin', $me['id'], 'self');
            }
            Admin::redirect('p=account');
        }
        Admin::layout('حساب من', function () use ($show) {
            if ($show) {
                $uri = Totp::uri($show[1], $show[0] . '@' . ($_SERVER['HTTP_HOST'] ?? 'divan'));
                echo '<div class="card"><h3>کد دومرحله‌ای تازه</h3><p>QR را اسکن کنید؛ کد قبلی دیگر کار نمی‌کند.</p><div class="qr" id="qr" data-uri="' . e($uri) . '"></div><div class="secret" style="margin-top:8px">' . e($show[1]) . '</div></div><script src="../assets/js/qrcode.js"></script>';
            }
            ?>
            <div class="grid g2">
            <div class="card"><h3>تغییر رمز</h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="password">
                <label>رمز فعلی</label><input type="password" name="password" required autocomplete="current-password">
                <label>رمز تازه (۱۲+ نویسه با حرف بزرگ، کوچک و رقم)</label><input type="password" name="new_password" required autocomplete="new-password">
                <label>کد دومرحله‌ای</label><input type="text" name="code" class="ltr" inputmode="numeric" maxlength="6" required>
                <p><button class="btn gold">تغییر رمز</button></p>
              </form>
            </div>
            <div class="card"><h3>کد دومرحله‌ای تازه</h3>
              <p class="hint">اگر گوشی برنامهٔ احراز هویت را عوض می‌کنید.</p>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="totp">
                <label>رمز فعلی</label><input type="password" name="password" required autocomplete="current-password">
                <label>کد دومرحله‌ای فعلی</label><input type="text" name="code" class="ltr" inputmode="numeric" maxlength="6" required>
                <p><button class="btn ghost">ساخت کد تازه</button></p>
              </form>
            </div>
            </div>
            <?php
        }, 'account');
    }
}
