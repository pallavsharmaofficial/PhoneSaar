// PhoneSaar site: language toggle, scroll reveal, hero tilt. No dependencies.
(function () {
  var root = document.documentElement;
  var KEY = 'phonesaar-lang';
  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Language toggle: English is the markup, data-hi holds the Hindi copy.
  var nodes = Array.prototype.slice.call(document.querySelectorAll('[data-hi]'));
  nodes.forEach(function (el) { el.setAttribute('data-en', el.innerHTML); });
  var buttons = Array.prototype.slice.call(document.querySelectorAll('.lang-toggle button'));

  function setLang(lang, save) {
    nodes.forEach(function (el) { el.innerHTML = el.getAttribute(lang === 'hi' ? 'data-hi' : 'data-en'); });
    root.lang = lang;
    buttons.forEach(function (b) { b.setAttribute('aria-pressed', String(b.getAttribute('data-lang') === lang)); });
    if (save) { try { localStorage.setItem(KEY, lang); } catch (e) { /* storage unavailable */ } }
  }
  buttons.forEach(function (b) { b.addEventListener('click', function () { setLang(b.getAttribute('data-lang'), true); }); });
  var saved = null;
  try { saved = localStorage.getItem(KEY); } catch (e) { /* storage unavailable */ }
  if (saved === 'hi') setLang('hi', false);

  // Scroll reveal.
  var items = Array.prototype.slice.call(document.querySelectorAll('.reveal'));
  if (reduce || !('IntersectionObserver' in window)) {
    items.forEach(function (el) { el.classList.add('in'); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) { if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); } });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
    items.forEach(function (el) { io.observe(el); });
  }

  // Hero phone follows the pointer a little.
  var stage = document.getElementById('stage');
  if (stage && !reduce && window.matchMedia('(hover: hover)').matches) {
    stage.addEventListener('pointermove', function (e) {
      var r = stage.getBoundingClientRect();
      var x = (e.clientX - r.left) / r.width - 0.5;
      var y = (e.clientY - r.top) / r.height - 0.5;
      stage.style.setProperty('--ry', (-14 + x * 16).toFixed(1) + 'deg');
      stage.style.setProperty('--rx', (5 - y * 12).toFixed(1) + 'deg');
    });
    stage.addEventListener('pointerleave', function () { stage.style.removeProperty('--ry'); stage.style.removeProperty('--rx'); });
  }
})();
