/* Divan Emtiaz admin panel — confirmations, QR codes and the live ad preview. No external libraries. */
(function () {
  'use strict';

  document.addEventListener('click', function (ev) {
    var b = ev.target.closest('[data-confirm]');
    if (b && !window.confirm(b.getAttribute('data-confirm'))) ev.preventDefault();
  });

  var qr = document.getElementById('qr');
  if (qr && window.qrcode) {
    var q = window.qrcode(0, 'M');
    q.addData(qr.getAttribute('data-uri'));
    q.make();
    qr.innerHTML = q.createSvgTag({ cellSize: 4, margin: 2 });
  }

  // ---------------------------------------------------------------- ad editor preview
  var form = document.getElementById('ad-form');
  var box = document.getElementById('ad-preview');
  if (!form || !box) return;

  function val(id) { var el = document.getElementById(id); return el ? el.value : ''; }
  function esc(s) { return String(s || '').replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); }
  function color(v, d) { return /^#[0-9a-fA-F]{6}$/.test(v) ? v : d; }

  var uploadUrl = null, uploadKind = null;
  var upload = document.getElementById('f-upload');
  if (upload) upload.addEventListener('change', function () {
    if (uploadUrl) URL.revokeObjectURL(uploadUrl);
    var f = upload.files && upload.files[0];
    uploadUrl = f ? URL.createObjectURL(f) : null;
    uploadKind = f ? (f.type.indexOf('video') === 0 ? 'video' : 'image') : null;
    render();
  });

  function media() {
    if (uploadUrl) return { url: uploadUrl, kind: uploadKind };
    var sel = document.getElementById('f-media');
    var opt = sel && sel.options[sel.selectedIndex];
    if (!opt || !opt.value) return null;
    return { url: opt.getAttribute('data-url'), kind: opt.getAttribute('data-kind') };
  }

  function mediaTag(m, style) {
    if (!m) return '';
    if (m.kind === 'video') return '<video class="ad-img" src="' + esc(m.url) + '" muted autoplay loop playsinline style="' + (style || '') + '"></video>';
    return '<img class="ad-img" src="' + esc(m.url) + '" alt="" style="' + (style || '') + '">';
  }

  function adHtml() {
    var f = val('f-format'), t = esc(val('f-title')), b = esc(val('f-body')), cta = esc(val('f-cta'));
    var bg = color(val('f-bg'), '#1C2A4A'), fg = color(val('f-fg'), '#FFFFFF'), ac = color(val('f-accent'), '#C39A4A');
    var m = media();
    var label = '<span class="ad-label">تبلیغات</span>';
    var btn = cta ? '<span style="display:inline-block;margin-top:4px;background:' + ac + ';color:#111;border-radius:8px;padding:1px 8px;font-weight:700">' + cta + '</span>' : '';
    var close = document.querySelector('[name=closable]').checked ? '<span style="position:absolute;top:4px;left:6px;opacity:.7">✕</span>' : '';
    var box = 'position:relative;border-radius:12px;padding:8px;background:' + bg + ';color:' + fg + ';';
    switch (f) {
      case 'banner_small': return '<div style="' + box + 'padding:4px 8px;display:flex;gap:6px;align-items:center">' + label + '<b>' + t + '</b>' + close + '</div>';
      case 'banner_wide': return '<div style="' + box + 'padding:4px">' + (m ? mediaTag(m, 'height:44px;object-fit:cover') : '<b>' + t + '</b> ' + b) + '<div>' + label + '</div>' + close + '</div>';
      case 'text': return '<div style="' + box + '">' + label + '<b>' + t + '</b><div>' + b + '</div>' + btn + close + '</div>';
      case 'image': return '<div style="' + box + 'padding:4px">' + mediaTag(m) + '<div>' + label + ' ' + t + '</div>' + close + '</div>';
      case 'video': return '<div style="' + box + 'padding:4px">' + mediaTag(m) + '<div>' + label + ' ' + t + '</div>' + btn + close + '</div>';
      case 'native': return '<div class="blk" style="position:relative;display:flex;gap:6px;align-items:center">' + (m ? mediaTag(m, 'width:38px;height:38px;object-fit:cover;border-radius:10px') : '') + '<div style="flex:1"><b>' + t + '</b> ' + label + '<div style="opacity:.8">' + b + '</div></div>' + btn + '</div>';
      case 'sponsor': return '<div style="' + box + 'text-align:center">' + label + '<div>با حمایت</div><b>' + t + '</b>' + (m ? mediaTag(m, 'max-height:40px;object-fit:contain') : '') + '</div>';
      case 'corner': return '<div style="' + box + 'padding:3px;width:86px">' + mediaTag(m) + label + close + '</div>';
      case 'interstitial':
      case 'rewarded': return '<div style="' + box + 'position:absolute;inset:0;border-radius:22px;display:flex;flex-direction:column;justify-content:center;text-align:center;gap:6px">' + label + mediaTag(m, 'max-height:58%;object-fit:contain') + '<b style="font-size:.85rem">' + t + '</b><div>' + b + '</div>' + btn +
        (f === 'rewarded' ? '<div style="opacity:.75">پس از تماشا: یک ساعت بدون تبلیغ</div>' : '<span style="position:absolute;top:10px;left:12px">✕ بستن</span>') + '</div>';
      default: return '<div style="' + box + '">' + label + (m ? mediaTag(m, 'max-height:90px;object-fit:cover') : '') + '<b>' + t + '</b><div>' + b + '</div>' + btn + close + '</div>';
    }
  }

  function phone(light, wide) {
    var f = val('f-format');
    var ad = adHtml();
    var full = f === 'interstitial' || f === 'rewarded';
    var corner = f === 'corner';
    var content = '<div class="blk">دیوان امتیاز</div><div class="blk">ادامهٔ داوری — شلم</div>' +
      (full || corner ? '' : ad) + '<div class="blk">بازی‌ها</div><div class="blk">تاریخچه</div>' +
      (corner ? '<div style="position:absolute;left:10px;bottom:54px">' + ad + '</div>' : '') + (full ? ad : '') + '<div class="nav"></div>';
    return '<div class="' + (wide ? 'preview-desktop' : '') + '"><div class="phone' + (light ? ' light' : '') + '"><div class="screen">' + content + '</div></div><div class="hint" style="text-align:center">' + (wide ? 'تبلت / افقی' : (light ? 'سبک روشن' : 'سبک تیره')) + '</div></div>';
  }

  function render() {
    box.innerHTML = phone(false) + phone(true) + phone(false, true);
    // Only slots that accept this format can be ticked.
    var f = val('f-format');
    document.querySelectorAll('[data-formats]').forEach(function (l) {
      var ok = l.getAttribute('data-formats').split(',').indexOf(f) >= 0;
      l.style.opacity = ok ? '1' : '.4';
      l.querySelector('input').disabled = !ok;
    });
  }

  form.addEventListener('input', render);
  form.addEventListener('change', render);
  render();
})();
