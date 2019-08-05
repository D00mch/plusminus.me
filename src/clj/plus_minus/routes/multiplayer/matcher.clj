(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :as contract
             :refer [map->Game ->Reply ->Message]]
            [plus-minus.utils :as utils]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.core.async :refer [>! <! alt! go go-loop chan] :as async])
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

(def ^:private grouped-by-2-trans
  (fn [rf]
    (let [size->id (transient {})]
      (fn ([] (rf))
          ([result] (rf result))
          ([result {id :id, size :data}]
           (if-let [cached-id (get size->id size)]
             (do (dissoc! size->id size) ;; TODO return pair here
                 (rf result (build-initial-state size cached-id id)))
             (do (assoc! size->id size id)
                 result)))))))

(def ^:private xform
  (comp (filter #(-> % :msg-type (= :new)))
        grouped-by-2-trans))

(defn pipe-games!
  "takes chan in> contract/Message, returns chan out> contract/Game;
  out> will be closed when in> closed"
  [in> out>]
  (utils/pipe! out> xform in> true)
  out>)

;; tests
(comment

  (do
    (def in> (chan))
    (def out> (chan))
    (pipe-games! in> out>)
    (go-loop [g (<! out>)]
      (println g)
      (when g (recur (<! out>)))))

  (async/>!! in> (->Message :new "bobby" 3))
  (async/>!! in> (->Message :new "regedar" 3))

  (async/onto-chan in> [(->Message :new "bob" 3)
                        (->Message :move "cat" 2)
                        (->Message :new "john" 4)
                        (->Message :new "regeda" 3)])

  (clojure.core.async.impl.protocols/closed? out>)
  )
