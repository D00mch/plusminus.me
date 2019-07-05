(ns plus-minus.routes.multiplayer.room
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [plus-minus.validation :as validation]
            [clojure.spec.alpha :as spec])
  (:import [java.util.concurrent.atomic AtomicInteger]
           [java.util.concurrent Executors TimeUnit]))

(def ^:private random (java.util.Random.))
(def ^:private service (Executors/newScheduledThreadPool
                            (+ 2 (. (Runtime/getRuntime) availableProcessors))))

#_(def fut (.schedule service #(prn "the end") 10 TimeUnit/SECONDS))

;;************************* SPECS *************************

(def ^:private game-status #{::wait ::active})
(def ^:private game-msg #{:state :move :give-up})

(spec/def ::status game-status)
(spec/def ::game (spec/keys :req-un [::s/state
                                     ::status ::game-id ::turn-timer
                                     ::player1-id ::player2-id ::player1-hrz
                                     ::player1-has-state ::player2-has-state]
                            :opt-un []))
(spec/def ::msg (spec/cat :msg-type game-msg, :id ::validation/id, :data (spec/? any?)))

#_(spec/explain ::msg [:first "dumch" {:a 1}])
#_(spec/explain ::game (get-in @rooms [0]))

;;************************* STATE *************************

(def id (AtomicInteger. 0))
(defn- generate-id! [] (.getAndIncrement id))

(def waiting-rooms (ref {}))
(def rooms (ref {}))
(def player->room (ref {}))

;;************************* GAME JUDGE *************************

(defn- on-disconnect "TODO: implement" [game] nil)
(defn- on-result "TODO: implement" [game]
  (let [moves (s/moves? (:state game))
        winner (if moves
                 "who has a turn - lose"
                 "who has less points - lose")]
    winner))

(defn- game-end! [game-id]
  (when-let [game (get rooms game-id)]
    (dosync (alter rooms dissoc (:game-id game))
            (alter player->room dissoc (:player1-id game) (:player2-id game)))
    (if (and (:player1-has-state game) (:player2-has-state game))
      (on-result game)
      (on-disconnect game))))

;;************************* GAME CREATION *************************

(defn- build-initial-state [size player-id]
  (let [game-id (generate-id!)]
    {:state       (s/state-template size)
    :status      ::wait
    :game-id     game-id
    :player1-id  player-id
    :player1-hrz (.nextBoolean random)
    :turn-timer  #(game-end! game-id)
    :player1-has-state false
    :player2-has-state false}))

(defn- join-game!
  "Remove game from waiting-rooms and put it in active rooms. Return game"
  [size game player-id]
  (let [joined-games (->> (assoc game
                                 :player2-id player-id
                                 :creted (java.util.Date.))
                          (commute rooms assoc (:game-id game)))
        game-id     (:game-id game)
        game        (get joined-games game-id)]
    (alter player->room assoc
           (:player1-id game) game-id
           (:player2-id game) game-id)
    (alter waiting-rooms dissoc size)
    game))

(defn get-or-create!
  "Try to match existing game or create new one. Return game id"
  [size player-id]
  (-> (if-let [game (get @waiting-rooms size)]
        (join-game! size game player-id)
        (let [game (build-initial-state size player-id)]
          (-> waiting-rooms
              (alter assoc size game)
              (get size))))
      :game-id))

;;************************* FIRST STATE EXCHANGE *************************


