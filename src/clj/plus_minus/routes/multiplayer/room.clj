(ns plus-minus.routes.multiplayer.room
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Reply ->Message]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.tools.logging :as log]
            [beicon.core :as rx]
            [clojure.spec.alpha :as s]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [plus-minus.validation :as validation]) )

;; handles user's messages in active game
;; methods with '-obs' ending return observables

;; TODO: rename: replies?

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
      :draw {:outcome :draw, :id (:player1 game)}
      :win  {:outcome :win,  :id (:player1 game)}
      :lose {:outcome :win,  :id (:player2 game)})))

(defn- player-turn? [game player-id]
  (= player-id (player-turn-id game)))

(defn- error-obs [player-id key]
  (rx/just (->Reply :error player-id key)))

(defn- replies-obs [game reply-type data]
  (rx/of (->Reply reply-type (:player1 game) data)
         (->Reply reply-type (:player2 game) data)))

(defn game-end-obs! [game]
  (let [moves  (-> game :state st/moves?)
        result (if moves
                 {:outcome :win, :id (player-turn-id game)}
                 (game-result game))]
    (replies-obs game :end result)))

(defn- ->state [game replies]
  {:game game, :replies-obs replies, :turn-timer nil})

(defn move->state!
  "get user msg, publishes a reply"
  [game {type :msg-type, id :id, move :data}]
  (log/info "on move")
  {:pre [(= type :move)]}
  (cond-let
   (not (player-turn? game id))      (->state game (error-obs id :not-your-turn))

   :let [state  (:state game)]
   (not (st/valid-move? state move)) (->state game (error-obs id :invalid-move))

   :let [game (update game :state st/move move)]
   (-> game :state st/moves? not)    (do
                                       (log/info "about to return :end")
                                       (->state game (game-end-obs! game)))

   :else                             (do
                                       (log/info "about ot return :move")
                                       (->state game (replies-obs game :move move)))))

(defn state-resp [game]
  (->state game (replies-obs game :state game)))

(defn- reduce-game-msg [{:keys [game _]} msg]
  (case (:msg-type msg)
    :state        (state-resp game)
    :move         (move->state! game msg)
    :time-elapsed (->state game (game-end-obs! game))
    :give-up      (->state game (game-end-obs! game))))

;; TODO: rename to reply-obs?
(defn reply
  "return Observable<Reply>"
  [game messages-obs]
  (->> messages-obs
       (rx/scan reduce-game-msg (state-resp game))
       (rx/flat-map (fn [{:keys [_ replies-obs]}] replies-obs))))
