(ns plus-minus.worker)

(enable-console-print!)
(print "I'm a worker!")

(def cache-name "plus-minus-page")
(def files-to-cache ["/"
                     "app.js"
                     "/html/home.html"
                     "/html/error.html"
                     "/css/screen.css"
                     "/img/warning_clojure.png"
                     "/img/google.png"])

(defn- install-service-worker [e]
  (js/console.log "[ServiceWorker] Installing")
  (-> js/caches
      (.open cache-name)
      (.then (fn [cache]
               (js/console.log "[ServiceWorker] Caching shell")
               (.addAll cache (clj->js files-to-cache))))
      (.then (fn []
               (js/console.log "[ServiceWorker] Successfully Installed")))))

(defn- fetch-cached [request]
  (-> js/caches
      (.match request)
      (.then (fn [response]
               (or response (js/fetch request))))))

(defn- purge-old-caches [e]
  (-> js/caches
      .keys
      (.then (fn [keys]
               (->> keys
                    (map #(when-not (contains? #{cache-name} %)
                            (.delete js/caches %)))
                    clj->js
                    js/Promise.all)))))

(.addEventListener js/self "install" #(.waitUntil % (install-service-worker %)))
(.addEventListener js/self "activate" #(.waitUntil % (purge-old-caches %)))
(.addEventListener js/self "fetch" #(.respondWith % (fetch-cached "/shell.html")))

(defn on-js-reload []
  (print "on-js-reload called!"))
