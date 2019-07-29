(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :as contract
             :refer [map->Game ->Reply]]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [beicon.core :as rx])
  (:import [java.util.concurrent.atomic AtomicLong]
           [java.util Random]))

(def ^:private random (Random.))

(def ^:private generate-id!
  (let [id (AtomicLong. 0)] #(.getAndIncrement id)))

(defn- build-initial-state [size player1 player2]
  (let [game-id (generate-id!)]
    (map->Game
     {:state      (st/state-template size)
      :game-id     game-id
      :created     (java.util.Date.)
      :player1     player1
      :player2     player2
      :player1-hrz (.nextBoolean ^Random random)})))

(defn- grouped-by-2 [observable size]
  (->> observable
       (rx/filter #(= (:data %) size))
       (rx/buffer 2)))

(defn- matched-requests [messages]
  (let [requests (->> messages
                      (rx/filter #(-> % :msg-type (= :new)))
                      (rx/observe-on :thread))]
    (apply rx/merge (->> (range b/row-count-min b/row-count-max-excl)
                         (map #(grouped-by-2 requests %))))))

(defn- build-game [[{p1 :id size :data} {p2 :id}]]
  (build-initial-state size p1 p2))

(defn generate-games [messages]
  (->> (matched-requests messages)
       (rx/map build-game)))
