<?php
declare(strict_types=1);
if (!defined('DIVAN')) { http_response_code(403); exit; }

final class AppUpdates
{
    public const CHANNELS = ['bazaar' => 'کافه‌بازار', 'myket' => 'مایکت', 'direct' => 'سایت'];

    public static function forStore(string $store): array
    {
        if (!isset(self::CHANNELS[$store])) throw new ApiError('update_store', 'کانال انتشار نامعتبر است.');
        return Settings::get('android_update_' . $store, [
            'version_code' => 0, 'version_name' => '', 'required' => false, 'notes' => '',
        ]);
    }

    public static function fromForm(array $in): array
    {
        $code = filter_var($in['version_code'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 0, 'max_range' => 2100000000]]);
        $name = trim((string)($in['version_name'] ?? ''));
        if ($code === false || ($code > 0 && (strlen($name) > 40 || !preg_match('/^[0-9]+(?:\.[0-9]+){1,3}$/D', $name)))) {
            throw new ApiError('update_version', 'کد نسخه و شمارهٔ نسخه را به‌درستی وارد کنید.');
        }
        if ($code === 0 && !empty($in['required'])) {
            throw new ApiError('update_required', 'برای به‌روزرسانی اجباری، کد نسخهٔ منتشرشده را وارد کنید.');
        }
        return [
            'version_code' => $code, 'version_name' => $code > 0 ? $name : '',
            'required' => $code > 0 && !empty($in['required']),
            'notes' => $code > 0 ? mb_substr(trim((string)($in['notes'] ?? '')), 0, 1200) : '',
        ];
    }

    public static function save(string $store, array $in): void
    {
        $before = self::forStore($store);
        $policy = self::fromForm($in);
        Settings::set('android_update_' . $store, $policy);
        Admin::audit('android_app_update', 'settings', $store, '', $before, $policy);
    }

    public static function panel(): void
    {
        if ($_SERVER['REQUEST_METHOD'] === 'POST') {
            self::save((string)($_POST['store'] ?? ''), $_POST);
            Admin::flash('تنظیمات به‌روزرسانی ذخیره شد.');
            Admin::redirect('p=appupdates');
        }
        Admin::layout('به‌روزرسانی دیوان امتیاز', function () {
            echo '<p>پس از انتشار نسخه در هر کانال، کد ساخت (versionCode)، شمارهٔ نسخه و توضیحات را ثبت کنید. کد صفر اعلان آن کانال را خاموش می‌کند.</p>';
            foreach (self::CHANNELS as $store => $label) {
                $policy = self::forStore($store);
                ?>
                <form method="post" class="card">
                    <?= Admin::csrfField() ?>
                    <input type="hidden" name="store" value="<?= e($store) ?>">
                    <h2><?= e($label) ?></h2>
                    <label>کد نسخه (versionCode)</label>
                    <input type="number" class="ltr" name="version_code" min="0" max="2100000000" required value="<?= e((string)$policy['version_code']) ?>">
                    <label>شمارهٔ نسخه (مثلاً 5.6.6)</label>
                    <input class="ltr" name="version_name" maxlength="40" value="<?= e($policy['version_name']) ?>">
                    <label>توضیحات نسخه</label>
                    <textarea name="notes" maxlength="1200" rows="4"><?= e($policy['notes']) ?></textarea>
                    <label class="check"><input type="checkbox" name="required" value="1" <?= $policy['required'] ? 'checked' : '' ?>>
                        اجرای نسخه‌های قدیمی به به‌روزرسانی نیاز داشته باشد</label>
                    <button type="submit" class="btn gold">ذخیرهٔ تنظیمات <?= e($label) ?></button>
                </form>
                <?php
            }
        }, 'appupdates');
    }
}
