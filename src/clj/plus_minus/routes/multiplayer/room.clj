(ns plus-minus.routes.multiplayer.room
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.tools.logging :as log]
            [beicon.core :as rx]))

;;************************* STATE *************************

(defonce rooms (ref {}))
(defonce player->room (ref {}))

(defn display-state []
  (->> @rooms count (str "rooms: ") println)
  (->> @player->room count (str "players: ") println))

(defn player-turn-id [game]
  (if (= (-> game :game-state :hrz-turn) (:player1-hrz game))
    (:player1-id game)
    (:player2-id game)))

(defn- game-result [game]
  (let [result (-> game :game-state (game/on-game-end (:player1-hrz game)))]
    (case result
      :draw {:outcome :draw :winner (:player1-id game)}
      :win  {:outcome :win  :winner (:player1-id game)}
      :lose {:outcome :win  :winner (:player2-id game)})))

(defn- player-turn? [game player-id]
  (= player-id (player-turn-id game)))


(defn- new-rooms []
  (topics/consume :matched)
  )

(defn- usr-msgs []
  (topics/consume :msg)
  )

;;************************* GAME JUDGE *************************

