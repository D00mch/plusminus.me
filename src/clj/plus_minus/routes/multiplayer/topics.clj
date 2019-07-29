(ns plus-minus.routes.multiplayer.topics
  (:require [beicon.core :as rx]
            [clojure.spec.alpha :as s]
            [clojure.tools.logging :as log]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Message]])
  (:gen-class))

(defn- subject [] (-> (rx/subject) rx/to-serialized))

(def topics {:msg     {:subj (subject), :spec ::contract/msg}
             :reply   {:subj (subject), :spec ::contract/reply}})

(defn publish
  "returns true, if successful"
  [topic data]
  (let [{:keys [subj spec]} (get topics topic)]
    (if-let [errors (s/explain-data spec data)]
      (do (log/error "can't publish invalid data" errors)
          false)
      (do (rx/push! subj data)
          true))))

(defn consume
  "returns Observable"
  [topic]
  (get-in topics [topic :subj]))

;; TODO: tmp for tests, remove
(defn reset-state! []
  (def topics {:msg     {:subj (subject), :spec ::contract/msg}
               :reply   {:subj (subject), :spec ::contract/reply}}))
