(ns plus-minus.routes.websockets
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [cognitect.transit :as t]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [immutant.web.async :as async]
            [mount.core :as mount]
            [plus-minus.multiplayer.contract
             :as
             contract
             :refer
             [->Reply map->Message]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.routes.multiplayer.reply :as room]
            [plus-minus.routes.multiplayer.topics :as topics])
  (:import java.io.ByteArrayOutputStream
           [java.util.concurrent Executors Future TimeUnit]))

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
    #_(rx/publish topics/messages (map->Message msg))))

(def websocket-callbacks
  {:on-open connect!
   :on-close disconnect!
   :on-message on-message!})

(defn ws-handler [request]
  (async/as-channel request websocket-callbacks))

(def websocket-routes ["/ws" ws-handler])

;;************************* SUBSCRIPTION *************************

;; (defn- on-reply [{type :reply-type id :id :as reply}]
;;   (when     (= type :end)              (swap! id->channel dissoc id))
;;   (when-let [ch (get @id->channel id)] (send-json! ch reply)))

;; (defn- subscribe []
;;   #_(rx/subscribe room/replies on-reply #(log/error "on-error:" %)))

;; (mount/defstate reply-disposable
;;   "subscribes to replies"
;;   :start (subscribe)
;;   :stop (.dispose reply-disposable))
