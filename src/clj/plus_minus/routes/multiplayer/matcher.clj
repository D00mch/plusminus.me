(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.routes.multiplayer.topics :as topics
             :refer [map->Game ->Reply]]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.tools.logging :as log]
            [beicon.core :as rx])
  (:import [java.util.concurrent.atomic AtomicLong]))

(def ^:private random (java.util.Random.))

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
      :player1-hrz (.nextBoolean random)})))

(defn- grouped-by-2 [observable size]
  (->> observable
       (rx/filter #(= (:data %) size))
       (rx/buffer 2)))

(defn- matched-requests []
  (let [requests (->> (topics/consume :msg)
                      (rx/filter #(-> % :msg-type (= :new))))]
    (apply rx/merge (->> (range b/row-count-min b/row-count-max-excl)
                         (map #(grouped-by-2 requests %))))))

(defn- publish [[{p1 :id size :data} {p2 :id}]]
  (log/info "matched-requests on-next: " p1 p2)
  (let [game     (build-initial-state size p1 p2)
        published (do (log/info "about to publish game: " game)
                      (topics/publish :matched game))]
    (when-not published
      (topics/publish :reply (->Reply :error p1 :game-with-yourself)))))

(defn subscribe "subscribe once! returns rx.disposable" []
  (rx/subscribe (matched-requests) publish #(log/error "matcher error: " %)))
