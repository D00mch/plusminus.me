(ns plus-minus.routes.multiplayer.topics
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [>! <! chan go go-loop tap mult]]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Message]])
  (:gen-class))

(defn ->chan [spec]
  (let [c (chan)] {:chan c, :mult (mult c), :spec spec}))

(def channels {:msg   (->chan ::contract/msg)
               :reply (->chan ::contract/reply)})

(defn tap! [topic chan]
  (tap (get-in channels [topic :mult]) chan)
  chan)

(defn in-chan
  "don't read from this channel - it has read race contention,
  because it's tapped with mult;
  use topics/tap! instead"
  [topic]
  (get-in channels [topic :chan]))

(defn push! [topic val]
  (let [{:keys [chan spec]} (get channels topic)]
    (if-let [errors (s/explain-data spec val)]
      (do (log/error "can't publish invalid data" errors)
          false)
      (do (go (>! chan val))
          true))))

;; TODO: tmp for tests, remove
(defn reset-state! []
  (async/close! (get-in channels [:msg :chan]))
  (async/close! (get-in channels [:reply :chan]))
  (def channels {:msg   (->chan ::contract/msg)
                 :reply (->chan ::contract/reply)}))
