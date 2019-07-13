(ns plus-minus.routes.websockets
  (:require [plus-minus.routes.multiplayer.room :as room]
            [clojure.tools.logging :as log]
            [immutant.web.async :as async]
            [cognitect.transit :as t]
            [com.walmartlabs.cond-let :refer [cond-let]])
  (:import [java.io ByteArrayOutputStream]
           [java.util.concurrent Executors TimeUnit Future]))

(defonce id->channel (ref {}))

(defn connect! [channel]
  (log/info "channel open" channel))

(defn disconnect! [channel {:keys [code reason]}]
  (log/info "close code:" code "reason:" reason))

(defn- send! [channel data]
  (let [out (ByteArrayOutputStream.)
        w   (t/writer out :json)
        _   (t/write w data)
        ret (.toString out)]
    (.reset out)
    (async/send! channel ret)))

;; TODO: implement
;; may be add player1-id? to be able to get the game
(defn- on-time-elapsed [[type [result id] :as result]] ;; [:end [:disconnect id]]
  )

(defn on-message! [ch [type id data :as msg]]
  (dosync (alter id->channel assoc id ch))
  (let [[reply _ :as ret] (case type
                            :state   (room/state-request! id)
                            :move    (room/move! msg)
                            :give-up (room/give-up! msg))]
    (case reply
      :error (send! ch ret)
      :state (send ch
                   (-> ret (dissoc :turn-timer :player1-ready :player2-ready :status)))
      (let [game (-> @room/player->room (get id))
            id2  (room/player-turn-id game)
            ch2  (get @id->channel id2)]
        (when (= reply :end)
          (send! ch ret))
        (send! ch2 ret)))))

(def websocket-callbacks
  {:on-open connect!
   :on-close disconnect!
   :on-message on-message!})

(defn ws-handler [request]
  (async/as-channel request websocket-callbacks))

(def websocket-routes
  ["/ws" ws-handler])

(comment
  (def out (ByteArrayOutputStream. 4096))
  (def writer (t/writer out :json))
  (t/write writer {:state {:board {:row-count 1, :cells [0 1 2 3]}}})
  (t/write writer [:move 1])

  (.reset out)

  (notify-clients! nil (.toString out)))
