(ns plus-minus.routes.multiplayer.topics
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.validation :as validation]
            [beicon.core :as rx]
            [clojure.spec.alpha :as s]
            [clojure.spec.alpha :as spec]
            [clojure.tools.logging :as log])
  (:import [io.reactivex Observable]
           [io.reactivex.subjects BehaviorSubject]
           [io.reactivex.internal.observers LambdaObserver]
           [io.reactivex.functions Function])
  (:gen-class))

(defn- subject [] (-> (rx/subject) rx/to-serialized))

(def topics {:matched {:subj (subject), :spec ::game}
             :msg     {:subj (subject), :spec ::msg}
             :reply   {:subj (subject), :spec ::reply}})

(defn publish
  "returns true, if successful"
  [topic data]
  (let [{:keys [subj spec]} (get topics topic)]
    (if-let [errors (s/explain-data spec data)]
      (do (log/error "can't publish invalid data" errors)
          false)
      (do (rx/push! subj data)
          true))))

(defn consume
  "returns Observable"
  [topic]
  (get-in topics [topic :subj]))

;;************************* SPECS *************************

;; user's input
(defrecord Message [msg-type id data])
(s/def ::msg-type #{:new :state :move :give-up})
(s/def ::msg (s/and
              (s/keys :req-un [::msg-type ::validation/id])
              #(if (= (:msg-type %) :new) (s/valid?  ::b/row-size (:data %)) true)
              #(if (= (:msg-type %) :move) (s/valid? ::b/index (:data %)) true)))
;;(s/explain ::msg (->Message :new "dumch" 3))

;; matched: created games
(defrecord Game [state game-id created player1 player2 player1-hrz])
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
(defrecord Reply   [reply-type id data])
(defrecord Result  [outcome id])                      ; data for Reply
(s/def ::reply-type #{:state :move :end :error})
(s/def ::outcome #{:draw :win :disconnect})
(s/def ::errors #{:ivalid-move :not-your-turn :game-doesnt-exist
                  :game-with-yourself :unknown})
(s/def ::result (s/keys :req-un [::outcome ::validation/id]))
(s/def ::reply (s/and (s/keys :req-un [::reply-type ::validation/id ::data])
                      #(case (:reply-type %)
                         :end   (s/valid? ::outcome (-> % :data :outcome))
                         :error (s/valid? ::errors (:data %))
                         (any? %))))
#_(s/explain ::reply (->Reply :error :game-with-yourself))

(comment "tmp code to simulate the game"

  (defn subscribe-all []
    (def matcher-subs (plus-minus.routes.multiplayer.matcher/subscribe))
    (def room-subs (plus-minus.routes.multiplayer.room/subscribe-to-new-games))
    (def tmp-subs2
      (rx/subscribe (consume :reply) #(println "reply:" %))))

  (defn unsubscribe-all []
    (.dispose matcher-subs)
    (.dispose room-subs)
    (.dispose tmp-subs2))

  (subscribe-all)
  (unsubscribe-all)

  (publish :msg (->Message :new "Bob" 3))
  (publish :msg (->Message :new "Dumch" 3))
  (publish :msg (->Message :move "Bob" 2))
  (publish :msg (->Message :move "Dumch" 5))

  (def tmp-subs (rx/subscribe (->> (consume :msg)
                                   (rx/map #(do (println "having " %)
                                                %))
                                   (rx/filter #(-> % :msg-type (= :new))))
                              #(println "msg: " %)
                              #(println "err: " %)))
  (.dispose tmp-subs)
)
