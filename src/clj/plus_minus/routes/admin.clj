(ns plus-minus.routes.admin
  (:require [plus-minus.middleware :as middleware]
            [plus-minus.layout :as layout]
            [ring.util.http-response :as response]))

(def ^:private maintenance (volatile! false))

(defn maintenance? [] @maintenance)

(defn- admin-page [request]
  (layout/render request "admin.html" {:turn (maintenance?)}))

(defn- switch-maintenance! [{params :params}]
  (vreset! maintenance (Boolean/parseBoolean (:turn params)))
  (response/found "/sudo/home"))

(defn admin-routes []
  ["/sudo"
   {:middleware [middleware/wrap-formats
                 (middleware/wrap-roles #{:admin})]}
   ["/home" {:get admin-page}]
   ["/maintenance" {:post switch-maintenance!}]])
