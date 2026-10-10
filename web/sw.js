// Rexi Rise service worker
// Pages are fetched network-first so new deploys reach phones right away;
// everything else is cache-first so the app still opens offline.
const CACHE = 'rexi-rise-v52';
const SOUND_IDS = ["marimba", "rise", "harp", "chimes", "bells", "sunny", "drums", "bellclock", "siren", "church", "organ", "birds", "carefree", "lifeofriley", "monkeys", "happyalley", "dailybeetle", "cipher", "motivator", "beachfront"];
const ASSETS = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png', './icon-maskable-512.png', './fonts/Roboto.ttf', ...SOUND_IDS.map(id=>'./sounds/'+id+'.mp3'), ...['study_start','break_suggested','break_start','break_end','goal_reached','study_complete','bible_nlt'].map(event=>'./voices/'+event+'.mp3')];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  // Only this site's own files. Never cache sign-in or backup requests to other servers.
  if (new URL(req.url).origin !== self.location.origin) return;
  const isPage = req.mode === 'navigate' || (req.headers.get('accept') || '').includes('text/html');

  if (isPage) {
    e.respondWith(
      fetch(req)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put('./index.html', copy)).catch(() => {});
          return res;
        })
        .catch(() => caches.match('./index.html'))
    );
    return;
  }

  e.respondWith(
    caches.match(req).then(hit => hit || fetch(req).then(res => {
      if (res.ok || res.type === 'opaque') {
        const copy = res.clone();
        caches.open(CACHE).then(c => c.put(req, copy)).catch(() => {});
      }
      return res;
    }))
  );
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
      for (const c of list) {
        c.postMessage('stop-alarm');
        if ('focus' in c) return c.focus();
      }
      if (self.clients.openWindow) return self.clients.openWindow('./index.html');
    })
  );
});
