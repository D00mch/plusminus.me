(ns plus-minus.routes.multiplayer.reply
  (:require [plus-minus.multiplayer.contract :as contract
             :refer [->Reply ->Result ->Message]]
            [clojure.core.async :refer [>!! <!! <! go-loop chan]
             :as async]
            [plus-minus.routes.multiplayer.timer :as timer]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.tools.logging :as log]
            [com.walmartlabs.cond-let :refer [cond-let]]))

(defn player-turn-id [game]
  (if (= (-> game :state :hrz-turn) (:player1-hrz game))
    (:player1 game)
    (:player2 game)))

(defn other-player [{p1 :player1 p2 :player2 :as game} p]
  (if (= p1 p) p2 p1))

(defn- game-result [game]
  (let [result (-> game :state (game/on-game-end (:player1-hrz game)))]
    (case result
      :draw (->Result :draw (:player1 game) :no-moves)
      :win  (->Result :win  (:player1 game) :no-moves)
      :lose (->Result :win  (:player2 game) :no-moves))))

(defn- player-turn? [game player-id]
  (= player-id (player-turn-id game)))

(defn- error-replies [player-id key]
  (list (->Reply :error player-id key)))

(defn- replies [game reply-type data]
  (list (->Reply reply-type (:player1 game) data)
        (->Reply reply-type (:player2 game) data)))

(defn- game-end-replies [game & {cause :cause}]
  (let [moves  (-> game :state st/moves?)
        result (if moves
                 (->Result :win (other-player game (player-turn-id game)) cause)
                 (game-result game))]
    (replies game :end result)))

(defrecord GR [game replies])

(defn- time-replies [game] (replies game :turn-time (timer/turn-ends-after game)))

(defn- state-replies [game] (replies game :state game))

(defn- move->game-replies
  [game {type :msg-type, id :id, move :data}]
  {:pre [(= type :move)]}
  (cond-let
   (not (player-turn? game id))      (->GR game (error-replies id :not-your-turn))

   :let [state  (:state game)]
   (not (st/valid-move? state move)) (->GR game (error-replies id :invalid-move))

   :let [game (update game :state st/move move)]
   (-> game :state st/moves? not)    (->GR game (game-end-replies game))

   :else                             (->GR (timer/updated game)
                                           (replies game :move move))))

(defn- move->reply-trans [game]
  (fn [rf]
    (let [vgame (volatile! game)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result {type :msg-type :as msg}]
         (rf result
             (if (timer/elapsed? @vgame)
               (game-end-replies @vgame :cause :time-out)
               (case type
                 :new          (do (log/error "get :new msg" msg) (result))
                 :turn-time    (time-replies @vgame)
                 :state        (state-replies @vgame)
                 :give-up      (game-end-replies @vgame :cause :give-up)
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
    (async/tap mult end>)
    (async/tap mult out>)
    (doseq [r (state-replies game)] (>!! bus> r))
    (timer/pipe-with-move-timer! bus> (xf-msg->reply game) in>)
    end>))

 ;;testing
(comment

  (do
    (def game
      {:state {:board {:row-size 3,:cells [4 2 -5 9 -8 -7 -5 -5 8]},
               :start 6,:moves [],:hrz-points 0,:vrt-points 0,:hrz-turn true},
       :game-id 0,
       :created (System/currentTimeMillis),
       :updated (System/currentTimeMillis),
       :player1 "bob",:player2 "regeda",
       :player1-hrz true})
    (def in> (chan))
    (def out> (chan))
    (go-loop [v (<! out>)]
      (println v)
      (when v (recur (<! out>))))
    (def end> (pipe-replies! game in> out>))
    )

  (do (prn (player-turn-id game))
      (prn (st/valid-moves (:state game)))
      (st/state-print (:state game)))

  (let [mv 8
        nm "bob"
        ;; nm "regeda"
        ] (>!! in> (->Message :move nm mv))
       (def game (update game :state st/move mv)))

  (>!! in> (->Message :give-up "regeda" nil))

  (clojure.core.async.impl.protocols/closed? in>)
  (<!! end>)
  (do
    (async/close! end>)
    (async/close! in>)
    (async/close! out>))
  )
