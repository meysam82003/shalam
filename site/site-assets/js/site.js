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
})();
