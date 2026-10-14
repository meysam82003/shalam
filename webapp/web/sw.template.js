/* Divan Emtiaz — offline support: the app's files are cached on install and served from the cache; the server's
   API and payment pages always go to the network (they are never cached). */
const VERSION = '__VERSION__';
const CACHE = 'divan-play-' + VERSION;
const CORE = __CORE__;
const LAZY = __LAZY__;

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CORE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k.startsWith('divan-play-') && k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim())
    .then(() => caches.open(CACHE).then((c) => Promise.all(LAZY.map((u) => c.add(u).catch(() => {}))))));
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin || !url.pathname.startsWith(new URL('./', self.location).pathname)) return;
  e.respondWith(caches.open(CACHE).then((c) => c.match(e.request, { ignoreSearch: true }).then((hit) => hit || fetch(e.request).then((r) => {
    if (r.ok && r.type === 'basic') c.put(e.request, r.clone());
    return r;
  }).catch(() => (e.request.mode === 'navigate' ? c.match('./') : Response.error())))));
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const id = e.notification.data && e.notification.data.id;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((list) => {
    const client = list[0];
    if (client) { client.postMessage({ type: 'notification-click', id: id }); return client.focus(); }
    return self.clients.openWindow('./');
  }));
});
