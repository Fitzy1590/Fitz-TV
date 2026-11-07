// Service worker: app shell offline, headshots cached forever, API network-first.
const SHELL = "shell-v2";
const IMAGES = "images-v1";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(["/", "/manifest.webmanifest", "/icon.svg"])));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => ![SHELL, IMAGES].includes(k)).map((k) => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET") return;
  if (url.hostname === "sleepercdn.com") {
    e.respondWith(
      caches.open(IMAGES).then(async (c) => {
        const hit = await c.match(e.request);
        if (hit) return hit;
        const res = await fetch(e.request);
        if (res.ok || res.type === "opaque") c.put(e.request, res.clone());
        return res;
      }),
    );
    return;
  }
  if (url.origin !== location.origin || url.pathname.startsWith("/api/")) return;
  if (url.pathname.startsWith("/assets/")) {
    e.respondWith(caches.open(SHELL).then(async (c) => (await c.match(e.request)) ?? fetch(e.request).then((r) => (c.put(e.request, r.clone()), r))));
    return;
  }
  if (e.request.mode === "navigate") {
    e.respondWith(fetch(e.request).catch(() => caches.match("/")));
  }
});

// Push notifications (lineup alerts from the hub's server).
self.addEventListener("push", (e) => {
  let data = { title: "Fitz Fantasy", body: "", url: "/" };
  try {
    data = { ...data, ...e.data.json() };
  } catch {
    /* plain text */
  }
  e.waitUntil(self.registration.showNotification(data.title, { body: data.body, icon: "/icon-192.png", badge: "/icon-192.png", data: { url: data.url } }));
});

self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const url = e.notification.data?.url ?? "/";
  e.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((list) => {
      const open = list.find((c) => "focus" in c);
      if (open) return open.navigate(url).then((c) => c?.focus());
      return self.clients.openWindow(url);
    }),
  );
});
