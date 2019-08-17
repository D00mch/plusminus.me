(ns plus-minus.websockets
  (:require [cognitect.transit :as t]
            [plus-minus.multiplayer.contract :as contract :refer
             [->Message ->Reply]]
            [plus-minus.app-db :as db]))

(defonce ws-chan (atom nil))
(def json-reader (t/reader :json))
(def json-writer (t/writer :json))

(defn receive-transit-msg!
  [update-fn]
  (fn [msg]
    (update-fn
     (->> msg .-data (t/read json-reader)))))

(defn close!
  "returns true if there is a ws-chan to close"
  []
  (if-let [c @ws-chan]
    (do (.close c) true)
    false))

(defn send-transit-msg!
  [msg]
  (if @ws-chan
    (.send @ws-chan (t/write json-writer msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn make-websocket! [url receive-handler & [on-open]]
  (println "attempting to connect websocket")
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
      (set! (.-onclose chan) (fn [_] (reset! ws-chan nil)))
      (set! (.-onerror chan)
            (fn [_]
              (receive-handler (->Reply :error (db/get :identity) :unknown))))
      (set! (.-onopen chan)
            (fn [_]
              (reset! ws-chan chan)
              (on-open)
              (println "Websocket connection established with: " url))))
    (throw (js/Error. "Websocket connection failed!"))))

(defn ensure-websocket! [url receive-handler & [on-open]]
  (if-let [chan   @ws-chan]
    (let [state   (.-readyState chan)
          closed? (or (= state 2) (= state 3))]
      (when closed? (make-websocket! url receive-handler)))
    (make-websocket! url receive-handler on-open)))

(comment
  (do
    (def player "regeda")
    (def ws (let [ch (js/WebSocket. (str "ws://" (.-host js/location) "/ws"))]
              (set! (.-onmessage ch)
                    (receive-transit-msg! #(println player "received :" %)))
              (set! (.-onclose ch) (println player "closed"))
              ch)))

  (.close ws)

  (.send ws (t/write json-writer (into {} (->Message :new player 3))))
  (.send ws (t/write json-writer (into {} (->Message :move player 6))))
  (.send ws (t/write json-writer (into {} (->Message :give-up player nil))))
 )
