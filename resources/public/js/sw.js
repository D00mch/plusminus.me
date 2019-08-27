var cacheName = 'plusminuscache'; // change name to update cache in browser
var filesToCache = [
    '/',
    'app.js',
    '/html/home.html',
    '/html/error.html',
    '/css/screen.css',
    '/img/warning_clojure.png',
    '/img/google.png'
];
self.addEventListener('install', function(e) {
    console.log('[ServiceWorker] Install');
    e.waitUntil(
        caches.open(cacheName).then(function(cache) {
            console.log('[ServiceWorker] Caching app shell');
            return cache.addAll(filesToCache);
        })
    );
});
self.addEventListener('activate',  event => {
    event.waitUntil(self.clients.claim());
});
self.addEventListener('fetch', event => {
    event.respondWith(
        caches.match(event.request, {ignoreSearch:true}).then(response => {
            return response || fetch(event.request);
        })
    );
});
