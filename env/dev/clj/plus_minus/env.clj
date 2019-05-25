(ns plus-minus.env
  (:require
    [selmer.parser :as parser]
    [clojure.tools.logging :as log]
    [plus-minus.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[plus-minus started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[plus-minus has shut down successfully]=-"))
   :middleware wrap-dev})
