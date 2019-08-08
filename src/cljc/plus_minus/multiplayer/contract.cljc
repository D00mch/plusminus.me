(ns plus-minus.multiplayer.contract
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.validation :as validation]
            [clojure.spec.alpha :as s]
            [clojure.spec.alpha :as spec])
  #?(:cljs (:require-macros [cljs.core :refer [defrecord]])))

(comment
  "request new game with :new (Message :new id size),
  when someone else requested :new with the same size, game will be matched"
  "if you want to request a game with new size, you have to pass :drop msg first
   or you will initiate two games at the same time."

  ":turn-time will immediatelly return millis till the turn end"
  )

(defrecord Message [msg-type, ^String id, data])
(s/def ::msg-type #{:new :state :move :give-up :turn-time :drop})
(s/def ::msg (s/and
              (s/keys :req-un [::msg-type ::validation/id])
              #(if (= (:msg-type %) :new) (s/valid?  ::b/row-size (:data %)) true)
              #(if (= (:msg-type %) :drop) (s/valid?  ::b/row-size (:data %)) true)
              #(if (= (:msg-type %) :move) (s/valid? ::b/index (:data %)) true)))
;;(s/explain ::msg (->Message :new "dumch" 3))

;; matched: created games
(defrecord Game [state game-id player1 player2 ^boolean player1-hrz
                 created updated])

(defn turn-id [game]
  (if (= (-> game :state :hrz-turn) (:player1-hrz game))
    (:player1 game)
    (:player2 game)))

(defn other-id [{p1 :player1 p2 :player2 :as game} p]
  (if (= p1 p) p2 p1))

(s/def ::game-id number?)
(s/def ::player1 ::validation/id)
(s/def ::player2 ::validation/id)
(s/def ::player1-hrz boolean?)
(s/def ::created pos?)
(s/def ::updated pos?)
(s/def ::game (s/and (s/keys :req-un [::st/state ::game-id ::created ::updated
                                      ::player1 ::player2 ::player1-hrz])
                     #(not= (:player1 %) (:player2 %))))
#_(s/explain ::game (map->Game
                   {:state       (st/state-template 3)
                    :game-id     9999999
                    :created     (System/currentTimeMillis)
                    :player1     "bob"
                    :player2     "dumch"
                    :player1-hrz true}))


;; reply to user
(defrecord Reply   [reply-type, ^String id, data])
(defrecord Result  [outcome, cause]) ;; data for Reply
(s/def ::reply-type #{:state :move :end :error :turn-time :drop :cant-drop})
(s/def ::outcome #{:draw :win :lose})
(s/def ::errors #{:invalid-move :not-your-turn :game-doesnt-exist
                  :game-with-yourself :invalid-msg :unknown})
(s/def ::cause #{:give-up :time-out :no-moves})
(s/def ::result (s/keys :req-un [::outcome ::validation/id ::cause]))
(s/def ::reply (s/and (s/keys :req-un [::reply-type ::validation/id ::data])
                      #(case (:reply-type %)
                         :end   (s/valid? ::outcome (-> % :data :outcome))
                         :error (s/valid? ::errors (:data %))
                         (any? %))))
;; (s/explain-data ::reply (->Reply :error "bob" :invalid-move))
