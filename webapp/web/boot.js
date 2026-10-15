// Runs before the page is drawn: paints the loading page in the colours of the style chosen in the app (kept in
// local storage by host.js whenever the app saves them), so the first picture is never white or another colour.
(window.DH = window.DH || {}).applyLaunch = function () {
  try {
    var raw = localStorage.getItem('divan.launch');
    if (!raw) return;
    var o = JSON.parse(raw), v = o.look;
    var look = JSON.parse(v && v.length === 2 && typeof v[1] === 'string' ? v[1] : v);
    var ok = function (c) { return typeof c === 'string' && /^#[0-9A-Fa-f]{6}$/.test(c); };
    var r = document.documentElement.style;
    [['b1', '--lb1'], ['b2', '--lb2'], ['b3', '--lb3'], ['title', '--ltitle'], ['gold', '--lgold'], ['muted', '--lmuted'], ['dim', '--ldim']].forEach(function (p) {
      if (ok(look[p[0]])) r.setProperty(p[1], look[p[0]]);
    });
    if (ok(look.b1)) { var m = document.querySelector('meta[name=theme-color]'); if (m) m.setAttribute('content', look.b1); }
    if (look.light) document.documentElement.classList.add('launch-light');
  } catch (e) {}
};
DH.applyLaunch();
