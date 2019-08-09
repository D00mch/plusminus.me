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

;; TODO: remove below, tmp for testing
(defonce ws-id->chan (atom {}))

(defn receive-transit-msg2!
  [update-fn]
  (fn [msg]
    (update-fn
     (->> msg .-data (t/read json-reader)))))

(defn send-transit-msg2!
  [{id :id :as msg}]
  (if-let [chan (get @ws-id->chan id)]
    (.send chan (t/write json-writer msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn make-websocket2! [url receive-handler id]
  (println "attempting to connect websocket")
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
      (set! (.-onclose chan) (swap! ws-id->chan dissoc id))
      (swap! ws-id->chan assoc id chan)
      (println "Websocket connection established with: " url))
    (throw (js/Error. "Websocket connection failed!"))))

(comment
  (make-websocket2! (str "ws://" (.-host js/location) "/ws")
                   #(println "received " %)
                   "bob")

  (send-transit-msg2! (into {} (->Message :new "bob" 3)))
  (send-transit-msg2! (into {} (->Message :move "bob" 6)))

  (send-transit-msg2! (into {} (->Message :give-up "bob" nil)))
 )
