(ns plus-minus.routes.multiplayer.topics
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [>!! <! chan go go-loop tap mult]]
            [plus-minus.multiplayer.contract :as contract])
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

(defn push!
  "blocking push! to channel"
  [topic val]
  (let [{:keys [chan spec]} (get channels topic)]
    (if-let [errors (s/explain-data spec val)]
      (do (log/error "can't publish invalid data" errors)
          false)
      (>!! chan val))))

;; TODO: tmp for tests, rewrite with mount
(defn reset-state! []
  (async/close! (get-in channels [:msg :chan]))
  (async/close! (get-in channels [:reply :chan]))
  (def channels {:msg   (->chan ::contract/msg)
                 :reply (->chan ::contract/reply)}))
