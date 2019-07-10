(ns plus-minus.routes.websockets
  (:require [clojure.tools.logging :as log]
            [immutant.web.async :as async]
            [cognitect.transit :as t])
  (:import [java.io ByteArrayOutputStream]
           [java.util.concurrent Executors TimeUnit Future]))

(defonce channels (atom #{}))

#_(org.projectodd.wunderboss.web.undertow.async.websocket.UndertowWebsocketChannel. "")

(defn connect! [channel]
  (log/info "channel open")
  (swap! channels conj channel))

(defn disconnect! [channel {:keys [code reason]}]
  (log/info "close code:" code "reason:" reason)
  (swap! channels #(remove #{channel} %)))

(defn notify-clients! [channel msg]
  (println "thread name: " (.getName (Thread/currentThread)))
  (println "client from chan" channel "sends" msg)
  (doseq [channel @channels]
    (async/send! channel msg)))

(def websocket-callbacks
  {:on-open connect!
   :on-close disconnect!
   :on-message notify-clients!})

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
