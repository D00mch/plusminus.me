(ns plus-minus.routes.websockets
  (:require [clojure.tools.logging :as log]
            [immutant.web.async :as async]
            [clojure.core.async :refer [<! chan go-loop close!]]
            [mount.core :as mount]
            [plus-minus.common.json :as parser]
            [plus-minus.multiplayer.contract :as contract :refer
             [->Message map->Message]]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.routes.multiplayer.game :as game])
  (:import java.io.ByteArrayOutputStream
           [java.util.concurrent Executors Future TimeUnit]))

;; https://gist.github.com/mattly/217eb6f26cb5d728a6cc88b4d6b926bb

(defonce id->channel (atom {}))

(defn- connect! [channel] (log/info "channel open" channel))
(defn- disconnect! [channel {:keys [code reason]}]
  (log/info "close code:" code "reason:" reason))

(defn- on-message! [ch json]
  (log/info "msg:" json)
  (let [{id :id :as msg} (parser/read-json json)
        _                (swap! id->channel assoc id ch)
        pushed           (topics/push! :msg (map->Message msg))]
    (log/debug "pushed?" pushed)))

(def websocket-callbacks
  {:on-open connect!
   :on-close disconnect!
   :on-message on-message!})

(defn ws-handler [request]
  (async/as-channel request websocket-callbacks))

(defn websocket-routes []
  [["/ws" ws-handler]
   ["/wss" ws-handler]])

(mount/defstate game-subscription>
  "subscribe messages and replies processing"
  :start
  (do
    (game/listen!)
    (let [replies> (topics/tap! :reply (chan))]
      (go-loop []
        (when-let [{:keys [reply-type id] :as reply} (<! replies>)]
          (if-let [ch (get @id->channel id)]
            (let [sent (async/send! ch (parser/->json reply))]
              (log/debug "ch for reply found, sent: " sent))
            (log/debug "can't find channel for reply" (into {} reply)))
          (when (= reply-type :end)
            (swap! id->channel dissoc id))
          (recur)))
      replies>))
  :stop  (do (game/close!)
             (topics/reset-state!)
             (close! game-subscription>)))

(comment

  (topics/reset-state!)

  (topics/push! :msg (->Message :new "bob" 3))
  (topics/push! :msg (->Message :new "dumch" 3))

  )
