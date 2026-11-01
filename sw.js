const V='pulsify-v9';const CORE=['./','index.html','manifest.webmanifest','icons/icon-192.png','icons/icon-512.png','media/car.jpg','media/planet-near.jpg','media/planet-loop.mp4',
'https://cdn.jsdelivr.net/npm/three@0.147.0/build/three.min.js','https://cdn.jsdelivr.net/npm/three@0.147.0/examples/js/environments/RoomEnvironment.js'];
self.addEventListener('install',e=>{e.waitUntil(caches.open(V).then(c=>Promise.all(CORE.map(u=>c.add(u).catch(()=>{})))).then(()=>self.skipWaiting()))});
self.addEventListener('activate',e=>{e.waitUntil(caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==V).map(k=>caches.delete(k)))).then(()=>self.clients.claim()))});
self.addEventListener('fetch',e=>{const r=e.request;if(r.method!=='GET'||r.headers.get('range'))return;const u=new URL(r.url);
  if(u.pathname.endsWith('/')||u.pathname.endsWith('index.html')){e.respondWith(fetch(r).then(res=>{const c=res.clone();caches.open(V).then(x=>x.put(r,c));return res}).catch(()=>caches.match(r)));return}
  e.respondWith(caches.match(r).then(hit=>hit||fetch(r).then(res=>{if(res.ok||res.type==='opaque'){const c=res.clone();caches.open(V).then(x=>x.put(r,c))}return res})))});
