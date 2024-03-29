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
   [plus-minus.routes.services.statistics :as statistics]
   [ring.util.http-response :as response]
   [plus-minus.game.board :as b]
   [plus-minus.multiplayer.contract :as contract]
   [plus-minus.validation :as validation]
   [plus-minus.game.progress :as p]))

;; http://localhost:3000/swagger-ui/index.html#/

(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api}
    :middleware [parameters/parameters-middleware ;; query-params & form-params
                 muuntaja/format-negotiate-middleware
                 muuntaja/format-response-middleware
                 exception/exception-middleware
                 muuntaja/format-request-middleware ;; decoding request body
                 coercion/coerce-response-middleware
                 coercion/coerce-request-middleware
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
            :parameters {:body {:id ::validation/id
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

   ["/game"
    {:swagger {:tags ["game"]}}

    ["/state"
     {:get {:summary "get last game state or new state"
            :parameters {:query {:id ::validation/id}}
            :responses {200 {:body {:state ::game-state/state}}}
            :handler (fn [{{{id :id} :query} :parameters}]
                       (state/get-state id))}

      :put {:summary "upsert game state"
            :parameters {:body {:id ::validation/id
                                :state ::game-state/state}}
            :responses {200 {:body {:result keyword?}}}
            :handler (fn [{{{id :id, s :state} :body} :parameters}]
                       (state/upsert-state id s))}}]

    ["/move"
     {:put {:summary "make move and update current game-state"
            :parameters {:query {:id   ::validation/id
                                 :move ::b/index}}
            :responses {200 {:body {:result keyword?}}}
            :handler (fn [{{{id :id, mv :move} :query} :parameters}]
                       (state/move id mv))}}]

    ["/end"
     {:put {:summary "update user game statistics"
            :parameters {:body ::state/result-state}
            :responses {200 {:body {:statistics ::p/statistics}}}
            :handler (fn [{{{:keys [id state usr-hrz give-up]} :body} :parameters}]
                       (state/game-end-resp id state usr-hrz give-up))}}]

    ["/ends"
     {:put {:summary "update user game statistics"
            :parameters {:body ::state/result-states}
            :responses {200 {:body {:statistics ::p/statistics}}}
            :handler (fn [{{states :body} :parameters}]
                       (state/games-end-resp states))}}]

    ["/statistics"
     {:get {:summary "get user game statistics"
            :parameters {:query {:id ::validation/id}}
            :responses {200 {:body {:statistics ::p/statistics}}}
            :handler (fn [{{{id :id} :query} :parameters}]
                       (state/get-stats-resp id))}}]
    ]

   ["/online"
    {:swagger {:tags ["online-game"]}}

    ["/statistics"
     {:get {:summary "get all players statistiscs"
            :responses {200 {:body
                             {:data [{:id ::validation/id
                                      :iq int?
                                      :statistics ::contract/statistics}]}}}
            :handler (fn [{{id :identity} :session}]
                       (statistics/get-all-online-stats id))}}]]

   ["/restricted"
    {:swagger {:tags ["restricted"]}
     :middleware [middleware/wrap-restricted]}

    ["/change-pass"
     {:post {:summary "change user's password"
             :parameters {:body {:pass string?}}
             :responses {200 {:body {:result keyword?}}}
             :handler (fn [{{id :identity :as s} :session
                            {{p :pass} :body} :parameters :as req}]
                        (clojure.pprint/pprint {:session s})
                        (auth/change-pass! id p))}}]

    ["/delete-account"
     {:post {:summary "delete user profile from database"
             :responses {200 {:body {:result keyword?}}}
             :handler (fn [{{id :identity} :session}] (auth/delete-account! id))}}]

    ["/user-stats"
     {:get {:summary "get user online-game statistics"
            :responses {200 {:body {:statistics ::contract/statistics}}}
            :handler (fn [{{id :identity} :session}]
                       (statistics/get-stats id))}}]]
   ])
