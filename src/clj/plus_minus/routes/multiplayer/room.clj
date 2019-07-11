(ns plus-minus.routes.multiplayer.room
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [plus-minus.validation :as validation]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [com.walmartlabs.cond-let :refer [cond-let]])
  (:import [java.util.concurrent.atomic AtomicInteger]
           [java.util.concurrent Executors TimeUnit]))

;; MULTIPLAYER IMPLEMENTED WITH STM

(def ^:private random (java.util.Random.))
(defonce ^:private service (Executors/newScheduledThreadPool
                        (+ 2 (. (Runtime/getRuntime) availableProcessors))))

(def ^:dynamic *turn-time-sec* 300)

#_(def fut (.schedule service #(prn "the end") 10 TimeUnit/SECONDS))

;;************************* STATE *************************

(def ^:private generate-id!
  (let [id (AtomicInteger. 0)] #(.getAndIncrement id)))

(defonce rooms (ref {}))
(defonce size->room (ref {}))
(defonce player->room (ref {}))

(defn display-state []
  (->> @rooms count (str "rooms: ") println)
  (->> @size->room count (str "sizes: ") println)
  (->> @player->room count (str "players: ") println))

(defn both-players-ready [game] (and (:player1-ready game) (:player2-ready game)))

(defn player-turn-id [game]
  (if (= (-> game :game-state :hrz-turn) (:player1-hrz game))
    (:player1-id game)
    (:player2-id game)))

(defn- game-result [game]
  (let [result (-> game :game-state (game/on-game-end (:player1-hrz game)))]
    (cond (= result :draw) [:draw]
          (= result :win)  [:win (:player1-id game)]
          (= result :lose) [:win (:player2-id game)])))

(defn- player-turn? [game player-id]
  (= player-id (player-turn-id game)))

;;************************* GAME JUDGE *************************

(defn- on-disconnect [game] [:end [:disconnect]])
(defn- on-result [game]
  (let [moves  (-> game :game-state st/moves?)
        result (if moves
                 [:win (player-turn-id game)]
                 (game-result game))]
    [:end result]))

(defn game-end! [game-id]
  (when-let [game (or (get @rooms game-id))]
    (dosync (alter rooms dissoc game-id)
            (alter player->room dissoc (:player1-id game) (:player2-id game)))
    (if (both-players-ready game)
      (on-result game)
      (on-disconnect game))))

;;************************* GAME CREATION *************************

(defn- schedule-turn-timer [game-id]
  (.schedule service #(game-end! game-id) *turn-time-sec* TimeUnit/SECONDS))

(defn- build-initial-state [size player-id]
  (let [game-id (generate-id!)]
    {:game-state    (st/state-template size)
     :status        :wait
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
  (dosync
   (cond-let
    :let [game-id   (get @player->room player-id)
          game      (get @rooms game-id)]
    (not game)      [:error :game-doesnt-exist]

    :let [ready-key (try (player-ready-key game player-id) (catch Exception e nil))]
    (not ready-key) [:error :game-player-missmatch]

    :else
    (let [started   (= :active (:status game))
          game-seen (assoc game ready-key true)
          seen      (both-players-ready game-seen)
          game-seen (if (and (not started) seen) ;; both just seen the game
                      (assoc game-seen
                             :status :active
                             :turn-timer (schedule-turn-timer game-id))
                      game-seen)]
      (when-not (= game game-seen)
        (alter rooms assoc game-id game-seen))
      game-seen))))

;;************************* MOVES *************************

(defn move!
  "Takes a ::msg, return changed game. Throws exceptions"
  [[type player-id move]]
  {:pre [(= type :move)]}

  (cond-let
   :let [game-id (get @player->room player-id)
         game    (get @rooms game-id)]
   (nil? game)                          [:error :game-doesnt-exist]
   (not (both-players-ready game))      [:error :players-not-ready]
   (not (player-turn? game player-id))  [:error :not-your-turn]

   :let [state  (:game-state game)]
   (not (st/valid-move? state move))    [:error :invalid-move]

   :let [game (-> (update game :game-state st/move move)
                  (assoc :turn-timer (schedule-turn-timer game-id)))
         game (dosync (commute rooms assoc game-id game) game)]
   (-> game :game-state st/moves? not)  (game-end! game-id)

   :else                                [:move move]))

;;************************* GIVE UP *************************

(defn give-up! [[type player-id _]]
  {:pre [(= type :give-up)]}
  (cond-let
   :let [game-id (get @player->room player-id)
         game    (get @rooms game-id)]
   (nil? game) [:error :game-doesnt-exist]

   :else       (game-end! game)))

;;************************* SPECS *************************

(def ^:private game-status #{:wait :active})
(def ^:private game-msg #{:state :move :give-up})
(def ^:private game-replies #{:state :move :end :error})
(def ^:private game-errors
  #{:game-player-missmatch :game-doesnt-exist
    :invalid-move :not-your-turn :players-not-ready})
(def ^:private game-results #{:draw :win :disconnect})

(spec/def ::status game-status)

(spec/def ::game (spec/and
                  (spec/keys :req-un [::st/game-state
                                      ::status ::game-id ::turn-timer
                                      ::player1-id ::player2-id ::player1-hrz
                                      ::player1-ready ::player2-ready]
                             :opt-un [])
                  #(= (-> % :status (= :active))
                      (and (:player1-ready %) (:player2-ready %))) ))

(spec/def ::msg (spec/cat :msg-type  game-msg
                          :player-id ::validation/id
                          :data      (spec/? any?)))

(spec/def ::reply
  (spec/cat :msg-type game-replies
            :data     (spec/or
                       :move  ::b/index
                       :end   (spec/cat :result game-results
                                        :win-id (spec/? ::validation/id))
                       :state ::game
                       :error game-errors)))

#_(spec/explain ::msg [:first "dumch" {:a 1}])
#_(spec/explain ::game (get-in @rooms [0]))

(comment "reply formats"
  [:state tmp-game]
  [:move 1]
  [:end [:draw]]
  [:end [:win "dumch"]]
  [:error :invalid-move]
  )

(defn instrument []
  (spec/fdef state-request!
    :args (spec/and (spec/cat :player-id ::validation/id)
                    #(spec/valid? ::game (->> % :player-id
                                              (get @player->room)
                                              (get @rooms)))))
  (stest/instrument `state-request!))
