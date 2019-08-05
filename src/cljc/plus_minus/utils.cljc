(ns plus-minus.utils
  (:require [clojure.core.async :as async
             :refer [chan >! <! go-loop close!]]))

(defonce cores
  (+ 2 (. (Runtime/getRuntime) availableProcessors)))

(defn ex-chain [^Exception e]
  (take-while some? (iterate ex-cause e)))

(defn pipe! [to> xf from> close?]
  (let [bus> (chan 1 xf)]
    (go-loop [msg (<! from>)]
      (if msg
        (do (>! bus> msg)
            (recur (<! from>)))
        (when close? (async/close! bus>))))
    (async/pipe bus> to>)))
