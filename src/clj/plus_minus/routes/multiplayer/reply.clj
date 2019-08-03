(ns plus-minus.routes.multiplayer.reply
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Reply ->Result ->Message]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.utils :as utils]
            [clojure.core.async :refer [>!! <!! >! <! alt! go go-loop chan]
             :as async]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [plus-minus.validation :as validation]) )

;; handles user's messages in active game
;; methods with '-obs' ending return observables

(s/def ::sys-msg-type #{:time-elapsed})
(s/def ::msg (s/or :player-msg ::contract/msg
                   :system-msg (s/and (s/keys :req-un [::validation/id])
                                      #(s/valid? ::sys-msg-type (:msg-type %)))))
#_(s/explain ::msg (->Message :time-elapsed "dumch" nil))


(defn player-turn-id [game]
  (if (= (-> game :state :hrz-turn) (:player1-hrz game))
    (:player1 game)
    (:player2 game)))

(defn- game-result [game]
  (let [result (-> game :state (game/on-game-end (:player1-hrz game)))]
    (case result
      :draw (->Result :draw (:player1 game) :no-moves)
      :win  (->Result :win  (:player1 game) :no-moves)
      :lose (->Result :win  (:player2 game) :no-moves))))

(defn- player-turn? [game player-id]
  (= player-id (player-turn-id game)))

(defn- errors [player-id key]
  (list (->Reply :error player-id key)))

(defn- replies [game reply-type data]
  (list (->Reply reply-type (:player1 game) data)
         (->Reply reply-type (:player2 game) data)))

(defn- game-end-replies [game & {cause :cause}]
  (let [moves  (-> game :state st/moves?)
        result (if moves
                 (->Result :win (player-turn-id game) cause)
                 (game-result game))]
    (replies game :end result)))

(defrecord GR [game replies])

(defn- msg->game-replies
  [game {type :msg-type, id :id, move :data}]
  {:pre [(= type :move)]}
  (cond-let
   (not (player-turn? game id))      (->GR game (errors id :not-your-turn))

   :let [state  (:state game)]
   (not (st/valid-move? state move)) (->GR game (errors id :invalid-move))

   :let [game (update game :state st/move move)]
   (-> game :state st/moves? not)    (->GR game (game-end-replies game))

   :else                             (->GR game (replies game :move move))))

(defn- state-replies [game]
  (replies game :state game))

(defn- move->reply-trans [game]
  (fn [rf]
    (let [vgame (volatile! game)]
      (fn
        ([] (rf))
        ([result] (rf result))
        ([result {type :msg-type :as msg}]
         (rf result
             (case type
               :new          (do (log/error "get :new msg" msg) (result))
               :state        (state-replies @vgame)
               :time-elapsed (game-end-replies @vgame :cause :time-out)
               :give-up      (game-end-replies @vgame :cause :give-up)
               :move         (let [{:keys [game replies]}
                                   (msg->game-replies @vgame msg)]
                               (vreset! vgame game)
                               replies))))))))

(defn- xf-msg->reply [game]
  (comp (move->reply-trans game)
        (mapcat identity)
        #_(cat (state-replies game))
        ))

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
    (go (doseq [r (state-replies game)] (>! bus> r))       ; pass init state to players
        (utils/pipe! bus> (xf-msg->reply game) in> false)) ; process messages
    (async/tap mult end>)
    (async/tap mult out>)
    end>))


 ;;testing
(comment

  (do
    (def game
      {:state {:board {:row-size 3,:cells [4 2 -5 9 -8 -7 -5 -5 8]},
               :start 6,:moves [],:hrz-points 0,:vrt-points 0,:hrz-turn true},
       :game-id 0,
       :created #inst "2019-08-02T10:01:53.612-00:00",
       :player1 "bob",:player2 "regeda",
       :player1-hrz true})
    (def in> (chan))
    (def out> (chan))
    (def end> (pipe-replies! game in> out>))
    (go-loop [v (<! out>)]
      (println v)
      (when v (recur (<! out>)))))

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
