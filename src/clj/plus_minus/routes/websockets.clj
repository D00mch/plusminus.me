(ns plus-minus.routes.websockets
  (:require [plus-minus.routes.multiplayer.topics :as topics
             :refer [map->Message ->Reply]]
            [plus-minus.routes.multiplayer.room :as room]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.routes.multiplayer.room :as room]
            [clojure.tools.logging :as log]
            [immutant.web.async :as async]
            [cognitect.transit :as t]
            [clojure.java.io :as io]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [beicon.core :as rx]
            [mount.core :as mount])
  (:import [java.io ByteArrayOutputStream]
           [java.util.concurrent Executors TimeUnit Future]))

;; https://gist.github.com/mattly/217eb6f26cb5d728a6cc88b4d6b926bb

(defn- ->stream [input]
  (cond (string? input) (io/input-stream (.getBytes input))
        :default input))

(defn- read-json [input]
  (with-open [ins (->stream input)]
    (-> ins (t/reader :json) t/read)))

(defn- send-json! [channel data]
  (let [out (ByteArrayOutputStream.)
        w   (t/writer out :json)
        _   (t/write w data)
        ret (.toString out)]
    (.reset out)
    (async/send! channel ret)))

;;************************* SOCKET *************************

(defonce id->channel (atom {}))

(defn- connect! [channel] (log/info "channel open" channel))
(defn- disconnect! [channel {:keys [code reason]}]
  (log/info "close code:" code "reason:" reason))

(defn- on-message! [ch json]
  (log/info "msg:" json)
  (let [{id :id :as msg} (read-json json)]
    (swap! id->channel assoc id ch)
    (let [valid (topics/publish :msg (map->Message msg))]
      (when-not valid
        (topics/publish :reply (->Reply :error id :invalid-msg))))))

(def websocket-callbacks
  {:on-open connect!
   :on-close disconnect!
   :on-message on-message!})

(defn ws-handler [request]
  (async/as-channel request websocket-callbacks))

(def websocket-routes ["/ws" ws-handler])

;;************************* SUBSCRIPTION *************************

(defn- on-reply [{type :reply-type id :id :as reply}]
  (when     (= type :end)              (swap! id->channel dissoc id))
  (when-let [ch (get @id->channel id)] (send-json! ch reply)))

(defn- subscribe []
  (rx/subscribe (topics/consume :reply) on-reply #(log/error "on-error:" %)))

(mount/defstate matcher-disposable
  "subscribes matcher to new game requests"
  :start (matcher/subscribe)
  :stop  (.dispose matcher-disposable))

(mount/defstate room-disposable
  "subscribes room to new games and user's messages"
  :start (room/subscribe)
  :stop  (.dispose room-disposable))

(mount/defstate reply-disposable
  "subscribes to replies"
  :start (subscribe)
  :stop (.dispose reply-disposable))
