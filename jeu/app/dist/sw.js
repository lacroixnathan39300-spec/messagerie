// Service worker : garde l'appli en cache pour qu'elle marche sans connexion.
const CACHE = "terre-vivante-v1";

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const liste = await (await fetch("cache.json")).json();
    await cache.addAll(liste);
    self.skipWaiting();
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", (event) => {
  event.respondWith((async () => {
    const trouve = await caches.match(event.request);
    if (trouve) return trouve;
    return fetch(event.request);
  })());
});
