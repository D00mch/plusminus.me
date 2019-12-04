(ns plus-minus.routes.pwa
  (:require [page-renderer.api :as pr]
            [plus-minus.middleware :as middleware]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]))

(defn service-worker [_]
  (pr/respond-service-worker
   {:script "/js/app.js"
    :sw-default-url "/app"
    :sw-add-assets
    ["/css/screen.css",
     "/img/warning_clojure.png",
     "/img/google.png",
     "/img/icons/icon-72x72.png",
     "/img/icons/icon-96x96.png",
     "/img/icons/icon-128x128.png",
     "/img/icons/icon-144x144.png",
     "/img/icons/icon-152x152.png",
     "/img/icons/icon-192x192.png",
     "/img/icons/icon-384x384.png",
     "/img/icons/icon-512x512.png",
     "/sound/time-warn.flac",
     "/sound/ring-bell.wav"]}))

(defn pwa-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/service-worker.js" {:get service-worker}]
   ["/.well-known/assetlinks.json"
    {:get
     (fn [_]
       (-> (response/ok (-> "public/json/assetlinks.json" io/resource slurp))
           (response/header "Content-Type" "application/json; charset=utf-8")))}]])
