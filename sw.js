// Service worker: la app abre al instante y sin red muestra la última versión.
// Los datos (Supabase) nunca se guardan aquí: siempre van a la red.
const CACHE = 'eco-v7';
const SHELL = ['./', 'index.html', 'config.js', 'vendor/supabase.js', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE && k !== 'eco-share').map(k => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', e => {
  const req = e.request; const url = new URL(req.url);
  // Compartir una captura desde la galería de Android → se guarda un momento y la app la lee
  if (req.method === 'POST' && url.origin === location.origin && url.pathname.endsWith('/compartir')) {
    e.respondWith((async () => {
      try {
        const fd = await req.formData(); const files = fd.getAll('img').filter(f => f && f.size);
        const c = await caches.open('eco-share'); for (const k of await c.keys()) await c.delete(k);
        let i = 0; for (const f of files.slice(0, 6)) await c.put('./shared/' + (i++), new Response(f, { headers: { 'Content-Type': f.type || 'image/jpeg' } }));
      } catch (err) {}
      return Response.redirect('./#captura', 303);
    })());
    return;
  }
  if (req.method !== 'GET' || url.origin !== location.origin) return;
  if (url.pathname.includes('/onb/')) return; // vídeos: los sirve la red con caché HTTP (peticiones por rangos)
  const fresh = req.mode === 'navigate' || /\/(index\.html|config\.js)?$/.test(url.pathname) || url.pathname.endsWith('config.js');
  if (fresh) {
    e.respondWith(fetch(req).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return r; }).catch(() => caches.match(req).then(r => r || caches.match('index.html'))));
  } else {
    e.respondWith(caches.match(req).then(r => r || fetch(req).then(res => { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res; })));
  }
});
