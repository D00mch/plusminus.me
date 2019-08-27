(ns plus-minus.figwheel
  (:require [figwheel-sidecar.repl-api :as ra]))

(defn start-fw []
  (ra/start-figwheel! "app" "dev-worker"))

(defn stop-fw []
  (ra/stop-figwheel!))

(defn cljs []
  (ra/cljs-repl "app"))

(defn cljs-sw []
  (ra/cljs-repl "dev-worker"))
