(ns plus-minus.routes.services
  (:require
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.ring.coercion :as coercion]
   [reitit.coercion.spec :as spec-coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]
   [plus-minus.middleware :as middleware]
   [plus-minus.middleware.formats :as formats]
   [plus-minus.middleware.exception :as exception]
   [plus-minus.routes.services.auth :as auth]
   [plus-minus.game.state :as game-state]
   [plus-minus.routes.services.state :as state]
   [ring.util.http-response :as response]
   [clojure.java.io :as io]
   [plus-minus.middleware :as middleware]))

(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "my-api"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
            {:url "/api/swagger.json"
             :config {:validator-url nil}})}]]

   ["/ping"
    {:get (constantly (response/ok {:message "pong"}))}]

   ["/register"
    {:post {:summary "register a user, providing id and password"
            :parameters {:body {:id string?
                                :pass string?
                                :pass-confirm string?}}
            :responses {200 {:body {:result keyword?}}}
            :handler (fn [{{user :body} :parameters :as req}]
                       (auth/register! req user))}}]

   ["/login"
    {:post {:summary "login a user and create a session"
            :parameters {:header {:authorization string?}}
            :responses {200 {:body {:result keyword?}}}
            :handler (fn [{{{h :authorization} :header} :parameters :as req}]
                       (auth/login! req h))}}]

   ["/logout"
    {:post {:summary "remove user session"
            :responses {200 {:body {:result keyword?}}}
            :handler (fn [req] (auth/logout! req))}}]

   ["/state"
    {:get {:summary "get last game state or new state"
           :parameters {:query {:id string?}}
           :responses {200 {:body {:result ::game-state/state}}}
           :handler (fn [{{{id :id} :query} :parameters}]
                      (state/get-state id))}

     :put {:summary "upsert game state"
           :parameters {:body {:id    string?
                               :state ::game-state/state}}
           :responses {200 {:body {:result keyword?}}}
           :handler (fn [{{{id :id, s :state} :body} :parameters}]
                      (state/upsert-state id s))}}]

   ["/restricted"
    {:swagger {:tags ["restricted"]}
     :middleware [middleware/wrap-restricted]}

    ["/delete-account"
     {:post {:summary "delete user profile from database"
             :responses {200 {:body {:result keyword?}}}
             :handler (fn [{{id :identity} :session}] (auth/delete-account! id))}}]]

   ["/math"
    {:swagger {:tags ["math"]}}

    ["/plus"
     {:get {:summary "plus with spec query parameters"
            :parameters {:query {:x int?, :y int?}}
            :responses {200 {:body {:total pos-int?}}}
            :handler (fn [{{{:keys [x y]} :query} :parameters}]
                       {:status 200
                        :body {:total (+ x y)}})}
      :post {:summary "plus with spec body parameters"
             :parameters {:body {:x int?, :y int?}}
             :responses {200 {:body {:total pos-int?}}}
             :handler (fn [{{{:keys [x y]} :body} :parameters}]
                        {:status 200
                         :body {:total (+ x y)}})}}]]])
