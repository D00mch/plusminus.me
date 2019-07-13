(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.tools.logging :as log]
            [beicon.core :as rx])
  (:import java.util.Random
           [java.util.concurrent.atomic AtomicLong]))

(def ^:private random (java.util.Random.))

(def ^:private generate-id!
  (let [id (AtomicLong. 0)] #(.getAndIncrement id)))

(defn- build-initial-state [size player1 player2]
  (let [game-id (generate-id!)]
    {:game-state    (st/state-template size)
     :game-id       game-id
     :creted        (java.util.Date.)
     :player1-id    player1
     :player2-id    player2
     :player1-hrz   (.nextBoolean random)}))

(defn- grouped-by-2 [observable size]
  (->> observable
       (rx/filter #(= (:row-size %) size))
       (rx/buffer 2)))

(defn- matched-requests []
  (let [requests (topics/consume :new)]
    (apply rx/merge (->> (range b/row-count-min b/row-count-max-excl)
                         (map #(grouped-by-2 requests %))))))

(defn- publish [[{p1 :id size :row-size} {p2 :id}]]
  (log/info "matched-requests on-next: " p1 p2)
  (let [game (build-initial-state size p1 p2)]
    (log/info "about to publish game: " game)
    (topics/publish :matched game)))

(defn subscribe "subscribe once! returns rx.disposable" []
  (rx/subscribe (matched-requests) publish #(log/error "matched-requests on-error: " %)))

(comment
  (def tmp-subs (subscribe))
  (.dispose tmp-subs)
  )
