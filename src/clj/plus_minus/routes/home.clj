(ns plus-minus.routes.home
  (:require
    [plus-minus.layout :as layout]
    [ring.util.response :refer [redirect]]
    [plus-minus.middleware :as middleware]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn privacy-policy [request]
  (layout/render request "privacy-policy.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get (fn [_] (redirect "/app"))}]
   ["/app" {:get home-page}]
   ["/privacy-policy" {:get privacy-policy}]])
