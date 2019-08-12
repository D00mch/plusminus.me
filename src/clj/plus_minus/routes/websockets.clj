(ns plus-minus.routes.websockets
  (:require [clojure.tools.logging :as log]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [immutant.web.async :as async]
            [clojure.core.async :refer [>! <! >!! chan alts! go-loop]]
            [mount.core :as mount]
            [plus-minus.common.json :as parser]
            [plus-minus.multiplayer.contract :as contract :refer
             [->Reply ->Message map->Message]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.routes.multiplayer.reply :as room]
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
  (let [{id :id :as msg} (parser/read-json json)]
    (swap! id->channel assoc id ch)
    (println "pushed?"
     (topics/push! :msg (map->Message msg)))))

(def websocket-callbacks
  {:on-open connect!
   :on-close disconnect!
   :on-message on-message!})

(defn ws-handler [request]
  (async/as-channel request websocket-callbacks))

(def websocket-routes [["/ws" ws-handler]])

(mount/defstate game-subscription>
  "subscribe messages and replies processing"
  :start
  (do
    (game/listen!)
    (let [exit>    (chan)
          replies> (topics/tap! :reply (chan))]
      (go-loop []
        (let [[v ch] (alts! [exit> replies>])]
          (cond
            (= ch exit>) (log/info "exit game-subscription loop")
            v            (let [{:keys [reply-type id] :as reply} v]
                           (println "about to send reply" (into {} reply))
                           (when-let [ch (get @id->channel id)]
                             (println "ch for reply found, sent - "
                              (async/send! ch (parser/->json reply))))
                           (when (= reply-type :end)
                             (swap! id->channel dissoc id))
                           (recur)))))
      exit>))
  :stop  (do (game/close!)
             (topics/reset-state!)
             (>!! game-subscription> 0)))

(comment

  (topics/reset-state!)

  (topics/push! :msg (->Message :new "bob" 3))
  (topics/push! :msg (->Message :new "dumch" 3))

  )
