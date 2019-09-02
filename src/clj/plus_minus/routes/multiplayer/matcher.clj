(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.multiplayer.contract :as contract
             :refer [map->Game ->Reply ->UsersStats]]
            [plus-minus.common.async :as a-utils]
            [plus-minus.game.state :as st]
            [plus-minus.routes.admin :as admin]
            [clojure.core.async :refer [>!!] :as async]
            [plus-minus.routes.multiplayer.persist :as persist]
            [clojure.spec.alpha :as s]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.game.board :as b])
  (:import [java.util.concurrent.atomic AtomicLong]
           [java.util Random HashMap]))

(defonce ^:private random (Random.))

(defonce ^:private generate-id!
  (let [id (AtomicLong. 0)] #(.getAndIncrement id)))

(defn initial-state [size player1 player2]
  (let [game-id (generate-id!)]
    (map->Game
     {:state       (st/state-template (contract/row-number size))
      :game-id     game-id
      :created     (System/currentTimeMillis)
      :updated     (System/currentTimeMillis)
      :player1     player1
      :player2     player2
      :users-stats (->UsersStats (persist/get-stats player1)
                                 (persist/get-stats player2))
      :player1-hrz (.nextBoolean ^Random random)})))

(defn- remove-values! [hashmap id & [id2]]
  (doseq [[k v] hashmap]
    (when (or (= id v) (= id2 v))
      (.remove ^HashMap hashmap k))))

(defn- msg->game-by-size
  "drop> - channel for :drop feedback
  active-games - atom; to prevent more than one simulteneous games"
  [drop> active-games]
  (fn [rf]
    (let [size->id (HashMap.)]
      (fn ([] (rf))
        ([result] (rf result))
        ([result {type :msg-type, id :id, size :data}]
         (let [cached-id (.get ^HashMap size->id size)
               new-game! (fn [id1 id2 size]
                           (remove-values! size->id id1 id2)
                           (rf result (initial-state size id2 id1)))
               cache!    (fn [id size] (.put ^HashMap size->id size id) result)]
           (case type
             :new (cond
                    (admin/maintenance?)
                    (>!! drop> (->Reply :drop id "server is on maintenance,
                    it may take several minutes"))

                    (get @active-games id)                  ;; already matched
                    (do (.remove ^HashMap size->id size) result)

                    (= cached-id id) (cache! id size)       ;; same player, cache it

                    cached-id (new-game! id cached-id size) ;; save size matched

                    (.containsKey ^HashMap size->id :quick) ;; a guy waits for any size
                    (new-game! id (.get ^HashMap size->id :quick) size)

                    (and (= size :quick)                    ;; you ready for any size
                         (.. ^HashMap size->id keySet stream findAny isPresent))
                    (let [any-size (.. ^HashMap size->id keySet stream findAny get)]
                      (new-game! id (.get ^HashMap size->id any-size) any-size))

                    :else (cache! id size))                 ;; can't match now, cache it

             :drop (if (= cached-id id)
                     (do (.remove ^HashMap size->id size)
                         (>!! drop> (->Reply :drop id nil))
                         result)
                     (do
                       (>!! drop> (->Reply :cant-drop id "must be already matched"))
                       result)))))))))

(defn- xform [drop> active-games]
  (comp (filter (comp #{:new :drop} :msg-type))
        (msg->game-by-size drop> active-games)))

(defn pipe-games!
  "takes chan in> contract/Message, returns chan out> contract/Game;
  out> will be closed when in> closed
  active-games - atom with map<id, value> to prevent multiple games"
  [in> out> drop> active-games & [close?]]
  (a-utils/pipe! out> (xform drop> active-games) in> (boolean close?))
  out>)
