(function () {
  var io = 'IntersectionObserver' in window ? new IntersectionObserver(function (es) {
    es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
  }, { threshold: .12 }) : null;
  document.querySelectorAll('.rv').forEach(function (el) { io ? io.observe(el) : el.classList.add('in'); });
  var nav = document.getElementById('nav');
  if (nav) nav.addEventListener('click', function (e) { if (e.target.tagName === 'A') nav.classList.remove('open'); });
  var y = document.getElementById('year');
  if (y) {
    try { y.textContent = new Intl.DateTimeFormat('fa-IR-u-ca-persian', { year: 'numeric' }).format(new Date()).replace(/[^۰-۹]/g, ''); } catch (e) {}
  }

  // Business details and downloads from site-config.js: an empty value keeps its row hidden, never a placeholder.
  var cfg = window.DIVAN_SITE || {};
  function get(path) {
    return path.split('.').reduce(function (o, k) { return o && o[k] != null ? o[k] : ''; }, cfg);
  }
  function fa(s) { return String(s).replace(/[0-9]/g, function (d) { return '۰۱۲۳۴۵۶۷۸۹'[d]; }); }
  function digits(s) { return String(s).replace(/[۰-۹]/g, function (d) { return '۰۱۲۳۴۵۶۷۸۹'.indexOf(d); }).replace(/[^0-9+]/g, ''); }

  document.querySelectorAll('[data-show]').forEach(function (row) {
    var v = get(row.getAttribute('data-show'));
    if (!v) { row.hidden = true; return; }
    row.hidden = false;
    row.querySelectorAll('[data-val]').forEach(function (el) {
      var kind = el.getAttribute('data-val');
      if (kind === 'tel') { el.textContent = v; el.setAttribute('href', 'tel:' + digits(v)); }
      else if (kind === 'mail') { el.textContent = v; el.setAttribute('href', 'mailto:' + v); }
      else if (kind === 'tg') { el.textContent = '@' + v; el.setAttribute('href', 'https://t.me/' + encodeURIComponent(v)); }
      else if (kind === 'eitaa') { el.textContent = '@' + v; el.setAttribute('href', 'https://eitaa.com/' + encodeURIComponent(v)); }
      else if (kind === 'bale') { el.textContent = '@' + v; el.setAttribute('href', 'https://ble.ir/' + encodeURIComponent(v)); }
      else el.textContent = kind === 'fa' ? fa(v) : v;
    });
  });

  // A seal block without the pasted official code stays out of sight (no empty heading).
  document.querySelectorAll('[data-trust]').forEach(function (w) { w.hidden = !w.querySelector('.trust > *'); });

  // Download cards: a platform without a published file says «به‌زودی» and has no link.
  var dl = cfg.downloads || {};
  document.querySelectorAll('[data-dl]').forEach(function (card) {
    var key = card.getAttribute('data-dl');
    var item = dl[key] || {};
    var btn = card.querySelector('[data-dl-btn]');
    var meta = card.querySelector('[data-dl-meta]');
    if (item.url) {
      if (btn) { btn.setAttribute('href', item.url); btn.removeAttribute('aria-disabled'); btn.classList.remove('off'); }
    } else if (btn) {
      btn.removeAttribute('href');
      btn.setAttribute('aria-disabled', 'true');
      btn.classList.add('off');
      btn.textContent = 'به‌زودی';
    }
    if (meta) {
      var parts = [];
      if (item.url && dl.version && key !== 'bazaar' && key !== 'myket' && key !== 'web') parts.push('نسخهٔ ' + fa(dl.version));
      if (item.url && item.size) parts.push(fa(item.size));
      meta.textContent = parts.join(' · ');
    }
    var sum = card.querySelector('[data-dl-sha]');
    if (sum) { if (item.url && item.sha256) { sum.hidden = false; sum.querySelector('code').textContent = item.sha256; } else sum.hidden = true; }
  });
  document.querySelectorAll('[data-dl-github]').forEach(function (a) { if (dl.github) a.setAttribute('href', dl.github); else a.hidden = true; });

  // Organization data for search engines, from the same real details (nothing is added when they are empty).
  var c = cfg.contact || {}, b = cfg.business || {};
  var org = { '@context': 'https://schema.org', '@type': 'Organization', name: b.brand || 'دیوان امتیاز', url: location.origin + '/',
    logo: location.origin + '/favicon.svg' };
  if (b.owner) org.legalName = b.owner;
  var point = { '@type': 'ContactPoint', contactType: 'customer support', availableLanguage: ['fa'] };
  if (c.email) point.email = c.email;
  if (c.phone) point.telephone = c.phone;
  org.contactPoint = [point];
  if (c.address) org.address = { '@type': 'PostalAddress', streetAddress: c.address, postalCode: c.postalCode || undefined, addressCountry: 'IR' };
  var s = document.createElement('script');
  s.type = 'application/ld+json';
  s.textContent = JSON.stringify(org);
  document.head.appendChild(s);

  // Official store logos (or the store's full badge when the owner uploaded it) on the «دریافت از …» buttons.
  document.querySelectorAll('[data-store]').forEach(function (a) {
    var st = (cfg.downloads || {})[a.getAttribute('data-store')] || {};
    var link = a.tagName === 'A';
    if (st.url && link) a.href = st.url;
    if (st.badge && link) {
      var b = document.createElement('img'); b.src = st.badge; b.alt = a.textContent.trim();
      a.classList.add('badge'); a.textContent = ''; a.appendChild(b);
      return;
    }
    // The official logo replaces the plain bag icon once it has loaded; if the store's site is unreachable the icon stays.
    var slot = a.querySelector('.store-logo, .dl-ico');
    if (!slot || !st.logo) return;
    var img = new Image();
    img.alt = ''; img.referrerPolicy = 'no-referrer';
    img.onload = function () { slot.textContent = ''; slot.appendChild(img); slot.hidden = false; };
    img.src = st.logo;
  });

  // Home page buttons: straight to the file once it is published; until then to its card on the download page.
  document.querySelectorAll('[data-direct]').forEach(function (a) {
    var item = (cfg.downloads || {})[a.getAttribute('data-direct')] || {};
    if (item.url) a.href = item.url;
  });

  // download.html#iphone etc. opens that install guide.
  function openHash() {
    var d = location.hash && document.getElementById(location.hash.slice(1));
    if (d && d.tagName === 'DETAILS') d.open = true;
  }
  openHash();
  window.addEventListener('hashchange', openHash);
  if (cfg.buyUrl) document.querySelectorAll('[data-site-buy]').forEach(function (a) { a.href = cfg.buyUrl; });
})();
