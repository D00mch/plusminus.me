(ns plus-minus.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[plus-minus started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[plus-minus has shut down successfully]=-"))
   :middleware identity})
