(ns plus-minus.app-db
  (:refer-clojure :exclude [get get-in reset! swap!])
  (:require [plus-minus.game.state :as st]
            [plus-minus.validation :as v]
            [reagent.core :as reagent :refer [atom]]
            [clojure.spec.alpha :as s]))

(comment
  ;; state example
  {:page :home,
   :identity nil,
   :usr-hrz-turn true,
   :game-state {:board
                {:row-size 4, :cells [-2 1 -1 -9 4 9 8 -9 -2 -6 8 9 -5 -7 -4 -9]},
                :start 9,
                :moves [],
                :hrz-points 0,
                :vrt-points 0,
                :hrz-turn true}}

  (s/def ::page #{:home :about})
  (s/def ::identity ::v/id)
  (s/def ::usr-hrz-turn boolean)
  (s/def ::game-state ::st/state)
  (s/def ::db-state (s/keys :req-un [::page ::identity ::usr-hrz-turn ::game-state]))
  )

(defonce state (atom {}))

(defn cursor [ks]
  (reagent/cursor state ks))

(defn get
  "Get the key's value from the session, returns nil if it doesn't exist."
  [k & [default]]
  (let [temp-a @(cursor [k])]
    (if-not (nil? temp-a) temp-a default)))

(defn put! [k v]
  (clojure.core/swap! state assoc k v))

(defn get-in
 "Gets the value at the path specified by the vector ks from the session,
  returns nil if it doesn't exist."
  [ks & [default]]
  (let [result @(cursor ks)]
    (if-not (nil? result) result default)))

(defn swap!
  "Replace the current session's value with the result of executing f with
  the current value and args."
  [f & args]
  (apply clojure.core/swap! state f args))

(defn clear! []
  (clojure.core/reset! state {}))

(defn reset! [m]
  (clojure.core/reset! state m))

(defn remove! [k]
  (clojure.core/swap! state dissoc k))

(defn assoc-in! [ks v]
  (clojure.core/swap! state assoc-in  ks v))

(defn get!
  "Destructive get from the session. This returns the current value of the key
  and then removes it from the session."
  [k & [default]]
  (let [cur (get k default)]
    (remove! k)
    cur))

(defn get-in!
  "Destructive get from the session. This returns the current value of the path
  specified by the vector ks and then removes it from the session."
  [ks & [default]]
    (let [cur (get-in ks default)]
      (assoc-in! ks nil)
      cur))

(defn update!
  "Updates a value in session where k is a key and f
   is the function that takes the old value along with any
   supplied args and return the new value. If key is not
   present it will be added."
  [k f & args]
  (clojure.core/swap!
    state
    #(apply (partial update % k f) args)))

(defn update-in!
  "Updates a value in the session, where ks is a
   sequence of keys and f is a function that will
   take the old value along with any supplied args and return
   the new value. If any levels do not exist, hash-maps
   will be created."
  [ks f & args]
  (clojure.core/swap!
    state
    #(apply (partial update-in % ks f) args)))
