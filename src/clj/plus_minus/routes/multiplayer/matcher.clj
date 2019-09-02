(ns plus-minus.routes.multiplayer.matcher
  (:require [plus-minus.multiplayer.contract :as contract
             :refer [map->Game ->Reply ->UsersStats]]
            [plus-minus.common.async :as a-utils]
            [plus-minus.game.state :as st]
            [plus-minus.routes.admin :as admin]
            [clojure.core.async :refer [>!!] :as async]
            [plus-minus.routes.multiplayer.persist :as persist]
            [clojure.spec.alpha :as s]
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
     {:state       (st/state-template
                    (if (number? size) size (gen/generate (s/gen ::b/row-size))))
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
                           (rf result (initial-state size id2 id1)))]
           (case type
             :new (cond
                    (admin/maintenance?)
                    (>!! drop> (->Reply :drop id "server is on maintenance,
                    it may take several minutes"))

                    (get @active-games cached-id)       ;; already matched
                    (do (.remove ^HashMap size->id size)
                        result)

                    (and (not= cached-id id) cached-id) ;; the same size is matched
                    (new-game! id cached-id size)

                    (and (not= cached-id id)            ;; someone waits for any size
                         (.containsKey ^HashMap size->id :quick))
                    (new-game! id (.get ^HashMap size->id :quick) size)

                    (and (not= cached-id id)
                         (= size :quick)                ;; you are ready for any size
                         (.. ^HashMap size->id keySet stream findAny isPresent))
                    (let [any-size (.. ^HashMap size->id keySet stream findAny get)]
                      (new-game! id (.get ^HashMap size->id any-size) any-size))

                    :else                               ;; can't match now, cache
                    (do (.put ^HashMap size->id size id)
                        result))

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
