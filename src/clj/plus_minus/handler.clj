(ns plus-minus.handler
  (:require
    [plus-minus.middleware :as middleware]
    [plus-minus.layout :refer [error-page]]
    [plus-minus.routes.home :refer [home-routes]]
    [plus-minus.routes.services :refer [service-routes]]
    [plus-minus.routes.oauth :refer [oauth-routes]]
    [plus-minus.routes.websockets :refer [websocket-routes] :as ws]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring :as ring]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.webjars :refer [wrap-webjars]]
    [plus-minus.env :refer [defaults]]
    [mount.core :as mount]))

(mount/defstate init-app
  :start ((or (:init defaults) (fn [])))
  :stop  ((or (:stop defaults) (fn []))))

(mount/defstate app
  :start
  (middleware/wrap-base
    (ring/ring-handler
      (ring/router
        [(home-routes)
         (websocket-routes)
         (service-routes)
         (oauth-routes)])
      (ring/routes
        (swagger-ui/create-swagger-ui-handler
          {:path   "/swagger-ui"
           :url    "/api/swagger.json"
           :config {:validator-url nil}})
        (ring/create-resource-handler
          {:path "/"})
        (wrap-content-type
          (wrap-webjars (constantly nil)))
        (ring/create-default-handler
          {:not-found
           (constantly (error-page {:status 404, :title "404 - Page not found"}))
           :method-not-allowed
           (constantly (error-page {:status 405, :title "405 - Not allowed"}))
           :not-acceptable
           (constantly (error-page {:status 406, :title "406 - Not acceptable"}))})))))
