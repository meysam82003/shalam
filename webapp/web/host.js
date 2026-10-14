/*
 * Divan Emtiaz — host of the web, Windows and iPhone versions. The app itself (divan.js) is the Android app's own
 * code compiled to JavaScript; this file gives it what Android's system gives it: the screen (one canvas), stored
 * files and settings (IndexedDB, loaded before start so reading stays synchronous), fonts, touch and keyboard,
 * sound, the microphone, notifications, sharing, the file picker and the separate payment window.
 */
(function () {
  'use strict';
  var DH = window.DH = {};
  var cfgEl = document.getElementById('divan-config');
  var cfg = window.DIVAN_CONFIG = cfgEl ? JSON.parse(cfgEl.textContent) : (window.DIVAN_CONFIG || {});
  var reloadBtn = document.getElementById('crash-reload');
  if (reloadBtn) reloadBtn.addEventListener('click', function () { location.reload(); });
  var frame = document.getElementById('app');
  var canvas = document.getElementById('screen');
  var ctx = canvas.getContext('2d', { alpha: false });
  DH.ctx = ctx;
  DH.noDash = [];

  // Browsers set User-Agent themselves; the app's own header would only log a refusal on every request.
  var setHeader = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.setRequestHeader = function (name, value) {
    if (String(name).toLowerCase() === 'user-agent') return;
    return setHeader.call(this, name, value);
  };

  // ------------------------------------------------------------------ screen size
  var phone = window.matchMedia('(max-width: 639px), (max-height: 559px)');
  function measureScreen() {
    frame.classList.toggle('full', phone.matches || document.documentElement.classList.contains('app-window'));
    var r = frame.getBoundingClientRect();
    var dpr = window.devicePixelRatio || 1;
    var w = Math.max(1, Math.round(r.width * dpr)), h = Math.max(1, Math.round(r.height * dpr));
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
    DH.dpr = dpr; DH.width = w; DH.height = h; DH.cssLeft = r.left; DH.cssTop = r.top;
  }
  measureScreen();

  DH.requestFrame = function (cb) { requestAnimationFrame(function (t) { cb(t); }); };

  var probe = document.createElement('div');
  probe.style.cssText = 'position:fixed;visibility:hidden;pointer-events:none;padding:env(safe-area-inset-top) env(safe-area-inset-right) env(safe-area-inset-bottom) env(safe-area-inset-left)';
  document.body.appendChild(probe);
  /** Safe-area inset in device pixels: 0 top, 1 right, 2 bottom, 3 left (only when the app fills the window). */
  DH.inset = function (i) {
    if (!frame.classList.contains('full')) return 0;
    var s = getComputedStyle(probe);
    var v = [s.paddingTop, s.paddingRight, s.paddingBottom, s.paddingLeft][i];
    return Math.round((parseFloat(v) || 0) * DH.dpr);
  };
  DH.imeHeight = function () {
    var vv = window.visualViewport;
    if (!vv || !field.el) return 0;
    var hidden = window.innerHeight - vv.height - vv.offsetTop;
    return hidden > 80 ? Math.round(hidden * DH.dpr) : 0;
  };

  // ------------------------------------------------------------------ input events
  DH.listen = function (pointer, wheel, back, resize, visible, hidden) {
    var active = -1;
    function pos(e) { var r = canvas.getBoundingClientRect(); return [(e.clientX - r.left) * DH.dpr, (e.clientY - r.top) * DH.dpr]; }
    canvas.addEventListener('pointerdown', function (e) {
      if (active !== -1 || e.button > 0) return;
      active = e.pointerId;
      try { canvas.setPointerCapture(e.pointerId); } catch (x) {}
      unlockAudio();
      var p = pos(e); pointer(0, p[0], p[1], e.timeStamp);
    });
    canvas.addEventListener('pointermove', function (e) {
      if (e.pointerId !== active) return;
      var list = e.getCoalescedEvents ? e.getCoalescedEvents() : [e];
      for (var i = 0; i < list.length; i++) { var p = pos(list[i]); pointer(1, p[0], p[1], list[i].timeStamp); }
    });
    function end(kind) {
      return function (e) {
        if (e.pointerId !== active) return;
        active = -1;
        var p = pos(e); pointer(kind, p[0], p[1], e.timeStamp);
      };
    }
    canvas.addEventListener('pointerup', end(2));
    canvas.addEventListener('pointercancel', end(3));
    canvas.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    canvas.addEventListener('wheel', function (e) {
      e.preventDefault();
      var k = e.deltaMode === 1 ? 32 : e.deltaMode === 2 ? DH.height / DH.dpr : 1;
      var p = pos(e); wheel(p[0], p[1], e.deltaX * k * DH.dpr, e.deltaY * k * DH.dpr);
    }, { passive: false });
    // The browser's back (and Android's back gesture in the installed app) is the app's back.
    history.replaceState({ divan: 0 }, '');
    history.pushState({ divan: 1 }, '');
    window.addEventListener('popstate', function () { history.pushState({ divan: 1 }, ''); back(); });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !field.el) { e.preventDefault(); back(); }
    });
    var resizeTimer = 0;
    function onResize() {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(function () { measureScreen(); resize(); }, 30);
    }
    window.addEventListener('resize', onResize);
    if (window.visualViewport) window.visualViewport.addEventListener('resize', onResize);
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible') { visible(); if (wakeWanted) keepAwake(true); } else hidden();
    });
  };

  var wakeWanted = false, wakeLock = null;
  function keepAwake(on) {
    wakeWanted = on;
    if (!('wakeLock' in navigator)) return;
    if (on && !wakeLock) navigator.wakeLock.request('screen').then(function (l) { wakeLock = l; l.addEventListener('release', function () { wakeLock = null; }); }, function () {});
    if (!on && wakeLock) { wakeLock.release(); wakeLock = null; }
  }
  DH.keepAwake = keepAwake;
  DH.lightBars = function (light) {
    var m = document.querySelector('meta[name=theme-color]');
    if (m) m.setAttribute('content', light ? '#F4EAD2' : '#050B1A');
  };
  DH.leave = function () { try { window.close(); } catch (e) {} };

  // ------------------------------------------------------------------ drawing
  DH.newCanvas = function (w, h) { var c = document.createElement('canvas'); c.width = Math.max(1, w); c.height = Math.max(1, h); return c; };
  DH.path2d = function (c) {
    var p = new Path2D(), i = 0, n = c.length;
    while (i < n) {
      switch (c[i]) {
        case 0: p.moveTo(c[i + 1], c[i + 2]); i += 3; break;
        case 1: p.lineTo(c[i + 1], c[i + 2]); i += 3; break;
        case 2: p.quadraticCurveTo(c[i + 1], c[i + 2], c[i + 3], c[i + 4]); i += 5; break;
        case 3: p.bezierCurveTo(c[i + 1], c[i + 2], c[i + 3], c[i + 4], c[i + 5], c[i + 6]); i += 7; break;
        default: p.closePath(); i += 1;
      }
    }
    return p;
  };
  function rings(a) {
    var out = [], o = 1, count = a[0] | 0;
    for (var r = 0; r < count; r++) {
      var n = a[o++] | 0, ring = [];
      for (var k = 0; k < n; k++) { ring.push([a[o], a[o + 1]]); o += 2; }
      if (n >= 3) out.push([ring]);
    }
    return out;
  }
  /** Path.op on flattened contours (polygon-clipping); the result rings are filled even-odd. */
  DH.pathOp = function (a, b, op) {
    var A = rings(a), B = rings(b), pc = window.polygonClipping, res;
    try {
      if (!A.length && op !== 2 && op !== 4) res = op === 3 ? B : [];
      else if (!B.length) res = op === 1 || op === 4 ? [] : A;
      else res = op === 0 ? pc.difference(A, B) : op === 1 ? pc.intersection(A, B) : op === 2 ? pc.union(A, B) : op === 3 ? pc.xor(A, B) : pc.difference(B, A);
    } catch (e) { res = op === 0 || op === 2 || op === 3 ? A : []; }
    var flat = [0], count = 0;
    res.forEach(function (poly) {
      poly.forEach(function (ring) {
        var n = ring.length;
        if (n > 1 && ring[0][0] === ring[n - 1][0] && ring[0][1] === ring[n - 1][1]) n--;
        flat.push(n);
        for (var k = 0; k < n; k++) flat.push(ring[k][0], ring[k][1]);
        count++;
      });
    });
    flat[0] = count;
    return new Float32Array(flat);
  };
  function css(c) { return 'rgba(' + ((c >> 16) & 255) + ',' + ((c >> 8) & 255) + ',' + (c & 255) + ',' + (((c >>> 24) & 255) / 255) + ')'; }
  function stops(g, colors, pos) {
    var n = colors.length;
    for (var i = 0; i < n; i++) {
      var t = pos && pos.length === n ? pos[i] : (n === 1 ? 0 : i / (n - 1));
      g.addColorStop(Math.min(1, Math.max(0, t)), css(colors[i]));
    }
    return g;
  }
  DH.linear = function (x0, y0, x1, y1, colors, pos) { return stops(ctx.createLinearGradient(x0, y0, x1, y1), colors, pos); };
  DH.radial = function (cx, cy, r, colors, pos) { return stops(ctx.createRadialGradient(cx, cy, 0, cx, cy, Math.max(0.001, r)), colors, pos); };
  DH.sweep = function (cx, cy, colors, pos) {
    if (!ctx.createConicGradient) return css(colors[0]);
    return stops(ctx.createConicGradient(0, cx, cy), colors, pos);
  };
  DH.pattern = function (img, rx, ry) { return ctx.createPattern(img, rx && ry ? 'repeat' : rx ? 'repeat-x' : ry ? 'repeat-y' : 'no-repeat'); };
  DH.roundRect = function (x, l, t, r, b, rx, ry) {
    rx = Math.max(0, Math.min(rx, (r - l) / 2)); ry = Math.max(0, Math.min(ry, (b - t) / 2));
    x.beginPath();
    if (rx <= 0 || ry <= 0) { x.rect(l, t, r - l, b - t); return; }
    x.moveTo(l + rx, t);
    x.lineTo(r - rx, t); x.ellipse(r - rx, t + ry, rx, ry, 0, -Math.PI / 2, 0);
    x.lineTo(r, b - ry); x.ellipse(r - rx, b - ry, rx, ry, 0, 0, Math.PI / 2);
    x.lineTo(l + rx, b); x.ellipse(l + rx, b - ry, rx, ry, 0, Math.PI / 2, Math.PI);
    x.lineTo(l, t + ry); x.ellipse(l + rx, t + ry, rx, ry, 0, Math.PI, Math.PI * 1.5);
    x.closePath();
  };
  DH.arc = function (x, cx, cy, rx, ry, start, sweep, center) {
    x.beginPath();
    if (center) x.moveTo(cx, cy);
    var a0 = start * Math.PI / 180, a1 = (start + sweep) * Math.PI / 180;
    x.ellipse(cx, cy, Math.max(0, rx), Math.max(0, ry), 0, a0, a1, sweep < 0);
    if (center) x.closePath();
  };
  DH.spacedText = function (x, s, px, py, spacing, stroke) {
    if ('letterSpacing' in x) {
      x.letterSpacing = spacing + 'px';
      if (stroke) x.strokeText(s, px, py); else x.fillText(s, px, py);
      x.letterSpacing = '0px';
      return;
    }
    if (stroke) x.strokeText(s, px, py); else x.fillText(s, px, py);
  };
  var mctx = document.createElement('canvas').getContext('2d'), mfont = '', mdir = '', mcache = new Map();
  DH.measure = function (font, s, dir) {
    var key = font + '\u0001' + dir + '\u0001' + s, v = mcache.get(key);
    if (v !== undefined) return v;
    if (mfont !== font) { mctx.font = font; mfont = font; }
    if (mdir !== dir) { mctx.direction = dir; mdir = dir; }
    v = mctx.measureText(s).width;
    if (mcache.size > 20000) mcache.clear();
    mcache.set(key, v);
    return v;
  };
  DH.encode = function (c, mime, q) {
    var url = c.toDataURL(mime, q), bin = atob(url.substring(url.indexOf(',') + 1)), out = new Int8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  };
  var images = Object.create(null);
  DH.decoded = function (key) { return images[key] || null; };
  DH.rememberDecoded = function (key, c) { images[key] = c; };
  function decode(key, bytes) {
    if (images[key]) return Promise.resolve(images[key]);
    var blob = new Blob([bytes.slice()]);
    var p = window.createImageBitmap ? createImageBitmap(blob) : new Promise(function (ok, bad) {
      var img = new Image(), u = URL.createObjectURL(blob);
      img.onload = function () { URL.revokeObjectURL(u); ok(img); };
      img.onerror = function (e) { URL.revokeObjectURL(u); bad(e); };
      img.src = u;
    });
    return p.then(function (img) { images[key] = img; return img; });
  }
  DH.decodeLater = function (key, bytes) { decode(key, bytes).then(function () { if (DH.redraw) DH.redraw(); }, function () {}); };
  DH.decodeThen = function (key, bytes, cb) { decode(key, bytes).then(function () { cb(); }, function () { cb(); }); };

  // ------------------------------------------------------------------ stored files and settings
  var db = null, pending = new Map(), flushTimer = 0;
  function openDb() {
    return new Promise(function (ok) {
      if (!window.indexedDB) { ok(null); return; }
      var req = indexedDB.open('divan-emtiaz', 1);
      req.onupgradeneeded = function () {
        var d = req.result;
        if (!d.objectStoreNames.contains('files')) d.createObjectStore('files');
        if (!d.objectStoreNames.contains('prefs')) d.createObjectStore('prefs');
      };
      req.onsuccess = function () { ok(req.result); };
      req.onerror = function () { ok(null); };
    });
  }
  function readAll(store) {
    return new Promise(function (ok) {
      if (!db) { ok([]); return; }
      var out = [], tx = db.transaction(store, 'readonly'), cur = tx.objectStore(store).openCursor();
      cur.onsuccess = function () { var c = cur.result; if (c) { out.push([c.key, c.value]); c.continue(); } else ok(out); };
      cur.onerror = function () { ok(out); };
    });
  }
  function flush() {
    flushTimer = 0;
    if (!db || !pending.size) return;
    var tx = db.transaction(['files', 'prefs'], 'readwrite');
    pending.forEach(function (v, k) {
      var store = k.charAt(0) === 'f' ? 'files' : 'prefs', key = k.substring(2);
      if (v === null) tx.objectStore(store).delete(key); else tx.objectStore(store).put(v, key);
    });
    pending.clear();
  }
  function queue(k, v) { pending.set(k, v); if (!flushTimer) flushTimer = setTimeout(flush, 120); }
  window.addEventListener('pagehide', flush);
  document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'hidden') flush(); });
  DH.saveFile = function (p, d, t) { queue('f:' + p, { data: d.slice().buffer, t: t }); };
  DH.deleteFile = function (p) { queue('f:' + p, null); };
  DH.savePrefs = function (n, json) { queue('p:' + n, json); };
  var blobs = Object.create(null);
  DH.blobUrl = function (key, d) {
    if (!blobs[key]) blobs[key] = URL.createObjectURL(new Blob([d.slice()]));
    return blobs[key];
  };

  // ------------------------------------------------------------------ typing (a real field over the EditText)
  var field = { el: null, closing: false };
  DH.field = {
    open: function (multi, type, value, hint, changed, action, blurred) {
      DH.field.close();
      var el = document.createElement(multi ? 'textarea' : 'input');
      if (!multi) el.type = type === 'password' ? 'password' : 'text';
      if (type === 'number') el.inputMode = 'numeric';
      el.className = 'field';
      el.value = value;
      el.placeholder = hint;
      el.autocomplete = 'off';
      el.setAttribute('autocapitalize', 'off');
      el.spellcheck = false;
      el.addEventListener('input', function () { changed(el.value); });
      el.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !multi) { e.preventDefault(); action(); }
        e.stopPropagation();
      });
      el.addEventListener('blur', function () { if (!field.closing && field.el === el) blurred(); });
      frame.appendChild(el);
      field.el = el;
      el.focus({ preventScroll: true });
    },
    close: function () {
      if (!field.el) return;
      field.closing = true;
      var el = field.el;
      field.el = null;
      el.blur();
      el.remove();
      field.closing = false;
    },
    value: function (v) { if (field.el && field.el.value !== v) field.el.value = v; },
    select: function (s, e) { if (field.el) try { field.el.setSelectionRange(s, e); } catch (x) {} },
    place: function (x, y, w, h, font, color, hintColor, align, rtl, padTop, lineH) {
      var el = field.el;
      if (!el) return;
      var st = el.style;
      var top = el.tagName === 'INPUT' ? y + padTop : y + padTop;
      var height = el.tagName === 'INPUT' ? lineH : Math.max(lineH, h - padTop);
      st.left = x + 'px'; st.top = top + 'px'; st.width = Math.max(10, w) + 'px'; st.height = height + 'px';
      st.font = font; st.lineHeight = lineH + 'px'; st.color = color; st.caretColor = color;
      st.textAlign = align; st.direction = rtl ? 'rtl' : 'ltr';
      st.setProperty('--hint', hintColor);
    }
  };

  // ------------------------------------------------------------------ toast, clipboard, links, sharing, files
  var toastEl = null, toastTimer = 0;
  DH.toast = function (t, ms) {
    clearTimeout(toastTimer);
    if (t === null) { if (toastEl) toastEl.classList.remove('on'); return; }
    if (!toastEl) { toastEl = document.createElement('div'); toastEl.className = 'toast'; frame.appendChild(toastEl); }
    toastEl.textContent = t;
    toastEl.classList.add('on');
    toastTimer = setTimeout(function () { toastEl.classList.remove('on'); }, ms);
  };
  DH.copy = function (t) {
    if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(t).catch(function () { legacyCopy(t); }); return; }
    legacyCopy(t);
  };
  function legacyCopy(t) {
    var a = document.createElement('textarea'); a.value = t; a.style.position = 'fixed'; a.style.opacity = '0';
    document.body.appendChild(a); a.select();
    try { document.execCommand('copy'); } catch (e) {}
    a.remove();
  }
  DH.openLink = function (u) {
    if (/^(mailto|tel):/i.test(u)) { location.href = u; return; }
    var w = window.open(u, '_blank', 'noopener');
    if (!w) location.href = u;
  };
  DH.download = function (name, mime, data) {
    var url = URL.createObjectURL(new Blob([data.slice()], { type: mime }));
    var a = document.createElement('a'); a.href = url; a.download = name || 'divan';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
  };
  DH.share = function (name, mime, data, text) {
    try {
      var file = new File([data.slice()], name || 'divan', { type: mime });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({ files: [file], text: text || undefined }).catch(function (e) { if (e && e.name !== 'AbortError') DH.download(name, mime, data); });
        return;
      }
    } catch (e) {}
    DH.download(name, mime, data);
  };
  DH.shareText = function (text) {
    if (navigator.share) { navigator.share({ text: text }).catch(function () {}); return; }
    DH.copy(text);
    DH.toast('متن کپی شد', 2000);
  };
  DH.pick = function (accept, cb) {
    var input = document.createElement('input');
    input.type = 'file';
    if (accept) input.accept = accept;
    input.style.display = 'none';
    var done = false;
    function finish(name, mime, bytes) { if (done) return; done = true; input.remove(); cb(name, mime, bytes); }
    input.addEventListener('change', function () {
      var f = input.files && input.files[0];
      if (!f) { finish(null, null, null); return; }
      f.arrayBuffer().then(function (b) { finish(f.name, f.type || 'application/octet-stream', new Int8Array(b)); }, function () { finish(null, null, null); });
    });
    input.addEventListener('cancel', function () { finish(null, null, null); });
    document.body.appendChild(input);
    input.click();
  };

  // ------------------------------------------------------------------ notifications
  var notifyCbs = Object.create(null);
  DH.askNotify = function (cb) {
    if (typeof Notification === 'undefined') { cb(false); return; }
    Notification.requestPermission().then(function (p) { cb(p === 'granted'); }, function () { cb(false); });
  };
  DH.notify = function (id, title, body, cb) {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
    notifyCbs[id] = cb;
    var opts = { body: body, tag: 'divan-' + id, icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', lang: 'fa', dir: 'rtl', data: { id: id } };
    if (navigator.serviceWorker && navigator.serviceWorker.controller) {
      navigator.serviceWorker.ready.then(function (reg) { reg.showNotification(title, opts); });
      return;
    }
    try { var n = new Notification(title, opts); n.onclick = function () { window.focus(); n.close(); cb(id); }; } catch (e) {}
  };
  if (navigator.serviceWorker) navigator.serviceWorker.addEventListener('message', function (e) {
    var d = e.data || {};
    if (d.type === 'notification-click' && notifyCbs[d.id]) notifyCbs[d.id](d.id);
  });

  // ------------------------------------------------------------------ sound
  var actx = null;
  function audioCtx() {
    if (!actx) { var A = window.AudioContext || window.webkitAudioContext; if (A) actx = new A(); }
    return actx;
  }
  function unlockAudio() { var a = audioCtx(); if (a && a.state === 'suspended') a.resume(); }
  var sounds = [], nextSound = 1;
  DH.snd = {
    loadUrl: function (u) { var a = new Audio(); a.preload = 'auto'; a.src = u; sounds[nextSound] = { a: a }; return nextSound++; },
    loadBytes: function (b) {
      var u = URL.createObjectURL(new Blob([b.slice()]));
      var a = new Audio(); a.preload = 'auto'; a.src = u; sounds[nextSound] = { a: a, url: u }; return nextSound++;
    },
    play: function (h, loop, vol, done) {
      var s = sounds[h]; if (!s) return;
      s.a.loop = loop; s.a.volume = Math.max(0, Math.min(1, vol));
      s.a.onended = function () { done(); };
      var p = s.a.play(); if (p && p.catch) p.catch(function () {});
    },
    stop: function (h) { var s = sounds[h]; if (s) s.a.pause(); },
    release: function (h) { var s = sounds[h]; if (!s) return; s.a.pause(); s.a.removeAttribute('src'); if (s.url) URL.revokeObjectURL(s.url); delete sounds[h]; },
    playing: function (h) { var s = sounds[h]; return !!s && !s.a.paused && !s.a.ended; },
    volume: function (h, v) { var s = sounds[h]; if (s) s.a.volume = Math.max(0, Math.min(1, v)); },
    duration: function (h) { var s = sounds[h]; return s && isFinite(s.a.duration) ? Math.round(s.a.duration * 1000) : 0; },
    tone: function (type, ms, vol) {
      var a = audioCtx(); if (!a) return;
      DH.snd.stopTone();
      // ToneGenerator tones by family: beeps, acknowledgement chirps and the high PBX double beep.
      var plan = { 24: [[1000, 120]], 28: [[1000, 80], [0, 60], [1000, 80]], 25: [[1400, 70], [0, 40], [1800, 90]], 26: [[600, 160]], 27: [[1200, 220]],
        70: [[2600, 90], [0, 70], [2600, 90], [0, 70], [2600, 90]], 93: [[1300, 120], [0, 60], [1300, 120]], 89: [[1480, 60]], 33: [[1200, 60]], 0: [[941, 120]],
        40: [[2400, 150]], 88: [[1000, 260]], 23: [[1200, 90], [0, 60], [1200, 90]], 97: [[1300, 90], [0, 60], [1300, 90]], 92: [[1400, 160], [0, 60], [1000, 160]] }[type] || [[880, 120]];
      var t = a.currentTime + 0.01, g = a.createGain(), level = Math.max(0.02, Math.min(1, vol)) * 0.25;
      g.connect(a.destination); g.gain.value = 0;
      var total = 0, osc = a.createOscillator();
      osc.type = 'sine'; osc.connect(g);
      plan.forEach(function (step) {
        var f = step[0], d = step[1] / 1000;
        if (f > 0) { osc.frequency.setValueAtTime(f, t + total); g.gain.setValueAtTime(level, t + total); } else g.gain.setValueAtTime(0, t + total);
        total += d;
      });
      if (ms > 0 && ms / 1000 < total) total = ms / 1000;
      g.gain.setValueAtTime(0, t + total);
      osc.start(t); osc.stop(t + total + 0.02);
      DH.snd._tone = osc;
    },
    stopTone: function () { if (DH.snd._tone) { try { DH.snd._tone.stop(); } catch (e) {} DH.snd._tone = null; } }
  };

  // ------------------------------------------------------------------ microphone
  var mic = null, recs = [], nextRec = 1, analyser = null, levelBuf = null;
  DH.rec = {
    ready: function () { return !!mic; },
    ask: function (cb) {
      if (mic) { cb(true); return; }
      if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { cb(false); return; }
      navigator.mediaDevices.getUserMedia({ audio: true }).then(function (s) {
        mic = s;
        var a = audioCtx();
        if (a) { analyser = a.createAnalyser(); analyser.fftSize = 1024; a.createMediaStreamSource(s).connect(analyser); levelBuf = new Float32Array(analyser.fftSize); }
        cb(true);
      }, function () { cb(false); });
    },
    start: function () {
      if (!mic || typeof MediaRecorder === 'undefined') return -1;
      var r = new MediaRecorder(mic), chunks = [];
      r.ondataavailable = function (e) { if (e.data && e.data.size) chunks.push(e.data); };
      r.start();
      recs[nextRec] = { r: r, chunks: chunks };
      return nextRec++;
    },
    stop: function (id, discard, cb) {
      var s = recs[id]; if (!s) return;
      delete recs[id];
      s.r.onstop = function () {
        if (discard) return;
        new Blob(s.chunks, { type: s.r.mimeType }).arrayBuffer().then(function (b) { cb(new Int8Array(b)); });
      };
      try { s.r.stop(); } catch (e) {}
    },
    level: function () {
      if (!analyser) return 0;
      analyser.getFloatTimeDomainData(levelBuf);
      var peak = 0;
      for (var i = 0; i < levelBuf.length; i++) peak = Math.max(peak, Math.abs(levelBuf[i]));
      return Math.round(peak * 32767);
    }
  };

  // ------------------------------------------------------------------ ad videos
  var videos = Object.create(null);
  DH.video = function (id, src, play, cb) {
    var v = videos[id];
    if (!v) {
      v = document.createElement('video');
      v.className = 'video'; v.muted = true; v.playsInline = true; v.setAttribute('playsinline', '');
      v.addEventListener('loadeddata', function () { cb(id, 0); });
      v.addEventListener('ended', function () { cb(id, 1); });
      frame.appendChild(v);
      videos[id] = v;
    }
    if (v.getAttribute('src') !== src) v.src = src;
    if (play) { var p = v.play(); if (p && p.catch) p.catch(function () {}); } else v.pause();
  };
  DH.videoPlace = function (id, x, y, w, h, visible) {
    var v = videos[id]; if (!v) return;
    v.style.left = x + 'px'; v.style.top = y + 'px'; v.style.width = w + 'px'; v.style.height = h + 'px';
    v.style.display = visible ? 'block' : 'none';
  };
  DH.videoRemove = function (id) { var v = videos[id]; if (!v) return; v.pause(); v.remove(); delete videos[id]; };

  // ------------------------------------------------------------------ payment window
  var payWin = null, payBack = [];
  DH.payOpen = function (u) {
    if (payWin && !payWin.closed) {
      try { payWin.location.href = u; } catch (e) {}
      try { payWin.focus(); } catch (e) {}
      return true;
    }
    var wide = window.innerWidth > 700;
    payWin = window.open(u, 'divan-pay', wide ? 'popup,width=480,height=820' : '');
    return !!payWin;
  };
  DH.payFocus = function () { if (payWin && !payWin.closed) { try { payWin.focus(); } catch (e) {} return true; } return false; };
  DH.payIsOpen = function () { return !!payWin && !payWin.closed; };
  DH.payClose = function () { if (payWin && !payWin.closed) try { payWin.close(); } catch (e) {} payWin = null; };
  DH.onPayBack = function (cb) { payBack.push(cb); };
  window.addEventListener('message', function (e) {
    var d = e.data;
    if (!d || d.divan !== 'back' || typeof d.href !== 'string' || !/^divan:\/\/[a-z0-9_\/]{1,80}$/i.test(d.href)) return;
    payBack.forEach(function (cb) { cb(d.href); });
  });

  // ------------------------------------------------------------------ start
  DH.crash = function (m) {
    var box = document.getElementById('crash');
    if (box) { box.querySelector('.msg').textContent = m; box.classList.add('on'); }
  };
  DH.started = function () {
    var l = document.getElementById('loading');
    if (l) { l.classList.add('done'); setTimeout(function () { l.remove(); }, 600); }
  };
  function fetchBytes(u) { return fetch(u).then(function (r) { if (!r.ok) throw new Error(u); return r.arrayBuffer(); }).then(function (b) { return new Int8Array(b); }); }
  function loadFonts() {
    if (!window.FontFace || !document.fonts) return Promise.resolve();
    var fonts = [new FontFace('Vazir', 'url(fonts/Vazirmatn-Regular.woff2) format("woff2")', { weight: '400' }),
      new FontFace('Vazir', 'url(fonts/Vazirmatn-Bold.woff2) format("woff2")', { weight: '700' })];
    return Promise.all(fonts.map(function (f) { return f.load().then(function (x) { document.fonts.add(x); }); })).catch(function () {});
  }
  function boot() {
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(function () {});
    var assets = {};
    Promise.all([
      openDb().then(function (d) { db = d; return Promise.all([readAll('files'), readAll('prefs')]); }),
      Promise.all((cfg.assets || []).map(function (p) { return fetchBytes('assets/' + p).then(function (b) { assets[p] = b; }); })),
      loadFonts()
    ]).then(function (r) {
      DH.boot = {
        files: r[0][0].map(function (e) { return [e[0], new Int8Array(e[1].data), e[1].t]; }),
        prefs: r[0][1].reduce(function (o, e) { o[e[0]] = e[1]; return o; }, {}),
        assets: assets,
        raw: cfg.raw || []
      };
      var s = document.createElement('script');
      s.src = 'divan.js?v=' + encodeURIComponent(cfg.versionName || '');
      s.onload = function () { main(); };
      s.onerror = function () { DH.crash('بارگیری برنامه ممکن نشد؛ اتصال را بررسی کنید و صفحه را تازه کنید.'); };
      document.body.appendChild(s);
    }, function (e) { DH.crash('آماده‌سازی برنامه ممکن نشد: ' + e); });
  }
  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(function () {});
  boot();
})();
