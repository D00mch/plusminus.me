(ns plus-minus.multiplayer.contract
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.validation :as validation]
            [clojure.spec.alpha :as s]
            [clojure.spec.alpha :as spec]))

(defrecord Message [msg-type, ^String id, data])
(s/def ::msg-type #{:new :state :move :give-up})
(s/def ::msg (s/and
              (s/keys :req-un [::msg-type ::validation/id])
              #(if (= (:msg-type %) :new) (s/valid?  ::b/row-size (:data %)) true)
              #(if (= (:msg-type %) :move) (s/valid? ::b/index (:data %)) true)))
;;(s/explain ::msg (->Message :new "dumch" 3))

;; matched: created games
(defrecord Game [state game-id created player1 player2 ^boolean player1-hrz])
(s/def ::game-id number?)
(s/def ::player1 ::validation/id)
(s/def ::player2 ::validation/id)
(s/def ::player1-hrz boolean?)
(s/def ::created inst?)
(s/def ::game (s/and (s/keys :req-un [::st/state ::game-id ::created
                                      ::player1 ::player2 ::player1-hrz])
                     #(not= (:player1 %) (:player2 %))))
#_(s/explain ::game (map->Game
                   {:state       (st/state-template 3)
                    :game-id     9999999
                    :created      (java.util.Date.)
                    :player1     "bob"
                    :player2     "dumch"
                    :player1-hrz true}))

;; reply to user
(defrecord Reply   [reply-type, ^String id, data])
(defrecord Result  [outcome, ^String id]) ;; data for Reply
(s/def ::reply-type #{:state :move :end :error})
(s/def ::outcome #{:draw :win :disconnect})
(s/def ::errors #{:invalid-move :not-your-turn :game-doesnt-exist
                  :game-with-yourself :invalid-msg :unknown})
(s/def ::result (s/keys :req-un [::outcome ::validation/id]))
(s/def ::reply (s/and (s/keys :req-un [::reply-type ::validation/id ::data])
                      #(case (:reply-type %)
                         :end   (s/valid? ::outcome (-> % :data :outcome))
                         :error (s/valid? ::errors (:data %))
                         (any? %))))
;; (s/explain-data ::reply (->Reply :error "bob" :invalid-move))
