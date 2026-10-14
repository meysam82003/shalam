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
  // Settings from the panel («مدیریت سایت»): notice bar, discount banner, ads, sections, texts and details.
  liveUrl: '/divan/site.php',
  downloads: {
    version: '5.5.0',
    date: '',
    // Files go in public_html/downloads/ next to the site (the package from the «Build all versions» workflow).
    android: { url: '/downloads/Divan-Emtiaz-Site-v5.5.0.apk', size: '', sha256: '' },
    // logo: empty uses the store logo kept on the site (site-assets/img/platforms/); an address replaces it.
    // badge: if you download the official «دریافت از …» badge from the store's developer panel, upload it to
    // site-assets/img/ and put its address here; the whole button then shows that badge.
    bazaar: { url: 'https://cafebazaar.ir/app/com.meysam.divanemtiaz', logo: '', badge: '' },
    myket: { url: 'https://myket.ir/app/com.meysam.divanemtiaz', logo: '', badge: '' },
    windows: { url: '/downloads/Divan-Emtiaz-Setup-v5.5.0.exe', size: '115 مگابایت', sha256: 'db0a8ec708e9a710862840c9cb9a1db68057cb940b707e08eadac2cb75ec75be' },
    iphone: { url: '/downloads/iPhone.mobileconfig', size: '97 کیلوبایت' },
    web: { url: '/play/' },
    pwa: { url: '/play/' },
    github: 'https://github.com/meysam82003/shalam/releases/latest'
  }
};
