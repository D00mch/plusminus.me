(ns plus-minus.middleware
  (:require
   [plus-minus.env :refer [defaults]]
   [cheshire.generate :as cheshire]
   [cognitect.transit :as transit]
   [clojure.tools.logging :as log]
   [plus-minus.layout :refer [error-page]]
   [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
   [plus-minus.middleware.formats :as formats]
   [muuntaja.middleware :refer [wrap-format wrap-params]]
   [plus-minus.config :refer [env]]
   [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
   [buddy.auth.backends.session :refer [session-backend]]
   [ring.util.http-response :as response]
   [clojure.set :as set]))

(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t (.getMessage t))
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
   handler
   {:error-response
    (error-page
     {:status 403
      :title "Invalid anti-forgery token"})}))

(defn wrap-formats [handler]
  (let [wrapped (-> handler wrap-params (wrap-format formats/instance))]
    (fn [request]
      ;; disable wrap-formats for websockets
      ;; since they're not compatible with this middleware
      ((if (:websocket? request) handler wrapped) request))))

(def wrap-restricted
  {:name :wrap-restricted
   :wrap (fn wrap-restricted [handler]
           (fn [req]
             (if (boolean (:identity req))
               (handler req)
               (response/unauthorized
                {:error "You are not authorized to perform that action."}))))})

(defn wrap-roles [needed-roles]
  {:name :wrap-roles
   :wrap (fn wrap-roles [handler]
           (fn [{{roles :roles} :session, :as req}]
             (if (set/subset? needed-roles roles)
               (handler req)
               (response/unauthorized
                {:error "You are not authorized for this url"}))))})

(defn wrap-auth [handler]
  (let [backend (session-backend)]
    (-> handler
        (wrap-authentication backend)
        (wrap-authorization backend))))

(defn wrap-cache [handler duration]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Cache-Control"]
                (str "max-age=" duration)))))

(defn wrap-base [handler]
  (-> ((:middleware defaults) handler)
      wrap-auth
      (wrap-cache 1200)
      (wrap-defaults
       (-> site-defaults
           (assoc-in [:security :anti-forgery] false)
           (assoc-in [:session :cookie-attrs :same-site] :lax)
           (assoc-in [:session :store]
                     (cookie-store {:key (:session-store-key env)}))))
      wrap-internal-error))
