(ns plus-minus.websockets
  (:require [cognitect.transit :as t]
            [plus-minus.multiplayer.contract :as contract :refer
             [->Message ->Reply]]
            [plus-minus.app-db :as db]
            [plus-minus.components.common :as c]))

(defonce ws-chan (atom nil))
(def json-reader (t/reader :json))
(def json-writer (t/writer :json))
(defn url []
  ;; it's not def because it can be initialized before setting :dev property
  (str (if (db/get :dev?) "ws" "wss") "://" (.-host js/location) "/ws"))

(defn- receive-transit-msg!
  [update-fn]
  (fn [msg]
    (update-fn
     (->> msg .-data (t/read json-reader)))))

(defn- send-transit-msg!
  [msg]
  (if @ws-chan
    (.send @ws-chan (t/write json-writer msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn message [type & [data]]
  (into {} (->Message type (db/get :identity) data)))

(defn push-message! [type & [data]]
  (send-transit-msg! (message type data)))

(defn close!
  "returns true if there is a ws-chan to close"
  []
  (if-let [c @ws-chan]
    (do
      ;; try drop searching to prevent game with leaver
      (push-message! :drop (db/get :online-row))
      (.close c)
      true)
    false))

(defn make-websocket! [url receive-handler & [on-open]]
  (println "attempting to connect websocket")
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
      (set! (.-onclose chan)
            (fn [_]
              (c/show-snack! "multiplayer disconnected!")
              (db/put! :websocket-connected false)
              (reset! ws-chan nil)))
      (set! (.-onerror chan)
            (fn [_]
              (receive-handler (->Reply :error (db/get :identity) :unknown))))
      (set! (.-onopen chan)
            (fn [_]
              (c/show-snack! "multiplayer connection established!")
              (db/put! :websocket-connected true)
              (reset! ws-chan chan)
              (on-open)
              (println "Websocket connection established with: " url))))
    (throw (js/Error. "Websocket connection failed!"))))

(defn ensure-websocket! [receive-handler & [on-open]]
  (if-let [chan   @ws-chan]
    (let [state   (.-readyState chan)
          closed? (or (= state 2) (= state 3))]
      (when closed? (make-websocket! (url) receive-handler)))
    (make-websocket! (url) receive-handler on-open)))

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
