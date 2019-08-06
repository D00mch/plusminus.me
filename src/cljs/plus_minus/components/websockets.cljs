(ns plus-minus.components.websockets
  (:require [cognitect.transit :as t]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Message]]))

(defonce ws-id->chan (atom {}))
(def json-reader (t/reader :json))
(def json-writer (t/writer :json))

(defn receive-transit-msg!
  [update-fn]
  (fn [msg]
    (update-fn
     (->> msg .-data (t/read json-reader)))))

(defn send-transit-msg!
  [{id :id :as msg}]
  (if-let [chan (get @ws-id->chan id)]
    (.send chan (t/write json-writer msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn make-websocket! [url receive-handler id]
  (println "attempting to connect websocket")
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
      (swap! ws-id->chan assoc id chan)
      (println "Websocket connection established with: " url))
    (throw (js/Error. "Websocket connection failed!"))))

(comment
  (make-websocket! (str "ws://" (.-host js/location) "/ws")
                   #(println "received " %)
                   "dumch")
  (make-websocket! (str "ws://" (.-host js/location) "/ws")
                   #(println "received " %)
                   "bob")

  (send-transit-msg! (into {} (->Message :new "dumch" 3)))

  (send-transit-msg! (into {} (->Message :new "bob" 3)))
  )
