(ns plus-minus.routes.admin
  (:require [plus-minus.middleware :as middleware]
            [plus-minus.layout :as layout]
            [ring.util.http-response :as response]
            [mount.core :as mount]))

(def ^:private maintanance (volatile! false))

(defn maintanance? [] @maintanance)

(defn- admin-page [request]
  (layout/render request "admin.html" {:turn (maintanance?)}))

(defn- switch-maintanence! [{params :params, {id :identity} :session}]
  (vreset! maintanance (Boolean/parseBoolean (:turn params)))
  (response/found "/sudo/home"))

(defn admin-routes []
  ["/sudo"
   {:middleware [middleware/wrap-formats
                 (middleware/wrap-roles #{:admin})]}
   ["/home" {:get admin-page}]
   ["/maintanence" {:post switch-maintanence!}]])
