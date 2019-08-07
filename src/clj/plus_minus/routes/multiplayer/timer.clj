(ns plus-minus.routes.multiplayer.timer
  (:require [plus-minus.multiplayer.contract :refer [->Message]]
            [clojure.core.async :refer [go-loop chan timeout alts! >! pipe close!]]))

(def ^:const ping-ms 3000)
(def ^:const turn-ms 30000)

(defn millis [] (System/currentTimeMillis))

(defn updated [game] (assoc game :updated (millis)))

(defn elapsed? [{old :updated :as game}]
  (> (- (millis) old) turn-ms))

(defn turn-ends-after [{old :updated :as game}]
  (let [passed (- (millis) old)]
    (if (> passed turn-ms)
      0
      (- turn-ms passed))))

(defn pipe-with-move-timer!
  "takes to> & from> channels and pipes messages with xf xform;
  every *ping-ms* millis sends {:msg-type :ping}"
  [to> xf from> close?]
  (let [bus> (chan 1 xf)]
    (go-loop []
      (let [timer>   (timeout ping-ms)
            [msg c>] (alts! [from> timer>])]
        (cond (= c> timer>) (do (>! bus> (->Message :ping nil nil))
                                (recur))
              msg           (do (>! bus> msg)
                                (recur))
              :else         (when close? (close! bus>)))))
    (pipe bus> to>)))
