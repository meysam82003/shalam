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

  var cfg = window.DIVAN_SITE || {};

  // Settings from the panel («مدیریت سایت») come first when the server answers quickly; otherwise the files decide.
  var started = false;
  function begin(live) {
    if (started) return;
    started = true;
    if (live) mergeLive(live);
    render();
    if (live) applyLive(live);
  }
  if (cfg.liveUrl && window.fetch) {
    setTimeout(function () { begin(null); }, 2500);
    fetch(cfg.liveUrl, { credentials: 'omit' }).then(function (r) { return r.ok ? r.json() : null; })
      .then(function (j) { begin(j && j.v ? j : null); }, function () { begin(null); });
  } else begin(null);

  function mergeLive(live) {
    ['business', 'contact'].forEach(function (k) { cfg[k] = Object.assign({}, cfg[k] || {}, live[k] || {}); });
    var d = cfg.downloads = Object.assign({}, cfg.downloads || {});
    Object.keys(live.downloads || {}).forEach(function (k) {
      var v = live.downloads[k];
      d[k] = typeof v === 'object' ? Object.assign({}, d[k] || {}, v) : v;
    });
  }

  // Business details and downloads: an empty value keeps its row hidden, never a placeholder.
  function render() {
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
  }

  // ------------------------------------------------------------------ panel settings on the page
  function el(tag, cls, text) {
    var n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text) n.textContent = text;
    return n;
  }
  function safeHref(u) { return /^(https:\/\/|\/(?!\/))/i.test(u || '') ? u : ''; }

  function applyLive(live) {
    var home = !!document.querySelector('.hero');
    var main = document.querySelector('main');
    var texts = live.texts || {};
    document.querySelectorAll('[data-edit]').forEach(function (n) {
      var v = texts[n.getAttribute('data-edit')];
      if (v) n.textContent = v;
    });

    // Home page sections: order and visibility (the introduction stays first).
    var secs = live.sections || {};
    var hero = document.querySelector('section.hero');
    if (home && hero) {
      var list = Object.keys(secs).map(function (k) { return { el: document.getElementById(k), s: secs[k], k: k }; })
        .filter(function (x) { return x.el && x.el.tagName === 'SECTION'; })
        .sort(function (a, b) { return (a.s.order || 0) - (b.s.order || 0); });
      var after = hero;
      list.forEach(function (x) {
        after.parentNode.insertBefore(x.el, after.nextSibling);
        after = x.el;
        x.el.hidden = x.s.show === false;
        document.querySelectorAll('a[href="#' + x.k + '"]').forEach(function (a) { a.hidden = x.s.show === false; });
      });
    }

    // e-Namad seal set in the panel: the same markup as the official snippet, in every seal box of the page.
    var en = live.enamad;
    if (en && /^\d{1,12}$/.test(en.id) && /^[A-Za-z0-9]{8,64}$/.test(en.code)) {
      var q = '?id=' + en.id + '&Code=' + en.code;
      document.querySelectorAll('.trust[id^="enamad"]').forEach(function (box) {
        var a = document.createElement('a');
        a.setAttribute('referrerpolicy', 'origin'); a.target = '_blank'; a.href = 'https://trustseal.enamad.ir/' + q;
        var img = document.createElement('img');
        img.setAttribute('referrerpolicy', 'origin'); img.src = 'https://trustseal.enamad.ir/logo.aspx' + q;
        img.alt = ''; img.style.cursor = 'pointer'; img.setAttribute('code', en.code);
        a.appendChild(img);
        var old = box.querySelector('a');
        if (old) box.replaceChild(a, old); else box.insertBefore(a, box.firstChild);
      });
    }

    // Notice bar at the very top.
    var an = live.announce;
    if (an && an.text && (!an.home_only || home)) {
      var bar = el('div', 'site-announce tone-' + (an.tone || 'gold'));
      var w = el('div', 'wrap');
      w.appendChild(el('span', '', an.text));
      var href = safeHref(an.link);
      if (href) { var a = el('a', '', an.link_text || 'مشاهده'); a.href = href; if (/^https:/i.test(href)) { a.target = '_blank'; a.rel = 'noopener'; } w.appendChild(a); }
      bar.appendChild(w);
      document.body.insertBefore(bar, document.body.firstChild);
    }

    // Discount banner: on the home page under the introduction, elsewhere at the top of the page.
    var pr = live.promo;
    if (pr && pr.code && main) {
      var box = el('section', 'site-promo');
      var pw = el('div', 'wrap');
      var card = el('div', 'promo-card');
      var side = el('div', 'promo-off');
      side.appendChild(el('b', '', String(pr.percent).replace(/[0-9]/g, function (d) { return '۰۱۲۳۴۵۶۷۸۹'[d]; }) + '٪'));
      side.appendChild(el('span', '', 'تخفیف'));
      card.appendChild(side);
      var info = el('div', 'promo-info');
      info.appendChild(el('h3', '', pr.title || 'کد تخفیف دیوان Pro'));
      if (pr.text) info.appendChild(el('p', '', pr.text));
      var meta = [];
      if (pr.plan) meta.push('فقط برای «' + pr.plan + '»');
      if (pr.ends_at) {
        try { meta.push('تا ' + new Intl.DateTimeFormat('fa-IR-u-ca-persian', { day: 'numeric', month: 'long' }).format(new Date(pr.ends_at * 1000))); } catch (e) {}
      }
      if (meta.length) info.appendChild(el('small', '', meta.join(' · ')));
      card.appendChild(info);
      var act = el('div', 'promo-act');
      var code = el('button', 'promo-code ltr', pr.code);
      code.type = 'button';
      code.title = 'کپی کد';
      code.addEventListener('click', function () {
        var ok = function () { code.textContent = 'کپی شد'; setTimeout(function () { code.textContent = pr.code; }, 1400); };
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(pr.code).then(ok, function () {});
      });
      act.appendChild(code);
      var buy = el('a', 'btn gold', 'خرید با این کد');
      buy.href = safeHref(pr.buy) || (cfg.buyUrl + '?promo=' + encodeURIComponent(pr.code));
      act.appendChild(buy);
      card.appendChild(act);
      pw.appendChild(card);
      box.appendChild(pw);
      if (home && hero) hero.parentNode.insertBefore(box, hero.nextSibling); else main.insertBefore(box, main.firstChild);
    }

    // Ads in the site's slots (image, text and an https link only).
    var ads = live.ads || {};
    function adBox(a) {
      var s = el('section', 'site-ad'), w = el('div', 'wrap'), c = el(a.url ? 'a' : 'div', 'ad-card');
      if (a.url && /^https:\/\//i.test(a.url)) { c.href = a.url; c.target = '_blank'; c.rel = 'sponsored noopener'; }
      c.appendChild(el('span', 'ad-tag', 'تبلیغ'));
      if (a.image) { var im = new Image(); im.src = a.image; im.alt = a.title || ''; im.loading = 'lazy'; c.appendChild(im); }
      var t = el('div', 'ad-text');
      if (a.title) t.appendChild(el('b', '', a.title));
      if (a.body) t.appendChild(el('p', '', a.body));
      if (a.cta && a.url) t.appendChild(el('span', 'btn ghost', a.cta));
      c.appendChild(t);
      w.appendChild(c); s.appendChild(w);
      return s;
    }
    function put(ad, before) { if (ad && before && before.parentNode) before.parentNode.insertBefore(adBox(ad), before); }
    if (home && hero) {
      var shown = Array.prototype.filter.call(document.querySelectorAll('main > section[id]'), function (n) { return !n.hidden; });
      if (ads.site_top) { var top = document.querySelector('main > .site-promo') || hero; put(ads.site_top, top.nextSibling); }
      if (ads.site_middle && shown[2]) put(ads.site_middle, shown[2]);
      var dlSec = document.getElementById('download');
      if (ads.site_bottom && dlSec && !dlSec.hidden) put(ads.site_bottom, dlSec);
    } else if (ads.site_pages && main) {
      var first = main.querySelector('.site-promo');
      put(ads.site_pages, first ? first.nextSibling : main.firstChild);
    }
  }
})();
