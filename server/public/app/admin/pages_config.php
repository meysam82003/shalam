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
        $types = [
            'bazaar' => ['consumable' => 'مصرفی — هر خرید یک دوره', 'permanent' => 'غیرمصرفی — یک بار برای همیشه', 'subscription' => 'اشتراک بازار — تمدید خودکار'],
            'myket' => ['consumable' => 'مصرفی — هر خرید یک دوره', 'permanent' => 'غیرمصرفی — یک بار برای همیشه'],
        ];
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $action = (string)($_POST['action'] ?? 'save');
            if ($action === 'create') {
                $code = strtolower(self::post('code'));
                if (!preg_match('/^[a-z0-9_]{1,32}$/', $code)) throw new ApiError('code', 'کد پلن فقط حروف کوچک لاتین، رقم و _ باشد (مثلاً m12).');
                if (Plans::byCode($code) !== null) throw new ApiError('code', 'پلنی با این کد هست.');
                $life = isset($_POST['lifetime']);
                $days = $life ? null : max(1, (int)self::post('days', '30'));
                $price = max(1000, (int)self::post('price', '0'));
                $sku = preg_match('/^[a-z0-9_.]{1,64}$/', self::post('sku')) ? self::post('sku') : 'divan_pro_' . $code;
                $t = $life ? 'permanent' : 'consumable';
                $id = Db::insert('plans', [
                    'code' => $code, 'title' => mb_substr(self::post('title'), 0, 80) ?: 'اشتراک', 'description' => mb_substr(self::post('description'), 0, 400),
                    'duration_days' => $days, 'base_price_toman' => $price, 'sort' => (int)Db::value('SELECT COALESCE(MAX(sort), 0) + 1 FROM {plans}'), 'active' => 1,
                    'renewal_note' => $life ? 'بدون تمدید؛ دسترسی دائمی.' : 'بدون تمدید خودکار؛ خرید تازه به انتهای اشتراک فعلی اضافه می‌شود.',
                    'sku_bazaar' => $sku, 'sku_myket' => $sku, 'type_bazaar' => $t, 'type_myket' => $t,
                    'store_price_bazaar' => $price, 'store_price_myket' => $price, 'on_bazaar' => 1, 'on_myket' => 1, 'updated_at' => Clock::sql(),
                ]);
                Admin::audit('plan_create', 'plan', $id, $reason, null, ['code' => $code]);
                Admin::flash('پلن ساخته شد. همین شناسهٔ محصول و قیمت را در پنل بازار و مایکت هم بسازید.');
                Admin::redirect('p=plans');
            }
            if ($action === 'myket_read') {
                $found = 0;
                foreach ((new MyketVerifier())->products() as $item) {
                    if (!is_array($item)) continue;
                    $sku = (string)($item['sku'] ?? $item['productId'] ?? $item['id'] ?? '');
                    $rial = (int)($item['price'] ?? $item['priceAmount'] ?? 0);
                    if ($sku === '' || $rial <= 0) continue;
                    $plan = Plans::bySku('myket', $sku);
                    if ($plan === null) continue;
                    Db::update('plans', ['store_price_myket' => intdiv($rial, 10), 'updated_at' => Clock::sql()], 'id = ?', [$plan['id']]);
                    $found++;
                }
                Admin::audit('plan_myket_prices_read', 'plan', null, $reason, null, ['updated' => $found]);
                Admin::flash($found ? fa_digits($found) . ' قیمت از مایکت خوانده و ثبت شد.' : 'محصولی با شناسه‌های این پلن‌ها در مایکت پیدا نشد.');
                Admin::redirect('p=plans');
            }
            $plan = Plans::byId((int)($_POST['id'] ?? 0));
            if ($plan === null) throw new ApiError('plan', 'پلن پیدا نشد.');
            if ($action === 'myket_push') {
                if ($plan['type_myket'] !== 'consumable') throw new ApiError('type', 'مایکت تغییر قیمت از راه API را فقط برای محصول مصرفی پذیرفته؛ قیمت این محصول را در پنل مایکت تغییر دهید.');
                $price = Plans::storePrice($plan, 'myket');
                (new MyketVerifier())->setPrice((string)$plan['sku_myket'], $price);
                Admin::audit('plan_myket_price_pushed', 'plan', $plan['id'], $reason, null, ['store_price_myket' => $price]);
                Admin::flash('قیمت ' . money($price) . ' در مایکت ثبت شد.');
                Admin::redirect('p=plans');
            }
            $color = self::post('badge_color');
            if ($color !== '' && !preg_match('/^#[0-9a-fA-F]{6}$/', $color)) throw new ApiError('color', 'رنگ برچسب باید به شکل #RRGGBB باشد.');
            $row = [
                'title' => mb_substr(self::post('title'), 0, 80) ?: $plan['title'],
                'description' => mb_substr(self::post('description'), 0, 400),
                'sort' => (int)self::post('sort', '0'),
                'active' => isset($_POST['active']) ? 1 : 0,
                'badge_text' => mb_substr(self::post('badge_text'), 0, 40) ?: null,
                'badge_color' => $color ?: null,
                'renewal_note' => mb_substr(self::post('renewal_note'), 0, 300) ?: null,
                'purchase_limit' => self::intOrNull('purchase_limit'),
                'updated_at' => Clock::sql(),
            ];
            foreach (Plans::STORES as $s) {
                $name = Admin::storeName($s);
                $price = (int)self::post("price_$s");
                if ($price < 1000) throw new ApiError('price', "قیمت $name باید دست‌کم ۱٬۰۰۰ تومان باشد.");
                $list = self::intOrNull("list_$s");
                if ($list !== null && $list <= $price) $list = null; // a struck-through price is only shown when it is really higher
                $sku = self::post("sku_$s");
                if ($sku !== '' && !preg_match('/^[A-Za-z0-9_.]{1,64}$/', $sku)) throw new ApiError('sku', "شناسهٔ محصول $name فقط حروف لاتین، رقم، نقطه و _ باشد.");
                $type = self::post("type_$s");
                if (!isset($types[$s][$type])) $type = $plan['duration_days'] === null ? 'permanent' : 'consumable';
                if ($plan['duration_days'] === null && $type === 'subscription') $type = 'permanent';
                $row += ["on_$s" => isset($_POST["on_$s"]) ? 1 : 0, "sku_$s" => $sku ?: null, "type_$s" => $type, "store_price_$s" => $price, "list_price_$s" => $list];
            }
            $row['base_price_toman'] = $row['store_price_bazaar'];
            $before = array_intersect_key($plan, $row);
            Db::update('plans', $row, 'id = ?', [$plan['id']]);
            Admin::audit('plan_update', 'plan', $plan['id'], $reason, $before, $row);
            Admin::flash('«' . $row['title'] . '» ذخیره شد و حداکثر یک دقیقه بعد در اپ دیده می‌شود.');
            Admin::redirect('p=plans#plan' . (int)$plan['id']);
        }
        $plans = Plans::all();
        $myketApi = (new MyketVerifier())->configured();
        Admin::layout('پلن‌ها و قیمت‌ها', function () use ($plans, $types, $myketApi) {
            ?>
            <div class="alert info">
              هر فروشگاه قیمت و شناسهٔ محصول خودش را دارد. <b>همان شناسه و همان قیمتی را که در پنل توسعه‌دهندهٔ کافه‌بازار یا مایکت ساخته‌اید اینجا بنویسید.</b>
              اپ هنگام نمایش، قیمت را از خود فروشگاه هم می‌خواند؛ اگر با پنل فرق داشته باشد، قیمت فروشگاه نمایش داده می‌شود تا کاربر همان را ببیند که می‌پردازد.
              <br>برای تخفیف: قیمت را در پنل فروشگاه کم کنید، قیمت تازه را در «قیمت» و قیمت قبلی را در «قیمت قبل از تخفیف» بنویسید (خط‌خورده نمایش داده می‌شود).
            </div>
            <div class="table-wrap"><table><thead><tr><th>پلن</th><th>مدت</th><th>کافه‌بازار</th><th>مایکت</th></tr></thead><tbody>
            <?php foreach ($plans as $p): $ob = Plans::offer($p, 'bazaar'); $om = Plans::offer($p, 'myket'); ?>
              <tr><td><a href="#plan<?= (int)$p['id'] ?>"><b><?= e($p['title']) ?></b></a><?= $p['active'] ? '' : ' <span class="badge">غیرفعال</span>' ?></td>
                <td><?= $p['duration_days'] === null ? 'مادام‌العمر' : fa_digits($p['duration_days']) . ' روز' ?></td>
                <?php foreach ([$ob, $om] as $o): ?>
                <td><?= Admin::statusBadge($o['state']) ?> <b><?= money($o['final_price']) ?></b><?= $o['base_price'] > $o['final_price'] ? ' <s class="muted">' . money($o['base_price']) . '</s>' : '' ?><br><code class="ltr"><?= e($o['sku'] ?: '—') ?></code></td>
                <?php endforeach; ?></tr>
            <?php endforeach; ?>
            </tbody></table></div>
            <?php if ($myketApi): ?>
            <form method="post" style="margin:8px 0"><?= Admin::csrfField() ?><button class="btn ghost" name="action" value="myket_read">خواندن قیمت‌ها از مایکت</button> <small class="muted">قیمت محصولات هم‌شناسه را از مایکت می‌خواند و در ستون مایکت ثبت می‌کند.</small></form>
            <?php endif; ?>
            <?php foreach ($plans as $p): ?>
            <div class="card" id="plan<?= (int)$p['id'] ?>">
              <h3><?= e($p['title']) ?> <small class="muted">(<?= e($p['code']) ?> — <?= $p['duration_days'] === null ? 'مادام‌العمر' : fa_digits($p['duration_days']) . ' روز' ?>)</small></h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= (int)$p['id'] ?>">
                <div class="row">
                  <div><label>عنوان</label><input type="text" name="title" value="<?= e($p['title']) ?>" maxlength="80"></div>
                  <div style="flex:2"><label>توضیح</label><input type="text" name="description" value="<?= e($p['description']) ?>" maxlength="400"></div>
                  <div><label>برچسب</label><input type="text" name="badge_text" value="<?= e($p['badge_text']) ?>" maxlength="40" placeholder="مثلاً: محبوب‌ترین"></div>
                  <div style="flex:0;min-width:70px"><label>رنگ</label><input type="color" name="badge_color" value="<?= e($p['badge_color'] ?: '#C9A24B') ?>"></div>
                </div>
                <div class="grid g2" style="margin-top:8px">
                <?php foreach (Plans::STORES as $s): $o = Plans::offer($p, $s); ?>
                  <div class="blk" style="border:1px solid var(--line);border-radius:12px;padding:10px">
                    <h4 style="margin:0 0 6px"><?= Admin::storeName($s) ?> <?= Admin::statusBadge($o['state']) ?></h4>
                    <label class="check"><input type="checkbox" name="on_<?= $s ?>" <?= Plans::onStore($p, $s) ? 'checked' : '' ?>> در <?= Admin::storeName($s) ?> عرضه شود</label>
                    <label>شناسهٔ محصول (SKU)</label><input type="text" name="sku_<?= $s ?>" value="<?= e($p["sku_$s"]) ?>" class="ltr" placeholder="divan_pro_1m">
                    <label>نوع محصول</label><select name="type_<?= $s ?>"><?php foreach ($types[$s] as $k => $l) { if ($p['duration_days'] === null && $k === 'subscription') continue; echo '<option value="' . $k . '"' . ($p["type_$s"] === $k ? ' selected' : '') . '>' . $l . '</option>'; } ?></select>
                    <div class="row">
                      <div><label>قیمت (تومان)</label><input type="number" name="price_<?= $s ?>" value="<?= Plans::storePrice($p, $s) ?>" min="1000" step="100" required></div>
                      <div><label>قیمت قبل از تخفیف</label><input type="number" name="list_<?= $s ?>" value="<?= e($p["list_price_$s"] ?? '') ?>" min="0" step="100" placeholder="خالی = بدون تخفیف"></div>
                    </div>
                    <?php if ($s === 'myket' && $myketApi && $p['type_myket'] === 'consumable' && $p['sku_myket']): ?><button class="btn sm ghost" name="action" value="myket_push" data-confirm="قیمت ذخیره‌شدهٔ این پلن در مایکت هم ثبت شود؟">ثبت همین قیمت در مایکت</button><?php endif; ?>
                  </div>
                <?php endforeach; ?>
                </div>
                <details style="margin-top:8px"><summary>بیشتر</summary>
                  <div class="row">
                    <div><label>ترتیب نمایش</label><input type="number" name="sort" value="<?= (int)$p['sort'] ?>"></div>
                    <div><label>سقف خرید هر حساب</label><input type="number" name="purchase_limit" value="<?= e($p['purchase_limit']) ?>" min="1" placeholder="نامحدود"></div>
                    <div style="flex:2"><label>متن تمدید</label><input type="text" name="renewal_note" value="<?= e($p['renewal_note']) ?>" maxlength="300"></div>
                  </div>
                </details>
                <div class="row" style="margin-top:8px;align-items:center">
                  <label class="check" style="flex:0;white-space:nowrap"><input type="checkbox" name="active" <?= $p['active'] ? 'checked' : '' ?>> فعال</label>
                  <div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div>
                  <div style="flex:0"><button class="btn gold" name="action" value="save">ذخیره</button></div>
                </div>
              </form>
            </div>
            <?php endforeach; ?>
            <div class="card"><h3>پلن تازه</h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="create">
                <div class="row">
                  <div><label>کد (لاتین)</label><input type="text" name="code" class="ltr" placeholder="m12" required></div>
                  <div><label>عنوان</label><input type="text" name="title" placeholder="اشتراک یک‌ساله" required></div>
                  <div><label>مدت (روز)</label><input type="number" name="days" value="365" min="1"></div>
                  <div><label>قیمت (تومان)</label><input type="number" name="price" min="1000" step="100" required></div>
                  <div><label>شناسهٔ محصول</label><input type="text" name="sku" class="ltr" placeholder="divan_pro_12m"></div>
                </div>
                <label>توضیح</label><input type="text" name="description" maxlength="400">
                <label class="check"><input type="checkbox" name="lifetime"> مادام‌العمر (بدون مدت)</label>
                <p><button class="btn teal">ساخت پلن</button></p>
              </form>
            </div>
            <?php
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
            $regular = Plans::storePrice($plan, 'bazaar');
            if ($price < 1000 || $price >= $regular) throw new ApiError('price', 'قیمت کمپین باید کمتر از قیمت عادی بازار (' . money($regular) . ') و دست‌کم ۱٬۰۰۰ تومان باشد.');
            $starts = Admin::dtParse(self::post('starts_at')); $ends = Admin::dtParse(self::post('ends_at'));
            if ($ends !== null && $starts !== null && Clock::ts($ends) <= Clock::ts($starts)) throw new ApiError('dates', 'پایان کمپین باید بعد از شروع آن باشد.');
            $color = self::post('label_color');
            $row = [
                'name' => mb_substr(self::post('name'), 0, 80) ?: 'کمپین', 'plan_id' => $plan['id'],
                'store' => 'bazaar',
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
            <?php $hasKey = Settings::secret('bazaar_discount_key') !== ''; ?>
            <div class="alert <?= $hasKey ? 'info' : 'warn' ?>">
              کمپین زمان‌دار یعنی تخفیف خودکار برای یک بازه (مثلاً نوروز) که خودش شروع و تمام می‌شود. این کار فقط در <b>کافه‌بازار</b> با «تخفیف پویا» ممکن است: سرور برای هر خرید توکن امضاشده می‌سازد و بازار همان قیمت کمتر را می‌گیرد.
              <?= $hasKey ? '<br>کلید تخفیف پویا ثبت شده است ✓' : '<br><b>کلید تخفیف پویای بازار ثبت نشده؛</b> تا ثبت نشود کمپین اجرا نمی‌شود. <a href="index.php?p=stores">ثبت کلید</a>' ?>
              <br>برای مایکت (یا بدون کلید): در صفحهٔ <a href="index.php?p=plans">پلن‌ها</a> قیمت را کم کنید و قیمت قبلی را در «قیمت قبل از تخفیف» بنویسید.
            </div>
            <div class="card"><h3><?= $edit ? 'ویرایش کمپین' : 'کمپین جدید' ?></h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= (int)($edit['id'] ?? 0) ?>">
                <div class="row">
                  <div><label>نام</label><input type="text" name="name" value="<?= e($edit['name'] ?? '') ?>" required></div>
                  <div><label>پلن</label><select name="plan_id"><?php foreach ($plans as $p) echo '<option value="' . (int)$p['id'] . '"' . (($edit['plan_id'] ?? 0) == $p['id'] ? ' selected' : '') . '>' . e($p['title']) . ' — ' . money(Plans::storePrice($p, 'bazaar')) . '</option>'; ?></select></div>
                  <div><label>قیمت کمپین (تومان)</label><input type="number" name="price_toman" value="<?= e($edit['price_toman'] ?? '') ?>" required></div>
                </div>
                <div class="row">
                  <div><label>برچسب</label><input type="text" name="label" value="<?= e($edit['label'] ?? '') ?>" placeholder="مثلاً: تخفیف نوروزی"></div>
                  <div><label>رنگ برچسب</label><input type="text" name="label_color" value="<?= e($edit['label_color'] ?? '#C23B48') ?>" class="ltr"></div>
                  <div><label>شروع (به وقت تهران)</label><input type="datetime-local" name="starts_at" value="<?= e(Admin::dtInput($edit['starts_at'] ?? null)) ?>"></div>
                  <div><label>پایان</label><input type="datetime-local" name="ends_at" value="<?= e(Admin::dtInput($edit['ends_at'] ?? null)) ?>"></div>
                </div>
                <label class="check"><input type="checkbox" name="active" <?= ($edit['active'] ?? 1) ? 'checked' : '' ?>> فعال</label>
                <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
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
                <?php $reg = Plans::storePrice($plan, 'bazaar'); ?><td><?= fa_digits((int)round(($reg - (int)$c['price_toman']) * 100 / max(1, $reg))) ?>٪</td>
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
                if ($f['k'] === 'mafia' || str_starts_with($f['k'], 'mafia_')) continue;
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
              <br><b>مافیا:</b> همهٔ بخش‌ها فقط با Pro یا تست یک‌بارهٔ ۱۰ دقیقه‌ای باز می‌شوند. ثبت‌نام حق تست را ثبت می‌کند؛ زمان پس از دریافت تنظیمات سرور و تأیید شروع توسط اپ آغاز می‌شود. پایان تست، بازی باز را هم قفل می‌کند ولی داده‌ها حذف نمی‌شوند. این سیاست با انتخاب موردهای تکی تغییر نمی‌کند.
              <br><b>نوع مسابقهٔ پانتومیم و سبک‌ها:</b> اگر موردی را تیک بزنید، <b>فقط همان موردها</b> Pro می‌شوند و بقیه رایگان می‌مانند. اگر هیچ موردی تیک نخورد و حالت «فقط Pro» باشد، <b>همهٔ موردها</b> Pro می‌شوند.
              <br>تغییرات حداکثر یک دقیقه بعد از باز کردن برنامه در گوشی کاربران اعمال می‌شود.
              <br>داده‌ها پاک نمی‌شوند. در بخش‌های غیرمافیا قانون مانند قبل هنگام ورود یا شروع مورد تازه اعمال می‌شود.
            </div>
            <form method="post"><?= Admin::csrfField() ?>
            <?php foreach (Features::SECTIONS as $sec => $secTitle): if (empty($groups[$sec])) continue; ?>
            <div class="card"><h3><?= e($secTitle) ?></h3>
            <div class="table-wrap"><table><thead><tr><th>قابلیت</th><th>حالت</th><th>محدودیت رایگان</th></tr></thead><tbody>
            <?php foreach ($groups[$sec] as $f): $catalog = Catalog::items($f['k']); $sel = array_filter(explode(',', (string)($f['pro_items'] ?? ''))); ?>
              <?php if ($f['k'] === 'mafia' || str_starts_with($f['k'], 'mafia_')): ?>
              <tr><td><b><?= e($f['title']) ?></b><br><code><?= e($f['k']) ?></code></td><td>فقط Pro</td><td>تست مشترک یک‌باره: ۱۰ دقیقه</td></tr>
              <?php continue; endif; ?>
              <tr><td><b><?= e($f['title']) ?></b><br><small class="muted"><?= e($f['description']) ?></small> <code><?= e($f['k']) ?></code>
                <?php if ($catalog): $extra = array_diff($sel, array_keys($catalog)); ?>
                  <details style="margin-top:6px"<?= $sel ? ' open' : '' ?>><summary>موردهای فقط Pro (<?= fa_digits(count($sel)) ?> از <?= fa_digits(count($catalog)) ?>)</summary>
                    <p style="margin:6px 0"><button type="button" class="btn sm ghost" data-check-all="items[<?= e($f['k']) ?>][]">همه</button> <button type="button" class="btn sm ghost" data-check-none="items[<?= e($f['k']) ?>][]">هیچ‌کدام</button></p>
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
            <div class="row" style="margin-top:12px"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold" data-confirm="تغییر دسترسی‌ها برای همهٔ کاربران اعمال شود؟">ذخیره</button></div></div>
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
                <div class="row" style="margin-top:8px"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
              </form>
              <?php if ($ad): ?><form method="post" style="margin-top:10px" class="row"><?= Admin::csrfField() ?><input type="hidden" name="action" value="delete"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn red" data-confirm="این تبلیغ حذف شود؟">حذف تبلیغ</button></div></form><?php endif; ?>
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
            <div class="row" style="margin-top:12px"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
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
                <?php if (!$m['used']): ?><form method="post" class="row" style="gap:4px;margin-top:6px"><?= Admin::csrfField() ?><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$m['id'] ?>"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"><button class="btn sm red" style="flex:0">حذف</button></form><?php endif; ?>
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
            <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
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
                // Along the route in use now: directly, or through the gateway in Iran.
                $_SESSION['store_result'] = [$store, PaymentBridge::testStore($store)];
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
            Settings::set('myket', ['enabled' => isset($_POST['myket_enabled']), 'package' => $pkg('myket_package'), 'public_key' => $key('myket_public_key'), 'verify' => self::post('myket_verify') === 'partners' ? 'partners' : 'tokens']);
            // Secrets: an empty field keeps the stored value; "-" clears it.
            foreach (['bazaar_api_secret', 'bazaar_client_secret', 'bazaar_refresh_token', 'bazaar_discount_key', 'myket_access_token'] as $s) {
                $v = self::post($s);
                if ($v === '-') Settings::setSecret($s, '');
                elseif ($v !== '') Settings::setSecret($s, $v);
            }
            Settings::set('cross_store', isset($_POST['cross_store']));
            Settings::set('offline_days', max(1, min(90, (int)self::post('offline_days', '21'))));
            Settings::set('renew_grace_days', max(0, min(14, (int)self::post('renew_grace_days', '3'))));
            Settings::set('single_device', isset($_POST['single_device']));
            Settings::set('device_moves_limit', max(0, min(50, (int)self::post('device_moves_limit', '3'))));
            Settings::set('pro_devices_limit', max(0, min(50, (int)self::post('pro_devices_limit', '1'))));
            Settings::set('device_offline_days', max(1, min(90, (int)self::post('device_offline_days', '7'))));
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
              <div class="hint">پنل توسعه‌دهندگان بازار ← برنامه ← پرداخت درون‌برنامه‌ای ← کلید RSA. سرور امضای هر رسید را با آن بررسی می‌کند و همین کلید به برنامه‌های نصب‌شده هم فرستاده می‌شود؛ تغییرش بدون نسخهٔ تازه اعمال می‌شود.</div>
              <?php if (PaymentBridge::route('bazaar') === 'bridge'): ?><div class="alert ok">بازار از راه <a href="index.php?p=bridge">واسط پرداخت ایران</a> بررسی می‌شود؛ کلید API فقط در config.php سرور ایران لازم است.</div><?php endif; ?>
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
              <div class="hint">پنل توسعه‌دهندگان مایکت ← برنامه ← پرداخت درون‌برنامه‌ای. سرور امضای هر رسید را با آن بررسی می‌کند و همین کلید به برنامه‌های نصب‌شده هم فرستاده می‌شود؛ تغییرش بدون نسخهٔ تازه اعمال می‌شود.</div>
              <?php if (PaymentBridge::route('myket') === 'bridge'): ?><div class="alert ok">مایکت از راه <a href="index.php?p=bridge">واسط پرداخت ایران</a> بررسی می‌شود؛ توکن API فقط در config.php سرور ایران لازم است و این فیلد را می‌توانید خالی بگذارید (با «-» پاک کنید).</div><?php endif; ?>
              <label>روش صحت‌سنجی مستقیم مایکت</label><select name="myket_verify"><option value="tokens">مستند: GET …/purchases/products/{sku}/tokens/{token}</option><option value="partners"<?= ($m['verify'] ?? 'tokens') === 'partners' ? ' selected' : '' ?>>قدیمی: POST …/partners/…/verify</option></select>
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
              </div>
              <label class="check"><input type="checkbox" name="single_device" <?= Settings::get('single_device', true) ? 'checked' : '' ?>> حساب هم‌زمان فقط روی تعداد محدودی دستگاه باز باشد</label>
              <div class="row">
                <div><label>تعداد دستگاه‌های هم‌زمان هر حساب (پیش‌فرض ۱؛ ۰ = بی‌سقف)</label><input type="number" name="pro_devices_limit" value="<?= Devices::limit() ?>" min="0" max="50"></div>
                <div><label>سقف انتقال دستی به دستگاه دیگر (در ۳۰ روز)</label><input type="number" name="device_moves_limit" value="<?= (int)Settings::get('device_moves_limit', 3) ?>" min="0" max="50"></div>
                <div><label>اعتبار آفلاین با این قانون (روز)</label><input type="number" name="device_offline_days" value="<?= (int)Settings::get('device_offline_days', 7) ?>" min="1" max="90"></div>
              </div>
              <p class="hint">کاربر با یک حساب روی هر نسخه‌ای (بازار، مایکت، APK سایت، وب، ویندوز و آیفون) وارد می‌شود و Pro همراه حساب است؛ خرید و تمدید در هر نسخه با روش پرداخت همان نسخه انجام می‌شود. با سقف ۱، ورود روی دستگاه تازه، دستگاه قبلی را خودکار از حساب خارج می‌کند: برنامهٔ آن دستگاه در اولین اتصال به اینترنت خارج می‌شود و پیام «حساب شما روی دستگاه دیگری باز شد» را می‌گیرد؛ اگر آفلاین بماند، حداکثر تا پایان «اعتبار آفلاین» Pro دارد. با سقف بیشتر، دستگاهی که مدت بیشتری استفاده نشده خارج می‌شود. اگر تیک بالا برداشته شود، تعداد دستگاه‌ها بی‌سقف است.</p>
              <p class="hint">اگر کاربر تا این مدت اینترنت نداشته باشد هم Pro او کار می‌کند؛ پس از اتصال، گواهی تازه می‌گیرد. اشتراک زمان‌دار هیچ‌وقت بعد از تاریخ پایانش آفلاین تمدید نمی‌شود و مهلت تمدید فقط برای اشتراک‌های «تمدید خودکار» بازار است.</p>
              <p class="hint"><b>مادام‌العمر:</b> بدون تاریخ پایان و بدون نیاز به اینترنت روی همان گوشی کار می‌کند. بازپرداخت یا انتقال آن در اولین اتصال گوشی به اینترنت اعمال می‌شود؛ گوشی‌ای که هرگز آنلاین نشود از ابطال خبردار نمی‌شود.</p>
              <label>متن راهنمای پشتیبانی در اپ</label><input type="text" name="support_text" value="<?= e(Settings::get('support_text', '')) ?>">
            </div>
            <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold" name="action" value="save">ذخیره</button></div></div>
            </form>
            <?php
        }, 'stores');
    }

    // ================================================================ payment methods
    public static function paymethods(): void
    {
        $adapters = PayRegistry::adapters();
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            if (($_POST['action'] ?? '') === 'sitebuy') {
                $old = (bool)Settings::get('site_codes_in_stores', false);
                Settings::set('site_codes_in_stores', isset($_POST['site_codes_in_stores']));
                Admin::audit('site_codes_setting', 'settings', 'site_codes_in_stores', $reason, ['on' => $old], ['on' => isset($_POST['site_codes_in_stores'])]);
                Admin::flash('تنظیم خرید از سایت ذخیره شد.');
                Admin::redirect('p=paymethods#site-buy');
            }
            if (($_POST['action'] ?? '') === 'pages') {
                $old = PayPages::cfg();
                $new = ['before_title' => mb_substr(trim(self::post('before_title')), 0, 80) ?: PayPages::defaults()['before_title'],
                    'before_notes' => mb_substr(trim(self::post('before_notes')), 0, 2000), 'card_note' => mb_substr(trim(self::post('card_note')), 0, 500),
                    'after_note' => mb_substr(trim(self::post('after_note')), 0, 500), 'show_ads' => isset($_POST['show_ads'])];
                Settings::set('pay_pages', $new);
                Admin::audit('pay_pages_save', 'settings', 'pay_pages', $reason, $old, $new);
                Admin::flash('صفحه‌های پیش و پس از پرداخت ذخیره شد.');
                Admin::redirect('p=paymethods#pay-pages');
            }
            $id = (string)($_POST['id'] ?? '');
            $a = $adapters[$id] ?? null;
            if ($a === null) throw new ApiError('method', 'روش پرداخت پیدا نشد.');
            $action = (string)($_POST['action'] ?? 'save');
            $cfg = PayRegistry::cfg($id);
            if ($action === 'test') {
                $_SESSION['pay_result'] = [$id, $a->test()];
                PayRegistry::note($id, $_SESSION['pay_result'][1][0] === 'ok', $_SESSION['pay_result'][1][1]);
                Admin::redirect('p=paymethods#m-' . $id);
            }
            if ($action === 'kill' || $action === 'unkill') {
                Settings::set('pay_' . $id, ['kill' => $action === 'kill'] + $cfg);
                Admin::audit('pay_' . $action, 'pay_method', $id, $reason);
                Admin::flash($action === 'kill' ? 'روش پرداخت فوراً از برنامه برداشته شد.' : 'قطع اضطراری برداشته شد.');
                Admin::redirect('p=paymethods#m-' . $id);
            }
            $num = fn($k) => self::post($k) === '' ? null : max(0, (int)self::post($k));
            $new = ['enabled' => isset($_POST['enabled']), 'sort' => (int)self::post('sort', '0'), 'min' => $num('min'), 'max' => $num('max'),
                'sandbox' => isset($_POST['sandbox']), 'route' => self::post('route') === 'direct' ? 'direct' : 'bridge'] + $cfg;
            if ($a instanceof PayEndpointAdapter) {
                // API address and payment-page domains: empty = the documented defaults; anything else must be a clean HTTPS domain.
                $base = rtrim(trim(self::post('api_base')), '/');
                $hosts = trim(self::post('hosts'));
                if ($base !== '' && !PayEndpoint::validBase($base)) { Admin::flash('نشانی API معتبر نیست: فقط https:// و نام دامنه (بدون IP، درگاه، ? و ..). چیزی ذخیره نشد.', 'bad'); Admin::redirect('p=paymethods#m-' . $id); }
                $list = PayEndpoint::parseHosts($hosts);
                if ($list === null) { Admin::flash('دامنهٔ صفحهٔ پرداخت معتبر نیست: فقط نام دامنه، جدا با ویرگول. چیزی ذخیره نشد.', 'bad'); Admin::redirect('p=paymethods#m-' . $id); }
                $new['api_base'] = $base === $a->defaultBase() ? '' : $base;
                $new['hosts'] = $list === $a->defaultHosts() ? '' : implode(', ', $list);
                // Path of each call (empty = documented) and extra fields of a new invoice.
                $paths = [];
                foreach ($a->defaultPaths() as $op => $def) {
                    $v = trim(self::post('path_' . $op));
                    if ($v === '' || $v === $def) continue;
                    if (!PayEndpoint::validPath($v, strpos($def, '{id}') !== false)) { Admin::flash('مسیر «' . $op . '» معتبر نیست: با / شروع شود، فقط حروف لاتین، رقم، - _ . و ' . (strpos($def, '{id}') !== false ? 'دقیقاً یک {id}' : 'بدون {id}') . '. چیزی ذخیره نشد.', 'bad'); Admin::redirect('p=paymethods#m-' . $id); }
                    $paths[$op] = $v;
                }
                $extra = trim(self::post('extra'));
                if (PayEndpoint::parseExtra($extra, $a->coreFields()) === null) { Admin::flash('فیلدهای اضافه معتبر نیست: هر خط «نام=مقدار»، نام با حرف کوچک لاتین، حداکثر ۱۰ خط، بدون فیلدهای اصلی (' . implode('، ', $a->coreFields()) . '). چیزی ذخیره نشد.', 'bad'); Admin::redirect('p=paymethods#m-' . $id); }
                $new['paths'] = $paths;
                $new['extra'] = $extra;
            }
            Settings::set('pay_' . $id, $new);
            foreach ($a->fields() as $k => [$label, $secret]) {
                $v = self::post('f_' . $k);
                if ($v === '-') Settings::setSecret('pay_' . $id . '_' . $k, '');
                elseif ($v !== '') Settings::setSecret('pay_' . $id . '_' . $k, $v);
            }
            Admin::audit('pay_method_save', 'pay_method', $id, $reason, array_intersect_key($cfg, $new), $new);
            $st = PayRegistry::status($id);
            Admin::flash($new['enabled'] && $st !== 'ready' ? 'ذخیره شد، ولی این روش هنوز آماده نیست و در برنامه نمایش داده نمی‌شود: ' . strip_tags(self::payBadge($st)) : 'ذخیره شد.', $new['enabled'] && $st !== 'ready' ? 'warn' : 'ok');
            Admin::redirect('p=paymethods#m-' . $id);
        }
        $result = $_SESSION['pay_result'] ?? null; unset($_SESSION['pay_result']);
        $orders = Db::all('SELECT o.*, u.username, p.title plan_title FROM {pay_orders} o LEFT JOIN {users} u ON u.id = o.user_id JOIN {plans} p ON p.id = o.plan_id ORDER BY o.id DESC LIMIT 40');
        Admin::layout('روش‌های پرداخت', function () use ($adapters, $result, $orders) {
            $pp = PayPages::cfg();
            ?>
            <div class="card" style="padding:12px 16px"><input type="search" id="pm-search" placeholder="جست‌وجو در درگاه‌ها و تنظیمات API (مثلاً webhook، verify، بلوپال، مسیر، دامنه)…" autocomplete="off"><div class="hint" id="pm-search-n"></div></div>
            <div class="alert info">
              هر نسخهٔ برنامه فقط روش‌هایی را می‌بیند که <b>هم</b> مجاز باشند <b>هم</b> آماده. طبق قوانین کافه‌بازار و مایکت، فروش اشتراک و قابلیت دیجیتال در نسخه‌های این دو فروشگاه فقط با پرداخت درون‌برنامه‌ای همان فروشگاه است؛
              درگاه‌های وب (زرین‌پال، بلوپال، آبان گیت‌وی و …) فقط در <b>نسخهٔ سایت</b> (APK مستقیم که در هیچ فروشگاهی منتشر نمی‌شود) نمایش داده می‌شوند و این قانون از پنل قابل تغییر نیست.
              <br>روشن کردن یک روش بدون تنظیمات درست، آن را در برنامه نشان نمی‌دهد؛ وضعیتش «نیازمند تنظیمات» می‌ماند. همهٔ تغییرها بدون APK تازه در برنامه اعمال می‌شوند.
            </div>
            <?php foreach ($adapters as $id => $a): $c = PayRegistry::cfg($id); $st = (array)Settings::get('pay_status_' . $id, []); ?>
            <div class="card pm-card" id="m-<?= e($id) ?>">
              <h3><?= e($a->title()) ?> <?= self::payBadge(PayRegistry::status($id)) ?> <?= $c['kill'] ? '<span class="badge bad">قطع اضطراری</span>' : '' ?></h3>
              <p class="muted"><?= e($a->description()) ?> — نوع: <?= $a->kind() === 'store' ? 'پرداخت فروشگاه' : 'درگاه وب' ?> — نسخه‌های مجاز: <b><?= e(implode('، ', array_map(fn($ch) => Notices::STORES[$ch] ?? $ch, $a->channels()))) ?></b> — بازپرداخت از API: <?= $a->supportsRefund() ? 'دارد' : 'ندارد (دستی از پنل درگاه)' ?></p>
              <?php if ($result && $result[0] === $id) echo '<div class="alert ' . e($result[1][0]) . '">' . e($result[1][1]) . '</div>'; ?>
              <p class="hint">آخرین موفقیت: <?= !empty($st['last_ok_at']) ? e(jdate(Clock::sql((int)$st['last_ok_at']))) : '—' ?> — آخرین خطا: <?= !empty($st['last_error_at']) ? e(jdate(Clock::sql((int)$st['last_error_at']))) . ' <small>' . e($st['last_error'] ?? '') . '</small>' : '—' ?></p>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= e($id) ?>">
                <div class="row">
                  <label class="check" style="flex:0;white-space:nowrap"><input type="checkbox" name="enabled" <?= $c['enabled'] ? 'checked' : '' ?>> فعال</label>
                  <div><label>اولویت نمایش</label><input type="number" name="sort" value="<?= (int)$c['sort'] ?>"></div>
                  <div><label>حداقل مبلغ (تومان)</label><input type="number" name="min" value="<?= e($c['min']) ?>" placeholder="بدون حد"></div>
                  <div><label>حداکثر مبلغ (تومان)</label><input type="number" name="max" value="<?= e($c['max']) ?>" placeholder="بدون حد"></div>
                </div>
                <?php foreach ($a->fields() as $k => [$label, $secret]): $has = Settings::secret('pay_' . $id . '_' . $k) !== ''; ?>
                  <label><?= e($label) ?> <?= $has ? '<span class="badge ok">ثبت شده</span>' : '<span class="badge warn">ثبت نشده</span>' ?></label>
                  <input type="password" name="f_<?= e($k) ?>" class="ltr" autocomplete="new-password" placeholder="خالی = بدون تغییر، - = پاک کردن">
                <?php endforeach; ?>
                <?php if ($a->kind() === 'web'): ?>
                  <div class="row">
                    <label class="check" style="flex:0;white-space:nowrap"><input type="checkbox" name="sandbox" <?= $c['sandbox'] ? 'checked' : '' ?>> حالت آزمایشی (Sandbox)</label>
                    <?php if ($id === 'zarinpal' || $id === 'aban'): ?>
                    <div><label>مسیر ارتباط</label><select name="route"><option value="bridge">از راه واسط ایران (پیشنهادی)</option><option value="direct"<?= $c['route'] === 'direct' ? ' selected' : '' ?>>مستقیم از هلند</option></select></div>
                    <?php endif; ?>
                  </div>
                  <?php if ($a instanceof PayEndpointAdapter): ?>
                    <div class="row">
                      <div><label>نشانی API <small class="muted">(خالی = پیش‌فرض)</small></label><input type="text" name="api_base" class="ltr" value="<?= e($c['api_base']) ?>" placeholder="<?= e($a->defaultBase()) ?>"></div>
                      <div><label>دامنهٔ صفحهٔ پرداخت <small class="muted">(خالی = پیش‌فرض؛ چند دامنه با ویرگول)</small></label><input type="text" name="hosts" class="ltr" value="<?= e($c['hosts']) ?>" placeholder="<?= e(implode(', ', $a->defaultHosts())) ?>"></div>
                    </div>
                    <p class="hint">اگر <?= e($a->title()) ?> دامنه یا نسخهٔ API را عوض کرد، نشانی تازه را اینجا بنویسید و «آزمایش اتصال» بزنید؛ APK یا نصب تازه لازم نیست. نشانی فعلی: <b class="ltr"><?= e(PayEndpoint::base($a)) ?></b> — صفحهٔ پرداخت: <b class="ltr"><?= e(implode('، ', PayEndpoint::hosts($a))) ?></b></p>
                    <?php $hook = App::baseUrl() . '/pay/webhook.php?m=' . $id; $cb = App::baseUrl() . '/pay/return.php?m=' . $id; ?>
                    <div class="alert info" style="margin-top:10px">
                      <b><?= $id === 'blupal' ? 'این دو نشانی را در پنل بلوپال (مدیریت درگاه ← ساخت API درگاه جدید) وارد کنید:' : 'این دو نشانی را دیوان با هر فاکتور خودکار به آبان می‌فرستد؛ برای اطلاع:' ?></b>
                      <div class="row" style="margin-top:8px;align-items:flex-end">
                        <div><label>Webhook URL</label><input type="text" class="ltr" readonly value="<?= e($hook) ?>"></div>
                        <div style="flex:0"><button type="button" class="btn sm ghost" data-copy="<?= e($hook) ?>">کپی</button></div>
                      </div>
                      <div class="row" style="align-items:flex-end">
                        <div><label><?= $id === 'blupal' ? 'Callback page (صفحهٔ بازگشت)' : 'return_url (صفحهٔ بازگشت)' ?></label><input type="text" class="ltr" readonly value="<?= e($id === 'blupal' ? $cb : App::baseUrl() . '/pay/return.php?o=…') ?>"></div>
                        <?php if ($id === 'blupal'): ?><div style="flex:0"><button type="button" class="btn sm ghost" data-copy="<?= e($cb) ?>">کپی</button></div><?php endif; ?>
                      </div>
                      <?php if ($id === 'blupal'): ?><div class="hint">«نام نمایشی در درگاه پرداخت» را «دیوان امتیاز» بگذارید. صفحهٔ بازگشت خودش سفارش خریدار را پیدا می‌کند و نتیجهٔ واقعی را از بلوپال می‌پرسد.</div><?php endif; ?>
                    </div>
                    <details style="margin-top:10px">
                      <summary><b>تنظیمات پیشرفتهٔ API و مرجع فراخوانی‌ها</b> <small class="muted">— برای وقتی که درگاه مسیر یا فیلد تازه‌ای اضافه کرد</small></summary>
                      <div class="table-wrap" style="margin-top:8px"><table><thead><tr><th>کار</th><th>روش</th><th>مسیر (خالی = پیش‌فرض)</th><th>نشانی کامل فعلی</th></tr></thead><tbody>
                      <?php $opNames = ['create' => 'ساخت فاکتور', 'get' => 'استعلام فاکتور', 'verify' => 'تأیید یک‌باره', 'cancel' => 'لغو'];
                      foreach ($a->defaultPaths() as $op => $def): $cur = (string)(((array)$c['paths'])[$op] ?? ''); ?>
                        <tr><td><?= e($opNames[$op] ?? $op) ?> <small class="muted ltr"><?= e($op) ?></small></td><td class="ltr"><?= $op === 'get' ? 'GET' : 'POST' ?></td>
                          <td><input type="text" name="path_<?= e($op) ?>" class="ltr" value="<?= e($cur) ?>" placeholder="<?= e($def) ?>"></td>
                          <td class="ltr"><small><?= e(str_replace('%7Bid%7D', '{id}', PayEndpoint::base($a) . PayEndpoint::path($a, $op, '{id}'))) ?></small></td></tr>
                      <?php endforeach; ?>
                      </tbody></table></div>
                      <label style="margin-top:8px">فیلدهای اضافهٔ فاکتور تازه <small class="muted">(هر خط: نام=مقدار — فیلدهای اصلی <span class="ltr"><?= e(implode(', ', $a->coreFields())) ?></span> را خود دیوان می‌گذارد)</small></label>
                      <textarea name="extra" class="ltr" rows="3" placeholder="shop_name=Divan Emtiaz"><?= e((string)$c['extra']) ?></textarea>
                      <p class="hint">مسیرها فقط بخش‌های ساده می‌پذیرند (بدون ?، .. یا دامنهٔ دیگر)؛ «{id}» جای شمارهٔ فاکتور است. بعد از تغییر، «آزمایش اتصال» بزنید.</p>
                    </details>
                  <?php endif; ?>
                  <?php if ($id === 'blupal'): ?>
                    <p class="hint">بلوپال از هلند مستقیم در دسترس است. کلید <span class="ltr">blu_test_…</span> فقط با «حالت آزمایشی» و کلید <span class="ltr">blu_live_…</span> فقط بدون آن کار می‌کند. بدون Webhook هم پرداخت‌ها با استعلام برنامه، صفحهٔ بازگشت و Cron تأیید می‌شوند.</p>
                  <?php endif; ?>
                  <?php if ($id === 'aban'): ?>
                    <p class="hint">سرورهای آبان در ایران‌اند؛ مسیر «واسط ایران» را نگه دارید (نسخهٔ ۱٫۱ واسط لازم است). توکن <span class="ltr">ag_test_…</span> فقط با «حالت آزمایشی» و <span class="ltr">ag_live_…</span> فقط بدون آن کار می‌کند.
                    آبان وبهوک را به نشانی <b class="ltr"><?= e(App::baseUrl() . '/pay/webhook.php?m=aban') ?></b> می‌فرستد (خودکار، با هر فاکتور). کلید مخفی وبهوک را از پنل آبان وارد کنید تا وبهوک‌های بدون امضا رد شوند — بدون آن هم پرداخت فقط با استعلام از خود آبان تأیید می‌شود.</p>
                  <?php endif; ?>
                  <?php if ($c['sandbox']): ?><div class="alert warn">حالت آزمایشی روشن است: در این حالت پرداخت بدون پول واقعی شبیه‌سازی می‌شود و هر کاربری می‌تواند Pro آزمایشی بگیرد. فقط برای تست؛ پیش از انتشار خاموش کنید.</div><?php endif; ?>
                <?php endif; ?>
                <div class="row" style="margin-top:8px;gap:6px;flex-wrap:wrap">
                  <div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div>
                  <button class="btn gold" name="action" value="save">ذخیره</button>
                  <button class="btn ghost" name="action" value="test">آزمایش اتصال</button>
                  <?= $c['kill'] ? '<button class="btn teal" name="action" value="unkill">برداشتن قطع اضطراری</button>' : '<button class="btn red" name="action" value="kill" data-confirm="این روش فوراً از برنامه برداشته شود؟">قطع اضطراری</button>' ?>
                </div>
              </form>
            </div>
            <?php endforeach; ?>
            <div class="card pm-card" id="site-buy"><h3>خرید از سایت (کد فعال‌سازی)</h3>
              <p class="muted">هر کس بدون برنامه هم می‌تواند از صفحهٔ خرید سایت اشتراک بخرد: یا برای یک حساب موجود (با نام کاربری؛ اشتراک بلافاصله پس از تأیید پرداخت به همان حساب اضافه می‌شود)، یا برای یک
                <b>کد فعال‌سازی</b> ۳۲ نویسه‌ای که پس از پرداخت فقط در همان مرورگر خریدار نشان داده می‌شود و در برنامه از «دیوان Pro ← کد تخفیف یا هدیه» یک بار روی هر حسابی فعال می‌شود.
                این خریدها به هیچ گوشی‌ای بسته نیستند؛ اولین گوشی‌ای که با آن حساب وارد شود، مثل هر خرید دیگری ثبت می‌شود.</p>
              <p><a class="btn sm ghost" href="../pay/buy.php" target="_blank" rel="noopener">صفحهٔ خرید سایت</a> <span class="hint">نشانی برای لینک در سایت: <b class="ltr"><?= e(App::baseUrl() . '/pay/buy.php') ?></b></span></p>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="sitebuy">
                <label class="check"><input type="checkbox" name="site_codes_in_stores" <?= Settings::get('site_codes_in_stores', false) ? 'checked' : '' ?>> کد فعال‌سازی در نسخه‌های کافه‌بازار و مایکت هم پذیرفته شود</label>
                <p class="hint">پیش‌فرض خاموش است: قوانین کافه‌بازار و مایکت فروش اشتراک بیرون از پرداخت خودشان را در نسخه‌های فروشگاهی نمی‌پذیرند. با خاموش بودن، کد در نسخهٔ سایت، وب، ویندوز و آیفون فعال می‌شود و چون اشتراک روی حساب است، همان حساب در همهٔ نسخه‌ها Pro است (طبق تنظیم «بین فروشگاهی»).</p>
                <div class="row" style="margin-top:8px"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><button class="btn gold">ذخیره</button></div>
              </form>
            </div>
            <div class="card pm-card" id="pay-pages"><h3>صفحه‌های پیش و پس از پرداخت</h3>
              <p class="muted">خریدار نسخهٔ سایت (و وب، ویندوز و آیفون) پیش از درگاه صفحهٔ دیوان را می‌بیند: پلن، مبلغ دقیق، این نکته‌ها و یک پیشنهاد؛ بعد از پرداخت هم به صفحهٔ نتیجهٔ دیوان برمی‌گردد که نتیجهٔ واقعی را از درگاه می‌پرسد و خودکار به‌روز می‌شود.
                پیشنهادها از «تبلیغات» با جایگاه‌های «صفحهٔ پیش از پرداخت» و «صفحهٔ پس از پرداخت» می‌آیند.</p>
              <p><a class="btn sm ghost" href="../pay/start.php?demo=1" target="_blank" rel="noopener">نمونهٔ صفحهٔ پیش از پرداخت</a>
                <a class="btn sm ghost" href="../pay/return.php?demo=paid" target="_blank" rel="noopener">نمونهٔ پرداخت موفق</a>
                <a class="btn sm ghost" href="../pay/return.php?demo=pending" target="_blank" rel="noopener">نمونهٔ در حال بررسی</a>
                <a class="btn sm ghost" href="../pay/return.php?demo=failed" target="_blank" rel="noopener">نمونهٔ ناموفق</a></p>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="pages">
                <label>عنوان صفحهٔ پیش از پرداخت</label><input type="text" name="before_title" maxlength="80" value="<?= e($pp['before_title']) ?>">
                <label>نکته‌های پیش از پرداخت <small class="muted">(هر خط یک نکته)</small></label><textarea name="before_notes" rows="4"><?= e($pp['before_notes']) ?></textarea>
                <label>نکتهٔ کارت به کارت <small class="muted">(فقط برای بلوپال و آبان)</small></label><textarea name="card_note" rows="2"><?= e($pp['card_note']) ?></textarea>
                <label>پیام پس از پرداخت موفق</label><textarea name="after_note" rows="2"><?= e($pp['after_note']) ?></textarea>
                <label class="check"><input type="checkbox" name="show_ads" <?= $pp['show_ads'] ? 'checked' : '' ?>> نمایش پیشنهاد (تبلیغ) در این دو صفحه</label>
                <div class="row" style="margin-top:8px"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><button class="btn gold">ذخیره</button></div>
              </form>
            </div>
            <div class="card pm-card"><h3>آخرین سفارش‌های پرداخت وب</h3>
              <div class="table-wrap"><table><thead><tr><th>سفارش</th><th>حساب / خریدار</th><th>پلن</th><th>روش</th><th>مبلغ</th><th>وضعیت</th><th>کد پیگیری</th><th>زمان</th></tr></thead><tbody>
              <?php foreach ($orders as $o): ?>
                <tr><td class="ltr"><?= e($o['code']) ?></td><td><?php
                  $buyer = $o['buyer'] ?? 'app';
                  if ($buyer === 'code') {
                      $act = Crypto::decrypt($o['activation_enc'] ?? null);
                      echo 'کد فعال‌سازی ' . ($o['redeemed_by'] !== null ? '— فعال شده برای <span class="ltr">' . e($o['username'] ?? '') . '</span>' : '— استفاده‌نشده');
                      if ($act !== null && $o['status'] === 'paid' && $o['redeemed_by'] === null) echo '<details><summary><small>نمایش کد (برای پشتیبانی)</small></summary><b class="ltr">' . e($act) . '</b></details>';
                  } else {
                      echo '<span class="ltr">' . e($o['username'] ?? '—') . '</span>' . ($buyer === 'site' ? ' <small class="muted">(خرید از سایت)</small>' : '');
                  }
                ?></td><td><?= e($o['plan_title']) ?></td><td><?= e($o['method']) ?></td><td><?= money((int)$o['amount_toman']) ?></td>
                  <td><?= Admin::statusBadge(['paid' => 'verified', 'pending' => 'pending', 'failed' => 'rejected', 'expired' => 'inactive', 'created' => 'pending'][$o['status']] ?? $o['status']) ?><?= $o['last_error'] && $o['status'] !== 'paid' ? '<br><small class="muted">' . e($o['last_error']) . '</small>' : '' ?></td>
                  <td class="ltr"><?= e($o['ref_id'] ?? '—') ?></td><td><?= e(jdate($o['created_at'])) ?></td></tr>
              <?php endforeach; if (!$orders) echo '<tr><td colspan="8" class="muted">سفارشی نیست.</td></tr>'; ?>
              </tbody></table></div>
            </div>
            <?php
        }, 'paymethods');
    }

    private static function payBadge(string $st): string
    {
        $m = ['ready' => ['ok', 'آماده و در برنامه نمایش داده می‌شود'], 'disabled' => ['', 'خاموش'], 'killed' => ['bad', 'قطع اضطراری'], 'needs_config' => ['warn', 'نیازمند تنظیمات'], 'not_allowed' => ['bad', 'غیرمجاز']];
        [$k, $l] = $m[$st] ?? ['', $st];
        return '<span class="badge ' . $k . '">' . e($l) . '</span>';
    }

    // ================================================================ promotional notifications
    /** «مدیریت سایت»: the introduction site's notice bar, discount banner, ads, sections, texts, details and downloads. */
    public static function site(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $part = (string)($_POST['action'] ?? '');
            SiteSettings::save($part, $_POST);
            Admin::audit('site_' . $part, 'settings', null, $reason);
            Admin::flash('ذخیره شد. سایت حداکثر تا یک دقیقهٔ دیگر تغییر را نشان می‌دهد.');
            Admin::redirect('p=site#' . $part);
        }
        $c = SiteSettings::get();
        $codes = Db::all("SELECT code, percent, plan_code, ends_at, active, max_uses, uses, users FROM {promo_codes} WHERE kind = 'discount' ORDER BY id DESC LIMIT 200");
        $slots = [];
        foreach (SiteSettings::AD_SLOTS as $k) {
            $pl = Db::one('SELECT * FROM {placements} WHERE k = ?', [$k]);
            $n = (int)Db::value("SELECT COUNT(*) FROM {ads} WHERE active = 1 AND FIND_IN_SET(?, placements)", [$k]);
            $slots[] = [$k, $pl['title'] ?? $k, (int)($pl['active'] ?? 0), $n];
        }
        $home = PayPages::siteHome();
        $note = fn() => '<div class="row" style="margin-top:10px"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>';
        $form = fn(string $part) => '<form method="post" action="index.php?p=site">' . Admin::csrfField() . '<input type="hidden" name="action" value="' . $part . '">';
        Admin::layout('مدیریت سایت', function () use ($c, $codes, $slots, $home, $note, $form) {
            ?>
            <div class="alert info">
              هر چه اینجا ذخیره کنید، بدون آپلود دوبارهٔ فایل‌ها روی <a href="<?= e($home) ?>" target="_blank" rel="noopener">سایت</a> اعمال می‌شود (حداکثر پس از یک دقیقه).
              خانه‌های خالی همان متن و مقدار فایل‌های خود سایت را نگه می‌دارند. کد رسمی اینماد عوض نمی‌شود.
              <br><input type="search" id="pm-search" placeholder="جست‌وجو در تنظیمات سایت…" style="margin-top:8px"> <span id="pm-search-n" class="hint"></span>
            </div>
            <div class="grid g2 site-cards">

            <div class="card pm-card" id="announce"><h3>نوار اعلان بالای سایت</h3>
              <?= $form('announce') ?>
                <label class="check"><input type="checkbox" name="on" <?= $c['announce']['on'] ? 'checked' : '' ?>> نمایش داده شود</label>
                <label>متن اعلان</label><textarea name="text" maxlength="300" placeholder="مثلاً: نسخهٔ ویندوز دیوان امتیاز منتشر شد!"><?= e($c['announce']['text']) ?></textarea>
                <div class="row">
                  <div><label>پیوند (اختیاری)</label><input type="text" name="link" class="ltr" value="<?= e($c['announce']['link']) ?>" placeholder="/download.html یا https://…"></div>
                  <div><label>متن دکمهٔ پیوند</label><input type="text" name="link_text" maxlength="40" value="<?= e($c['announce']['link_text']) ?>" placeholder="مشاهده"></div>
                </div>
                <div class="row">
                  <div><label>رنگ</label><select name="tone"><?php foreach (SiteSettings::TONES as $k => $l) echo '<option value="' . e($k) . '"' . ($c['announce']['tone'] === $k ? ' selected' : '') . '>' . e($l) . '</option>'; ?></select></div>
                  <div><label class="check" style="margin-top:30px"><input type="checkbox" name="home_only" <?= $c['announce']['home_only'] ? 'checked' : '' ?>> فقط در صفحهٔ اصلی</label></div>
                </div>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="promo"><h3>بنر کد تخفیف</h3>
              <p class="hint">یک کد «تخفیف خرید» را روی سایت نشان می‌دهد؛ دکمهٔ آن صفحهٔ خرید از سایت را با همین کد پرشده باز می‌کند. با پایان مهلت یا ظرفیت کد، بنر خودکار پنهان می‌شود. کدها را در <a href="index.php?p=promos">کد تخفیف و کد هدیه</a> بسازید.</p>
              <?= $form('promo') ?>
                <label class="check"><input type="checkbox" name="on" <?= $c['promo']['on'] ? 'checked' : '' ?>> نمایش داده شود</label>
                <label>کد تخفیف</label><select name="code"><option value="">— انتخاب کنید —</option><?php foreach ($codes as $p) {
                    $tag = (int)$p['percent'] . '٪' . ($p['plan_code'] ? ' — ' . $p['plan_code'] : '') . (!$p['active'] ? ' (غیرفعال)' : '') . ($p['ends_at'] ? ' — تا ' . jdate($p['ends_at'], false) : '') . (trim((string)$p['users']) !== '' ? ' (ویژهٔ حساب‌های خاص؛ روی سایت نمایش داده نمی‌شود)' : '');
                    echo '<option value="' . e($p['code']) . '"' . ($c['promo']['code'] === $p['code'] ? ' selected' : '') . '>' . e($p['code'] . ' — ' . $tag) . '</option>'; } ?></select>
                <label>عنوان بنر (اختیاری)</label><input type="text" name="title" maxlength="80" value="<?= e($c['promo']['title']) ?>" placeholder="جشنوارهٔ پاییزهٔ دیوان">
                <label>توضیح (اختیاری)</label><textarea name="text" maxlength="300" placeholder="با این کد، اشتراک Pro را ارزان‌تر بخرید."><?= e($c['promo']['text']) ?></textarea>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="ads"><h3>تبلیغات سایت</h3>
              <p class="hint">تبلیغ‌ها همان تبلیغ‌های بخش <a href="index.php?p=ads">تبلیغات</a> هستند: برای هر تبلیغ، جایگاه‌های «سایت — …» را تیک بزنید. فقط تصویر، متن و پیوند https نمایش داده می‌شود؛ هر جایگاه را در <a href="index.php?p=placements">جایگاه‌های تبلیغ</a> روشن یا خاموش کنید.</p>
              <?= $form('ads') ?>
                <label class="check"><input type="checkbox" name="on" <?= $c['ads']['on'] ? 'checked' : '' ?>> تبلیغات روی سایت نمایش داده شود</label>
                <div class="table-wrap"><table><thead><tr><th>جایگاه</th><th>وضعیت</th><th>تبلیغ فعال</th></tr></thead><tbody>
                <?php foreach ($slots as [$k, $t, $on, $n]) echo '<tr><td>' . e($t) . '<br><code>' . e($k) . '</code></td><td>' . Admin::statusBadge($on ? 'active' : 'disabled') . '</td><td>' . fa_digits((string)$n) . '</td></tr>'; ?>
                </tbody></table></div>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="sections"><h3>بخش‌های صفحهٔ اصلی</h3>
              <p class="hint">ترتیب (عدد کوچک‌تر بالاتر) و نمایش هر بخش. بخش معرفی بالای صفحه همیشه اول است.</p>
              <?= $form('sections') ?>
                <div class="table-wrap"><table><thead><tr><th>بخش</th><th>ترتیب</th><th>نمایش</th></tr></thead><tbody>
                <?php foreach (SiteSettings::SECTIONS as $k => $t) echo '<tr><td>' . e($t) . '</td><td><input type="number" min="1" max="99" name="order[' . e($k) . ']" value="' . (int)$c['sections'][$k]['order'] . '" style="width:80px"></td><td><label class="check"><input type="checkbox" name="show[' . e($k) . ']" ' . ($c['sections'][$k]['show'] ? 'checked' : '') . '> نمایش</label></td></tr>'; ?>
                </tbody></table></div>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="texts"><h3>متن‌های صفحهٔ اصلی</h3>
              <p class="hint">خالی بماند = متن فعلی سایت.</p>
              <?= $form('texts') ?>
                <?php foreach (SiteSettings::TEXTS as $k => [$label, $max]) {
                    echo '<label>' . e($label) . '</label>';
                    echo $max > 120 ? '<textarea name="' . e($k) . '" maxlength="' . $max . '">' . e($c['texts'][$k]) . '</textarea>' : '<input type="text" name="' . e($k) . '" maxlength="' . $max . '" value="' . e($c['texts'][$k]) . '">';
                } ?>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="business"><h3>اطلاعات کسب‌وکار و تماس</h3>
              <p class="hint">فقط اطلاعات واقعی و ثبت‌شده، دقیقاً مطابق پروندهٔ اینماد. خالی بماند = مقدار فایل تنظیمات سایت.</p>
              <?= $form('business') ?>
                <div class="row" style="flex-wrap:wrap">
                <?php foreach (SiteSettings::BUSINESS as $k => [$label, $max]) echo '<div style="min-width:45%"><label>' . e($label) . '</label><input type="text" name="business[' . e($k) . ']" maxlength="' . $max . '" value="' . e($c['business'][$k]) . '"></div>'; ?>
                <?php foreach (SiteSettings::CONTACT as $k => [$label, $max]) echo '<div style="min-width:45%"><label>' . e($label) . '</label><input type="text" name="contact[' . e($k) . ']" maxlength="' . $max . '" value="' . e($c['contact'][$k]) . '"' . (in_array($k, ['email', 'phone', 'mobile', 'postalCode', 'telegram', 'eitaa', 'bale'], true) ? ' class="ltr"' : '') . '></div>'; ?>
                </div>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="enamad"><h3>نماد اعتماد الکترونیکی (اینماد)</h3>
              <p class="hint">کد کامل نماد را همان‌طور که در پنل enamad.ir داده شده اینجا بچسبانید؛ شناسه و Code از آن برداشته می‌شود و نماد در همهٔ صفحه‌های سایت با همان ساختار رسمی نمایش داده می‌شود. خالی = کد فعلی فایل‌های سایت.</p>
              <?= $form('enamad') ?>
                <textarea name="enamad" class="ltr" style="min-height:90px" placeholder="&lt;a referrerpolicy='origin' target='_blank' href='https://trustseal.enamad.ir/?id=…&amp;Code=…'&gt;…&lt;/a&gt;"><?= $c['enamad']['id'] !== '' ? e("https://trustseal.enamad.ir/?id={$c['enamad']['id']}&Code={$c['enamad']['code']}") : '' ?></textarea>
                <?php if ($c['enamad']['id'] !== ''): ?><p class="hint">نماد فعلی: شناسهٔ <b class="ltr"><?= e($c['enamad']['id']) ?></b></p><?php endif; ?>
                <?= $note() ?>
              </form>
            </div>

            <div class="card pm-card" id="downloads"><h3>لینک‌های دانلود</h3>
              <p class="hint">پس از هر انتشار تأییدشده، نشانی فایل، حجم و SHA-256 را اینجا به‌روز کنید. خالی بماند = مقدار فایل تنظیمات سایت. نسخهٔ وب و PWA همیشه <code>/play/</code> است.</p>
              <?= $form('downloads') ?>
                <div class="row">
                  <div><label>شمارهٔ نسخه</label><input type="text" name="version" class="ltr" maxlength="20" value="<?= e($c['downloads']['version']) ?>" placeholder="5.5.0"></div>
                  <div><label>صفحهٔ انتشار گیت‌هاب</label><input type="text" name="github" class="ltr" value="<?= e($c['downloads']['github']) ?>" placeholder="https://github.com/…/releases/latest"></div>
                </div>
                <?php foreach (SiteSettings::DOWNLOADS as [$k, $t, $size, $sha]) { $d = $c['downloads'][$k]; ?>
                  <fieldset style="margin-top:10px"><legend><?= e($t) ?></legend>
                    <label>نشانی</label><input type="text" name="<?= e($k) ?>[url]" class="ltr" value="<?= e($d['url']) ?>" placeholder="<?= in_array($k, ['bazaar', 'myket'], true) ? 'https://…' : '/downloads/…' ?>">
                    <?php if ($size): ?><label>حجم</label><input type="text" name="<?= e($k) ?>[size]" maxlength="30" value="<?= e($d['size']) ?>" placeholder="مثلاً ۱۲ مگابایت"><?php endif; ?>
                    <?php if ($sha): ?><label>SHA-256</label><input type="text" name="<?= e($k) ?>[sha256]" class="ltr" maxlength="64" value="<?= e($d['sha256']) ?>"><?php endif; ?>
                  </fieldset>
                <?php } ?>
                <?= $note() ?>
              </form>
            </div>

            </div>
            <?php
        }, 'site', ['<a class="btn ghost sm" href="' . e($home) . '" target="_blank" rel="noopener">مشاهدهٔ سایت</a>']);
    }

    public static function notices(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $action = (string)($_POST['action'] ?? 'save');
            $id = (int)($_POST['id'] ?? 0);
            if ($action === 'settings') {
                Settings::set('notices_enabled', isset($_POST['notices_enabled']));
                Settings::set('notices_daily_cap', max(1, min(5, (int)self::post('notices_daily_cap', '1'))));
                Admin::audit('notices_settings', 'settings', null, $reason);
                Admin::flash('ذخیره شد.');
                Admin::redirect('p=notices');
            }
            if ($action === 'cancel' || $action === 'toggle') {
                $n = Db::one('SELECT * FROM {notices} WHERE id = ?', [$id]);
                if ($n === null) throw new ApiError('notice', 'اعلان پیدا نشد.');
                $row = $action === 'cancel' ? ['canceled_at' => Clock::sql(), 'active' => 0] : ['active' => $n['active'] ? 0 : 1];
                Db::update('notices', $row + ['updated_at' => Clock::sql()], 'id = ?', [$id]);
                Admin::audit('notice_' . $action, 'notice', $id, $reason, null, $row);
                Admin::redirect('p=notices');
            }
            $title = mb_substr(self::post('title'), 0, 80);
            $body = mb_substr(self::post('body'), 0, 300);
            if ($title === '' || $body === '') throw new ApiError('text', 'عنوان و متن اعلان لازم است.');
            $target = self::post('target_app') !== '' ? self::post('target_app') : self::post('target_url');
            if (!Notices::validTarget($target)) throw new ApiError('target', 'مقصد باید یکی از صفحه‌های برنامه یا یک نشانی https باشد.');
            $stores = array_values(array_intersect(array_keys(Notices::STORES), (array)($_POST['stores'] ?? [])));
            if (!$stores) throw new ApiError('stores', 'دست‌کم یک نسخه را انتخاب کنید.');
            $media = (int)self::post('media_id', '0');
            $sendAt = isset($_POST['send_now']) ? null : Admin::dtParse(self::post('send_at'));
            $endsAt = Admin::dtParse(self::post('ends_at'));
            $row = [
                'title' => $title, 'body' => $body, 'target' => $target ?: null,
                'media_id' => $media > 0 && Db::value("SELECT id FROM {media} WHERE id = ? AND kind = 'image'", [$media]) !== null ? $media : null,
                'audience' => isset(Notices::AUDIENCES[self::post('audience')]) ? self::post('audience') : 'free',
                'stores' => implode(',', $stores), 'send_at' => $sendAt, 'ends_at' => $endsAt, 'active' => 1, 'updated_at' => Clock::sql(),
            ];
            $nid = Db::insert('notices', $row + ['admin_id' => (int)Admin::$me['id'], 'created_at' => Clock::sql()]);
            Admin::audit('notice_create', 'notice', $nid, $reason, null, ['title' => $title]);
            Admin::flash('اعلان ثبت شد. گوشی‌ها در نخستین همگام‌سازی (معمولاً ظرف چند ساعت) آن را نشان می‌دهند.');
            Admin::redirect('p=notices');
        }
        $rows = Db::all('SELECT * FROM {notices} ORDER BY id DESC LIMIT 100');
        $media = Db::all("SELECT id, original_name FROM {media} WHERE kind = 'image' ORDER BY id DESC LIMIT 100");
        Admin::layout('اعلان‌های تبلیغاتی', function () use ($rows, $media) {
            ?>
            <div class="alert info">
              این اعلان‌ها در <b>نوار اعلان گوشی</b> نشان داده می‌شوند، فقط برای کسانی که هنگام نخستین اجرا یا از تنظیمات برنامه «اعلان پیشنهادها» را پذیرفته‌اند.
              کاربران Pro فقط وقتی اعلان می‌گیرند که خودشان آن را روشن کرده باشند.
              <br><b>زمان رسیدن:</b> برنامه چند بار در روز (وقتی گوشی اینترنت دارد و Android اجازه می‌دهد) اعلان‌ها را می‌گیرد؛ ارسال لحظه‌ای (Push) نیست و ممکن است تا چند ساعت طول بکشد.
              هر گوشی هر اعلان را فقط یک بار می‌بیند و در هر ۲۴ ساعت حداکثر به تعداد سقف روزانه.
            </div>
            <div class="grid g2">
            <div class="card"><h3>اعلان تازه</h3>
              <form method="post"><?= Admin::csrfField() ?>
                <label>عنوان</label><input type="text" name="title" maxlength="80" required placeholder="مثلاً: ۳۰٪ تخفیف دیوان Pro">
                <label>متن</label><textarea name="body" maxlength="300" required></textarea>
                <label>تصویر (اختیاری، از رسانه‌ها)</label><select name="media_id"><option value="0">بدون تصویر</option><?php foreach ($media as $m) echo '<option value="' . (int)$m['id'] . '">' . e($m['original_name']) . '</option>'; ?></select>
                <div class="row">
                  <div><label>مقصد در برنامه</label><select name="target_app"><option value="">صفحهٔ اصلی</option><?php foreach (Ads::APP_TARGETS as $k => $l) echo '<option value="' . e($k) . '">' . e($l) . '</option>'; ?></select></div>
                  <div><label>یا نشانی وب (https؛ فقط در نسخهٔ سایت)</label><input type="text" name="target_url" class="ltr" placeholder="https://..."></div>
                </div>
                <div class="row">
                  <div><label>مخاطب</label><select name="audience"><?php foreach (Notices::AUDIENCES as $k => $l) echo '<option value="' . $k . '">' . e($l) . '</option>'; ?></select></div>
                  <div><label>نسخه‌ها</label><?php foreach (Notices::STORES as $k => $l) echo '<label class="check"><input type="checkbox" name="stores[]" value="' . $k . '"' . ($k !== 'direct' ? ' checked' : '') . '> ' . e($l) . '</label>'; ?></div>
                </div>
                <label class="check"><input type="checkbox" name="send_now" checked onchange="document.getElementById('nt-when').style.display=this.checked?'none':''"> ارسال از همین حالا</label>
                <div class="row" id="nt-when" style="display:none"><div><label>شروع ارسال (به وقت تهران)</label><input type="datetime-local" name="send_at"></div></div>
                <div class="row"><div><label>پایان (اختیاری)</label><input type="datetime-local" name="ends_at"></div></div>
                <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ثبت اعلان</button></div></div>
              </form>
            </div>
            <div class="card"><h3>تنظیمات</h3>
              <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="settings">
                <label class="check"><input type="checkbox" name="notices_enabled" <?= Notices::enabled() ? 'checked' : '' ?>> ارسال اعلان‌های تبلیغاتی روشن است (خاموش = توقف فوری همه)</label>
                <label>سقف اعلان برای هر گوشی در ۲۴ ساعت</label><input type="number" name="notices_daily_cap" min="1" max="5" value="<?= Notices::dailyCap() ?>">
                <p><button class="btn ghost">ذخیره</button></p>
              </form>
              <p class="hint">اعلان‌های حساب و تراکنش و یادآوری پایان اشتراک کانال جدا دارند و با خاموش کردن تبلیغات قطع نمی‌شوند.</p>
            </div>
            </div>
            <div class="table-wrap"><table><thead><tr><th>عنوان</th><th>مخاطب</th><th>نسخه‌ها</th><th>زمان</th><th>نمایش</th><th>باز شده</th><th>وضعیت</th><th></th></tr></thead><tbody>
            <?php foreach ($rows as $n):
                $state = $n['canceled_at'] ? '<span class="badge bad">لغوشده</span>' : (!$n['active'] ? '<span class="badge">متوقف</span>' : ($n['ends_at'] && Clock::ts($n['ends_at']) <= Clock::now() ? '<span class="badge">پایان‌یافته</span>' : ($n['send_at'] && Clock::ts($n['send_at']) > Clock::now() ? '<span class="badge info">زمان‌بندی‌شده</span>' : '<span class="badge ok">در حال ارسال</span>'))); ?>
              <tr><td><b><?= e($n['title']) ?></b><br><small class="muted"><?= e(mb_substr($n['body'], 0, 80)) ?></small></td>
                <td><?= e(Notices::AUDIENCES[$n['audience']] ?? $n['audience']) ?></td>
                <td><?= e(implode('، ', array_map(fn($s) => Notices::STORES[$s] ?? $s, explode(',', $n['stores'])))) ?></td>
                <td><?= $n['send_at'] ? e(jdate($n['send_at'])) : 'فوری' ?><?= $n['ends_at'] ? '<br><small>تا ' . e(jdate($n['ends_at'])) . '</small>' : '' ?></td>
                <td><?= fa_digits((int)$n['shown']) ?></td><td><?= fa_digits((int)$n['opened']) ?></td><td><?= $state ?></td>
                <td><?php if (!$n['canceled_at']): ?>
                  <form method="post" style="display:inline"><?= Admin::csrfField() ?><input type="hidden" name="id" value="<?= (int)$n['id'] ?>"><button class="btn sm ghost" name="action" value="toggle"><?= $n['active'] ? 'توقف' : 'ادامه' ?></button>
                  <button class="btn sm red" name="action" value="cancel" data-confirm="این اعلان برای همیشه لغو شود؟">لغو</button></form>
                <?php endif; ?></td></tr>
            <?php endforeach; if (!$rows) echo '<tr><td colspan="8" class="muted">هنوز اعلانی ثبت نشده.</td></tr>'; ?>
            </tbody></table></div>
            <?php
        }, 'notices');
    }

    // ================================================================ payment gateway in Iran
    public static function bridge(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::managerOnly();
            $reason = Admin::requireReason();
            $action = (string)($_POST['action'] ?? 'save');
            $cfg = PaymentBridge::cfg();
            if (in_array($action, ['gen_key', 'set_key', 'activate_next', 'clear_next'], true)) self::ownerOnly();

            if ($action === 'ping' || $action === 'test_myket' || $action === 'test_bazaar') {
                $_SESSION['bridge_result'] = $action === 'ping' ? self::bridgePing() : PaymentBridge::testStore($action === 'test_myket' ? 'myket' : 'bazaar', true);
                Admin::audit('bridge_' . $action, 'settings', null, $reason, null, ['result' => $_SESSION['bridge_result'][0]]);
                Admin::redirect('p=bridge');
            }
            if ($action === 'gen_key' || $action === 'set_key') {
                $key = $action === 'gen_key' ? bin2hex(random_bytes(32)) : strtolower(preg_replace('/\s+/', '', self::post('new_key')));
                if (!preg_match('/^[0-9a-f]{64}$/', $key)) throw new ApiError('key', 'کلید باید ۶۴ نویسهٔ هگز (0-9 و a-f) باشد.');
                preg_match('/(\d+)$/', (string)$cfg['key_id'], $m);
                $nextId = $action === 'set_key' && preg_match('/^[a-z0-9_-]{1,32}$/', self::post('new_key_id')) ? self::post('new_key_id') : 'k' . (((int)($m[1] ?? 0)) + 1);
                Settings::setSecret('bridge_key_next', $key);
                Settings::set('payment_bridge', ['next_key_id' => $nextId] + $cfg);
                Admin::audit('bridge_key_next', 'settings', null, $reason, null, ['key_id' => $nextId]);
                if ($action === 'gen_key') $_SESSION['bridge_new_key'] = [$nextId, $key];
                Admin::flash('کلید تازه با شناسهٔ «' . $nextId . '» آماده است. آن را در config.php واسط ایران اضافه کنید و بعد «فعال کردن کلید تازه» را بزنید.');
                Admin::redirect('p=bridge');
            }
            if ($action === 'activate_next') {
                $next = Settings::secret('bridge_key_next'); $nextId = (string)($cfg['next_key_id'] ?? '');
                if ($next === '' || $nextId === '') throw new ApiError('key', 'کلید تازه‌ای در انتظار نیست.');
                $r = PaymentBridge::call('ping', [], $nextId, $next);
                if ($r['kind'] !== 'pong') throw new ApiError('key', 'واسط ایران کلید تازه را نپذیرفت؛ کلید فعلی دست نخورد. ' . ($r['error'] ?? $r['kind']));
                Settings::setSecret('bridge_key', $next);
                Settings::setSecret('bridge_key_next', '');
                $cfg['key_id'] = $nextId; unset($cfg['next_key_id']);
                Settings::set('payment_bridge', $cfg);
                Admin::audit('bridge_key_activated', 'settings', null, $reason, null, ['key_id' => $nextId]);
                Admin::flash('کلید «' . $nextId . '» فعال شد. حالا کلید قبلی را از config.php واسط ایران پاک کنید.');
                Admin::redirect('p=bridge');
            }
            if ($action === 'clear_next') {
                Settings::setSecret('bridge_key_next', ''); unset($cfg['next_key_id']); Settings::set('payment_bridge', $cfg);
                Admin::redirect('p=bridge');
            }
            // save: address and the route of each store
            $url = self::post('url', PaymentBridge::DEFAULT_URL);
            if (substr($url, -1) !== '/') $url .= '/';
            if (!PaymentBridge::validUrl($url)) throw new ApiError('url', 'نشانی واسط باید با https:// شروع شود، مثل https://pay.httpuser87890.ir/');
            $new = ['url' => $url] + $cfg;
            $msgs = [];
            foreach (['myket', 'bazaar'] as $s) {
                $want = self::post('route_' . $s) === 'bridge' ? 'bridge' : 'direct';
                $new['route_' . $s] = $want;
            }
            Settings::set('payment_bridge', $new);
            // A store goes through the gateway only if the gateway answers with the current key right now.
            if ($new['route_myket'] === 'bridge' || $new['route_bazaar'] === 'bridge') {
                $r = PaymentBridge::call('ping');
                if ($r['kind'] !== 'pong') {
                    foreach (['myket', 'bazaar'] as $s) if ($new['route_' . $s] === 'bridge' && ($cfg['route_' . $s] ?? 'direct') !== 'bridge') $new['route_' . $s] = 'direct';
                    Settings::set('payment_bridge', $new);
                    $msgs[] = 'واسط پاسخ نداد، پس فروشگاهی به مسیر واسط نرفت: ' . ($r['error'] ?? $r['kind']);
                }
            }
            Admin::audit('bridge_settings', 'settings', null, $reason, ['url' => $cfg['url'], 'route_myket' => $cfg['route_myket'], 'route_bazaar' => $cfg['route_bazaar']], ['url' => $new['url'], 'route_myket' => $new['route_myket'], 'route_bazaar' => $new['route_bazaar']]);
            Admin::flash($msgs ? implode(' ', $msgs) : 'ذخیره شد.', $msgs ? 'bad' : 'ok');
            Admin::redirect('p=bridge');
        }
        $cfg = PaymentBridge::cfg();
        $status = (array)Settings::get('bridge_status', []);
        $result = $_SESSION['bridge_result'] ?? null; unset($_SESSION['bridge_result']);
        $newKey = $_SESSION['bridge_new_key'] ?? null; unset($_SESSION['bridge_new_key']);
        Admin::layout('واسط پرداخت ایران', function () use ($cfg, $status, $result, $newKey) {
            $t = fn($ts) => $ts ? e(jdate(Clock::sql((int)$ts))) : '—';
            $hasKey = Settings::secret('bridge_key') !== '';
            $hasNext = Settings::secret('bridge_key_next') !== '' && !empty($cfg['next_key_id']);
            if ($result) echo '<div class="alert ' . e($result[0]) . '">' . $result[1] . '</div>';
            if ($newKey): ?>
              <div class="alert warn"><b>کلید تازه (فقط همین یک بار نمایش داده می‌شود):</b><br>
                شناسه: <code class="ltr"><?= e($newKey[0]) ?></code><br>
                <input type="text" class="ltr" readonly value="<?= e($newKey[1]) ?>" onclick="this.select()" style="font-family:monospace">
                <br>در config.php واسط ایران، داخل 'keys' این خط را اضافه کنید: <code class="ltr">'<?= e($newKey[0]) ?>' => '…همین کلید…',</code> سپس «فعال کردن کلید تازه» را بزنید. این کلید را در پیام‌رسان یا GitHub نگذارید.</div>
            <?php endif; ?>
            <div class="alert info">
              مایکت از سرور هلند در دسترس نیست؛ با این بخش، سرور اصلی رسیدها را از راه سرور ایران (<span class="ltr">pay.httpuser87890.ir</span>) به مایکت و بازار می‌فرستد.
              واسط فقط پاسخ فروشگاه را برمی‌گرداند؛ تصمیم دربارهٔ خرید، حساب‌ها و اشتراک‌ها همین‌جا گرفته می‌شود. ارتباط با کلید مشترک امضا می‌شود و هر درخواست فقط یک بار پذیرفته می‌شود.
            </div>
            <div class="grid g2">
              <div class="card"><h3>وضعیت</h3>
                <p>مسیر مایکت: <?= PaymentBridge::route('myket') === 'bridge' ? '<span class="badge ok">از راه واسط ایران</span>' : '<span class="badge">مستقیم</span>' ?>
                  — مسیر بازار: <?= PaymentBridge::route('bazaar') === 'bridge' ? '<span class="badge ok">از راه واسط ایران</span>' : '<span class="badge">مستقیم</span>' ?></p>
                <p>کلید مشترک: <?= $hasKey ? '<span class="badge ok">ثبت شده</span> شناسهٔ <code class="ltr">' . e($cfg['key_id']) . '</code>' : '<span class="badge warn">ثبت نشده</span>' ?>
                  <?= $hasNext ? ' — کلید تازهٔ در انتظار: <code class="ltr">' . e($cfg['next_key_id']) . '</code>' : '' ?></p>
                <p>آخرین ارتباط موفق: <?= $t($status['last_ok_at'] ?? null) ?></p>
                <p>آخرین خطای واسط: <?= $t($status['last_error_at'] ?? null) ?> <?= !empty($status['last_error']) ? '<br><small class="muted">' . e($status['last_error']) . '</small>' : '' ?></p>
                <?php if (!empty($status['store_error'])): ?><p>آخرین خطای فروشگاه از راه واسط: <?= $t($status['store_error_at'] ?? null) ?><br><small class="muted"><?= e($status['store_error']) ?></small></p><?php endif; ?>
                <form method="post" class="row" style="gap:6px;flex-wrap:wrap"><?= Admin::csrfField() ?>
                  <button class="btn ghost sm" name="action" value="ping">آزمایش واسط</button>
                  <button class="btn ghost sm" name="action" value="test_myket">آزمایش مایکت از راه واسط</button>
                  <button class="btn ghost sm" name="action" value="test_bazaar">آزمایش بازار از راه واسط</button>
                </form>
              </div>
              <div class="card"><h3>تنظیمات</h3>
                <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="save">
                  <label>نشانی واسط</label><input type="text" name="url" class="ltr" value="<?= e($cfg['url']) ?>">
                  <div class="row">
                    <div><label>مسیر مایکت</label><select name="route_myket"><option value="direct">مستقیم از هلند</option><option value="bridge"<?= PaymentBridge::route('myket') === 'bridge' ? ' selected' : '' ?>>از راه واسط ایران</option></select></div>
                    <div><label>مسیر بازار</label><select name="route_bazaar"><option value="direct">مستقیم از هلند</option><option value="bridge"<?= PaymentBridge::route('bazaar') === 'bridge' ? ' selected' : '' ?>>از راه واسط ایران</option></select></div>
                  </div>
                  <p class="hint">مسیر واسط فقط وقتی پذیرفته می‌شود که واسط همان لحظه با کلید فعلی پاسخ درست بدهد. در مسیر واسط، توکن مایکت و کلید بازار فقط در config.php سرور ایران لازم است.</p>
                  <div class="row"><div style="flex:3"><input type="text" name="reason" placeholder="یادداشت (اختیاری)"></div><div style="flex:0"><button class="btn gold">ذخیره</button></div></div>
                </form>
              </div>
            </div>
            <?php if (Admin::owner()): ?>
            <div class="card"><h3>کلید مشترک (فقط مدیر اصلی)</h3>
              <p class="hint">کلید فعلی هیچ‌وقت نمایش داده نمی‌شود. برای تعویض: «ساخت کلید تازه» → افزودن آن به config.php واسط (کنار کلید فعلی) → «فعال کردن کلید تازه» → پاک کردن کلید قبلی از config.php.</p>
              <form method="post" class="row" style="gap:6px;flex-wrap:wrap"><?= Admin::csrfField() ?>
                <button class="btn teal sm" name="action" value="gen_key" data-confirm="کلید تازه ساخته شود؟ کلید فعلی تا فعال‌سازی کلید تازه کار می‌کند.">ساخت کلید تازه</button>
                <?php if ($hasNext): ?><button class="btn gold sm" name="action" value="activate_next">فعال کردن کلید تازه</button><button class="btn ghost sm" name="action" value="clear_next">لغو کلید تازه</button><?php endif; ?>
              </form>
              <details style="margin-top:8px"><summary class="muted">کلیدی را که خودتان ساخته‌اید وارد کنید</summary>
                <form method="post"><?= Admin::csrfField() ?><input type="hidden" name="action" value="set_key">
                  <div class="row"><div style="flex:0;min-width:110px"><label>شناسه</label><input type="text" name="new_key_id" class="ltr" placeholder="k2"></div>
                  <div style="flex:3"><label>کلید (۶۴ نویسهٔ هگز)</label><input type="password" name="new_key" class="ltr" autocomplete="new-password"></div></div>
                  <p><button class="btn ghost sm">ثبت به‌عنوان کلید تازه</button></p>
                </form>
              </details>
            </div>
            <?php endif;
        }, 'bridge');
    }

    /** Ping with the current key: version, clock difference, store keys present, product ids allowed. */
    private static function bridgePing(): array
    {
        $r = PaymentBridge::call('ping');
        if ($r['kind'] !== 'pong') return ['bad', e($r['error'] ?? $r['kind'])];
        $skew = abs(time() - (int)($r['time'] ?? 0));
        $parts = ['واسط پاسخ داد و امضای پاسخ معتبر بود. نسخهٔ واسط: <code class="ltr">' . e($r['version'] ?? '?') . '</code>',
            'اختلاف ساعت دو سرور: ' . fa_digits($skew) . ' ثانیه' . ($skew > 120 ? ' <b>(زیاد است؛ باید زیر ۵ دقیقه بماند)</b>' : ''),
            'کلید مایکت روی واسط: ' . (!empty($r['stores']['myket']) ? 'ثبت شده' : '<b>ثبت نشده</b>') . ' — کلید بازار روی واسط: ' . (!empty($r['stores']['bazaar']) ? 'ثبت شده' : '<b>ثبت نشده</b>')];
        $allowed = (array)($r['skus'] ?? []);
        if ($allowed) {
            $missing = [];
            foreach (Plans::all() as $p) foreach (['myket', 'bazaar'] as $s) if ($p['active'] && !empty($p["sku_$s"]) && PaymentBridge::route($s) === 'bridge' && !in_array($p["sku_$s"], $allowed, true)) $missing[] = $p["sku_$s"];
            if ($missing) $parts[] = '<b>این شناسه‌ها در فهرست skus واسط نیستند و خریدشان بررسی نمی‌شود:</b> <code class="ltr">' . e(implode(', ', array_unique($missing))) . '</code>';
        }
        return [$skew > 120 || !empty($missing) ? 'warn' : 'ok', implode('<br>', $parts)];
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
                <label>یادداشت (اختیاری)</label><input type="text" name="reason">
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
                    <input type="text" name="reason" placeholder="یادداشت (اختیاری)" style="min-width:100px">
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
                <label>یادداشت (اختیاری)</label><input type="text" name="reason">
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
