(ns plus-minus.common.async
  (:require [clojure.core.async :as async
             :refer [chan >! <! go-loop close!]]))

(defonce cores
  (+ 2 (. (Runtime/getRuntime) availableProcessors)))

(defn pipe! [to> xf from> close?]
  (let [bus> (chan 1 xf)]
    (go-loop [msg (<! from>)]
      (if msg
        (do (>! bus> msg)
            (recur (<! from>)))
        (when close? (async/close! bus>))))
    (async/pipe bus> to>)))
