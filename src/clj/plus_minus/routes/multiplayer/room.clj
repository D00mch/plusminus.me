(ns plus-minus.routes.multiplayer.room
  (:require [plus-minus.routes.multiplayer.topics :as topics
             :refer [->Reply]]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.tools.logging :as log]
            [beicon.core :as rx]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [clojure.string :as str])
  (:import [io.reactivex.disposables CompositeDisposable]))

;; TODO: set up timers

;;************************* STATE *************************

(defonce rooms (ref {}))
(defonce player->room (ref {}))

(defn display-state []
  (->> @rooms count (str "rooms: ") println)
  (->> @player->room count (str "players: ") println))

(defn print-state [game-id]
  (let [state (get-in @rooms [game-id :state])]
    (st/state-print state)))

;; TODO make private or remove
(defn reset-state! []
  (dosync (ref-set rooms {})
          (ref-set player->room {})))

;;************************* MOVES *************************

(defn player-turn-id [game]
  (if (= (-> game :state :hrz-turn) (:player1-hrz game))
    (:player1 game)
    (:player2 game)))

(defn- game-result [game]
  (let [result (-> game :state (game/on-game-end (:player1-hrz game)))]
    (case result
      :draw {:outcome :draw, :id (:player1 game)}
      :win  {:outcome :win,  :id (:player1 game)}
      :lose {:outcome :win,  :id (:player2 game)})))

(defn- player-turn? [game player-id]
  (= player-id (player-turn-id game)))

(defn- push-error [player-id key]
  (topics/publish :reply (->Reply :error player-id key)))

(defn- push [game reply-type data]
  (and (topics/publish :reply (->Reply reply-type (:player1 game) data))
       (topics/publish :reply (->Reply reply-type (:player2 game) data))))

(defn game-end! [game-id]
  (when-let [game (get @rooms game-id)]
    (dosync (alter rooms dissoc game-id)
            (alter player->room dissoc (:player1 game) (:player2 game)))
    (let [moves  (-> game :state st/moves?)
          result (if moves
                   {:outcome :win, :id (player-turn-id game)}
                   (game-result game))]
      (push game :end result))))

(defn on-move!
  "get user msg, publishes a reply"
  [{type :msg-type, id :id, move :data}]
  {:pre [(= type :move)]}
  (cond-let
   :let [game-id (get @player->room id)
         game    (get @rooms game-id)]
   (nil? game)                          (push-error id :game-doesnt-exist)
   (not (player-turn? game id))         (push-error id :not-your-turn)

   :let [state  (:state game)]
   (not (st/valid-move? state move))    (push-error id :invalid-move)

   :let [game (update game :state st/move move)
         game (dosync (alter rooms assoc game-id game) game)]
   (-> game :state st/moves? not)  (game-end! game-id)

   :else                                (push game :move move)))

;;************************* SUBSCRIPTION *************************

;; locking to get sure messages in the same order in particular game

(defn- on-new-game [game]
  (let [game-id (:game-id game)
        player1 (:player1 game)
        player2 (:player2 game)]
    (dosync (alter rooms assoc game-id game)
            (alter player->room assoc player1 game-id, player2 game-id))
    (let [published (locking (:game-id game) (push game :state game))]
      (when-not published
        (do
          (log/info "can't publish game with id" game-id)
          (push game :error :unknown))))))

(defn- on-message [{:keys [msg-type id data] :as msg}]
  (let [game-id (get @player->room id)
        game    (get @rooms game-id)]
    (if game
      (locking game-id
        (case msg-type
          :state   (push game :state game)
          :move    (on-move! msg)
          :give-up (game-end! game-id)))
      (do (log/info "on-message; cant find game" msg-type id data)
          (push-error id :game-doesnt-exist)))))

(defn subscribe-to-new-games []
  (let [matched (->> (topics/consume :matched)
                     (rx/observe-on :thread))]
    (rx/subscribe matched on-new-game #(log/error "new-rooms on-error: " %))))

(defn subscribe-to-usr-msgs []
  (let [msgs (->> (topics/consume :msg)
                  (rx/filter #(-> % :msg-type (not= :new)))
                  (rx/observe-on :thread))]
    (rx/subscribe msgs on-message #(log/error "usr-msgs on-error: " %))))

(defn subscribe []
  (doto (CompositeDisposable.)
    (.add (subscribe-to-new-games))
    (.add (subscribe-to-usr-msgs))))
