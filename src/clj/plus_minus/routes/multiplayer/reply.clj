(ns plus-minus.routes.multiplayer.reply
  (:require [plus-minus.multiplayer.contract :as c
             :refer [->Reply ->Result ->Message]]
            [clojure.core.async :refer [>!! <!! <! go-loop chan]
             :as async]
            [plus-minus.routes.multiplayer.timer :as timer]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.tools.logging :as log]
            [com.walmartlabs.cond-let :refer [cond-let]]))

(defn- player-turn? [game player-id]
  (= player-id (c/turn-id game)))

(defn- error-replies [player-id key]
  (list (->Reply :error player-id key)))

(defn- replies [game reply-type data]
  (list (->Reply reply-type (:player1 game) data)
        (->Reply reply-type (:player2 game) data)))

(defn- end-results [{p1 :player1 p2 :player2 :as game} p1-outcome p2-outcome cause]
  (list (->Reply :end p1 (->Result p1-outcome cause game))
        (->Reply :end p2 (->Result p2-outcome cause game))))

(defn- calc-end-results [game]
  (let [result (-> game :state (game/on-game-end (:player1-hrz game)))]
    (case result
      :draw (end-results game :draw :draw :no-moves)
      :win  (end-results game :win :lose :no-moves)
      :lose (end-results game :lose :win :no-moves))))

(defn- winner-results [{p1 :player1 p2 :player2 :as game} winner cause]
  (end-results game
               (if (= p1 winner) :win :lose)
               (if (= p2 winner) :win :lose)
               cause))

(defn- game-end-replies [game {id :id type :msg-type}]
  (let [moves  (-> game :state st/moves?)]
    (cond
      (= type
         :give-up) (winner-results game (c/other-id game id) :give-up)
      moves        (winner-results game (c/other-id game (c/turn-id game)) :time-out)
      :else        (calc-end-results game))))

(defrecord GR [game replies])

(defn- time-replies [game] (replies game :turn-time (timer/turn-ends-after game)))

(defn- state-replies [game] (replies game :state game))

(defn- move->game-replies
  [game {type :msg-type, id :id, move :data :as msg}]
  {:pre [(= type :move)]}
  (cond-let
   (not (player-turn? game id))      (->GR game (error-replies id :not-your-turn))

   :let [state  (:state game)]
   (not (st/valid-move? state move)) (->GR game (error-replies id :invalid-move))

   :let [game (update game :state st/move move)]
   (-> game :state st/moves? not)    (->GR game (game-end-replies game msg))

   :else                             (->GR (timer/updated game)
                                           (replies game :move move))))

(defn- move->reply-trans [game]
  (fn [rf]
    (let [vgame (volatile! game)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result {id :id type :msg-type :as msg}]
         (rf result
             (if (timer/elapsed? @vgame)
               (game-end-replies @vgame msg)
               (case type
                 :new          (do (log/error "get :new msg" msg) result)
                 :drop         result
                 :turn-time    (time-replies @vgame)
                 :state        (state-replies @vgame)
                 :give-up      (game-end-replies @vgame msg)
                 :move         (let [{:keys [game replies]}
                                     (move->game-replies @vgame msg)]
                                 (vreset! vgame game)
                                 replies)

                 (time-replies @vgame)))))))))

(defn- xf-msg->reply [game]
  (comp (move->reply-trans game)
        (mapcat identity)))

(defn pipe-replies!
  "takes contracts/Game as initial state,
  in> channel with contract/Message, and out> channel with contract/Reply;
  return chan passing game-end event"
  [game in> out>]
  (let [bus> (async/chan)
        mult (async/mult bus>)
        end> (chan 1 (comp (filter #(= (:reply-type %) :end))
                           (drop 1)
                           (map (fn [_] :end))))] ; to close after 2 :end
    (async/tap mult end> true)
    (async/tap mult out> false)
    (doseq [r (state-replies game)] (>!! bus> r))
    (timer/pipe-with-move-timer! bus> (xf-msg->reply game) in> true)
    end>))
