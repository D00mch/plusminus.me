(ns plus-minus.routes.home
  (:require
    [plus-minus.layout :as layout]
    [plus-minus.db.core :as db]
    [clojure.java.io :as io]
    [plus-minus.middleware :as middleware]
    [ring.util.http-response :as response]
    [page-renderer.api :as pr]))

(defn service-worker [request]
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

(defn home-page [request]
  (layout/render request "home.html"))

(defn privacy-policy [request]
  (layout/render request "privacy-policy.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/app" {:get home-page}]
   ["/service-worker.js" {:get service-worker}]
   ["/privacy-policy" {:get privacy-policy}]
   ["/docs" {:get (fn [_]
                    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
                        (response/header "Content-Type" "text/plain; charset=utf-8")))}]])

