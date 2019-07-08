(ns plus-minus.routes.multiplayer.room
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [plus-minus.validation :as validation]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest])
  (:import [java.util.concurrent.atomic AtomicInteger]
           [java.util.concurrent Executors TimeUnit]))

;; MULTIPLAYER IMPLEMENTED WITH STM

(def ^:private random (java.util.Random.))
(def ^:private service (Executors/newScheduledThreadPool
                        (+ 2 (. (Runtime/getRuntime) availableProcessors))))

(def ^:dynamic *turn-time-sec* 30)

#_(def fut (.schedule service #(prn "the end") 10 TimeUnit/SECONDS))

;;************************* STATE *************************

(def id (AtomicInteger. 0))
(defn- generate-id! [] (.getAndIncrement id))

(def rooms (ref {}))
(def size->room "tmp storage for waiting games" (ref {}))
(def player->room (ref {}))

(defn display-state []
  (->> @rooms count (str "rooms: ") println)
  (->> @size->room count (str "sizes: ") println)
  (->> @player->room count (str "players: ") println))

;;************************* GAME JUDGE *************************

(defn- on-disconnect "TODO: implement" [game] nil)
(defn- on-result "TODO: implement" [game]
  (let [moves (s/moves? (:state game))
        winner (if moves
                 "who has a turn - lose"
                 "who has less points - lose")]
    winner))

(defn- game-end! [game-id]
  (when-let [game (or (get @rooms game-id))]
    (dosync (alter rooms dissoc game-id)
            (alter player->room dissoc (:player1-id game) (:player2-id game)))
    (if (and (:player1-ready game) (:player2-ready game))
      (on-result game)
      (on-disconnect game))))

;;************************* GAME CREATION *************************

(defn- schedule-turn-timer [game-id]
  (.schedule service #(game-end! game-id) *turn-time-sec* TimeUnit/SECONDS))

(defn- build-initial-state [size player-id]
  (let [game-id (generate-id!)]
    {:state         (s/state-template size)
     :status        ::wait
     :game-id       game-id
     :creted        (java.util.Date.)
     :player1-id    player-id
     :player1-hrz   (.nextBoolean random)
     :turn-timer    (schedule-turn-timer game-id)
     :player1-ready false
     :player2-ready false}))

(defn- join-game!
  "Joins game with one player, returning that game"
  [size game-id player-id]
  (let [rooms (commute rooms assoc-in [game-id :player2-id] player-id)
        game  (get rooms game-id)]
    (alter player->room assoc
           (:player1-id game) game-id
           (:player2-id game) game-id)
    (alter size->room dissoc size)
    game))

(defn get-or-create!
  "Try to match existing game or create new one. Return game-id"
  [size player-id]
  (->> (if-let [game (->> (get @size->room size)
                          (get @rooms))]
         (join-game! size (:game-id game) player-id)
         (let [game    (build-initial-state size player-id)
               game-id (:game-id game)]
           (alter size->room assoc size game-id)
           (commute rooms assoc game-id game)
           game))
       :game-id))

;;************************* FIRST STATE EXCHANGE *************************

(defn- player-ready-key [game player-id]
  (cond (= (:player1-id game) player-id) :player1-ready
        (= (:player2-id game) player-id) :player2-ready
        :else (throw (ex-info "wrong ids" {:game game :player-id player-id}))))

(defn state-request!
  "Return state. When last player sees the game state, setting up turn-timer."
  [player-id]
  (let [game-id   (get @player->room player-id)
        game      (get @rooms game-id)
        ready-key (try (player-ready-key game player-id) (catch Exception e nil))]
    (cond (and game ready-key)
          (let [started   (= ::active (:status game))
                game-seen (assoc game ready-key true)
                seen      (and (:player1-ready game-seen) (:player2-ready game-seen))
                game-seen (if (and (not started) seen) ;; both just seen the game
                            (assoc game-seen
                                   :status ::active
                                   :turn-timer (schedule-turn-timer game-id))
                            game-seen)]
            (when-not (= game game-seen)
              (dosync (commute rooms assoc game-id game-seen)))
            (assoc game-seen :status ::active))

          ;; TODO: deside on what to do on below cases. May be just thrown an ex?
          (not game)
          ::game-doesnt-exist

          (not ready-key)
          ::game-player-missmatch)))

;;************************* SPECS *************************

(def ^:private game-status #{::wait ::active})
(def ^:private game-msg #{::state ::move ::give-up})

(spec/def ::status game-status)

(spec/def ::game (spec/and
                  (spec/keys :req-un [::s/state
                                      ::status ::game-id ::turn-timer
                                      ::player1-id ::player2-id ::player1-hrz
                                      ::player1-ready ::player2-ready]
                             :opt-un [])
                  #(= (-> % :status (= ::active))
                      (and (:player1-ready %) (:player2-ready %))) ))

(spec/def ::msg (spec/cat :msg-type game-msg
                          :id ::validation/id
                          :data (spec/? any?)))

#_(spec/explain ::msg [:first "dumch" {:a 1}])
#_(spec/explain ::game (get-in @rooms [0]))

(defn instrument []
  (spec/fdef state-request!
    :args (spec/and (spec/cat :player-id ::validation/id)
                    #(spec/valid? ::game (->> % :player-id
                                              (get @player->room)
                                              (get @rooms)))))
  (stest/instrument `state-request!))

