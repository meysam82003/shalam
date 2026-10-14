/*
 * Divan Emtiaz site — the only file the owner edits for business details and downloads.
 *
 * Every empty value ("") stays hidden on the site; nothing is ever shown in its place. Fill only real, registered
 * information: the same name, phone and address as your e-Namad (اینماد) file, so banks see one identity everywhere.
 * After editing, upload just this file to public_html/site-assets/js/ (no other page needs changing).
 *
 * The e-Namad seal itself is NOT set here: paste the official code from your enamad.ir panel, exactly as given,
 * between ENAMAD-START and ENAMAD-END in index.html and contact.html (see server/docs/9-…md).
 */
window.DIVAN_SITE = {
  business: {
    brand: 'دیوان امتیاز',
    // نام حقیقی یا حقوقی صاحب کسب‌وکار، دقیقاً مطابق پروندهٔ اینماد؛ مثلاً «میثم …» یا «شرکت …»
    owner: '',
    // نوع: «حقیقی» یا «حقوقی»
    ownerType: '',
    // شناسهٔ ملی شرکت یا شمارهٔ ثبت (فقط برای اشخاص حقوقی؛ برای اشخاص حقیقی خالی بماند)
    registration: '',
    // سال شروع فعالیت به شمسی، مثلاً «۱۴۰۵»
    since: ''
  },
  contact: {
    email: 'meysamsalamatnasab@gmail.com',
    // تلفن ثابت ثبت‌شده در اینماد، با کد شهر؛ مثلاً «021-12345678»
    phone: '',
    // تلفن همراه پشتیبانی (اختیاری)
    mobile: '',
    // نشانی پستی ثبت‌شده در اینماد
    address: '',
    postalCode: '',
    // ساعات پاسخ‌گویی؛ مثلاً «شنبه تا چهارشنبه، ۹ تا ۱۷»
    hours: '',
    // شناسهٔ پشتیبانی در پیام‌رسان‌ها (اختیاری): فقط نام کاربری، بدون @
    telegram: '',
    eitaa: '',
    bale: ''
  },
  /*
   * Download section («دریافت دیوان امتیاز»). A platform with an empty url is shown as «به‌زودی» with no link.
   * Fill url, version, size and sha256 after each approved release (the release notes list them).
   */
  // نشانی صفحهٔ خرید اشتراک از سایت (روی سرور دیوان)
  buyUrl: '/divan/pay/buy.php',
  downloads: {
    version: '5.5.0',
    date: '',
    android: { url: '', size: '', sha256: '' },
    // logo: the store's own official icon, loaded from the store's site when the page opens.
    // badge: if you download the official «دریافت از …» badge from the store's developer panel, upload it to
    // site-assets/img/ and put its address here; the whole button then shows that badge.
    bazaar: { url: 'https://cafebazaar.ir/app/com.meysam.divanemtiaz', logo: 'https://cafebazaar.ir/favicon.ico', badge: '' },
    myket: { url: 'https://myket.ir/app/com.meysam.divanemtiaz', logo: 'https://myket.ir/favicon.ico', badge: '' },
    windows: { url: '', size: '', sha256: '' },
    iphone: { url: '', size: '' },
    web: { url: '/play/' },
    github: 'https://github.com/meysam82003/shalam/releases/latest'
  }
};
