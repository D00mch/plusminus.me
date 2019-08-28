(ns plus-minus.routes.multiplayer.connection
  (:require [clojure.core.async :refer [<! chan go-loop close!]]
            [mount.core :as mount]
            [plus-minus.multiplayer.contract :as contract :refer
             [->Message map->Message]]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.routes.multiplayer.game :as game]))

(defmulti on-reply :id)

(defn message [msg]
  (topics/push! :msg (map->Message msg)))

(mount/defstate game-subscription>
  "subscribe messages and replies processing"
  :start (do
           (game/listen!)
           (let [replies> (topics/tap! :reply (chan))]
             (go-loop []
               (when-let [{:keys [reply-type id] :as reply} (<! replies>)]
                 (on-reply reply)
                 (recur)))
             replies>))
  :stop  (do (game/close!)
             (topics/reset-state!)
             (close! game-subscription>)))
