(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.multiplayer.contract :as contract
             :refer [map->Game ->Reply]]
            [plus-minus.common.async :as a-utils]
            [plus-minus.game.state :as st]
            [plus-minus.routes.admin :as admin]
            [clojure.core.async :refer [>!!] :as async])
  (:import [java.util.concurrent.atomic AtomicLong]
           [java.util Random]))

(defonce ^:private random (Random.))

(defonce ^:private generate-id!
  (let [id (AtomicLong. 0)] #(.getAndIncrement id)))

(defn initial-state [size player1 player2]
  (let [game-id (generate-id!)]
    (map->Game
     {:state       (st/state-template size)
      :game-id     game-id
      :created     (System/currentTimeMillis)
      :updated     (System/currentTimeMillis)
      :player1     player1
      :player2     player2
      :player1-hrz (.nextBoolean ^Random random)})))

(defn- msg->game-by-size
  "drop> - channel for :drop feedback"
  [drop>]
  (fn [rf]
    (let [size->id (transient {})]
      (fn ([] (rf))
        ([result] (rf result))
        ([result {type :msg-type, id :id, size :data}]
         (let [cached-id (get size->id size)]
           (case type
             :new (cond
                    (admin/maintenance?)
                    (>!! drop> (->Reply :drop id "server is on maintenance,
                    it may take several minutes"))

                    (and cached-id (not= cached-id id))
                    (do (dissoc! size->id size)
                        (rf result (initial-state size cached-id id)))

                    :else
                    (do (assoc! size->id size id)
                        result))

             :drop (if (= cached-id id)
                     (do (dissoc! size->id size)
                         (>!! drop> (->Reply :drop id nil))
                         result)
                     (do
                       (>!! drop> (->Reply :cant-drop id "must be already matched"))
                       result)))))))))

(defn- xform [drop>]
  (comp (filter (comp #{:new :drop} :msg-type))
        (msg->game-by-size drop>)))

(defn pipe-games!
  "takes chan in> contract/Message, returns chan out> contract/Game;
  out> will be closed when in> closed"
  [in> out> drop> & [close?]]
  (a-utils/pipe! out> (xform drop>) in> (boolean close?))
  out>)
