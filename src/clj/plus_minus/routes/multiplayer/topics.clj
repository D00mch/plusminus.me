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
           [io.reactivex.functions Function]))

(defn- subject [] (-> (rx/subject) rx/to-serialized))

(def topics {:new     (subject)
             :matched (subject)
             :msg     (subject)
             :reply   (subject)})

;; TODO: match topics with specs
(defn publish
  "returns true, if successful"
  [topic data]
  (-> (get topics topic) (rx/push! data))
  true)

(defn consume
  "returns Observable"
  [topic]
  (get topics topic))


;;************************* SPECS *************************

(defrecord Request [id row-size])
(defrecord Message [msg-type id data])
(defrecord Reply   [reply-type data])
(defrecord Result  [outcome id])        ;; data for Reply

(s/def ::request (s/keys :req-un [::validation/id ::b/row-size]))

(s/def ::msg-type #{:state :move :give-up})
(s/def ::msg (s/and (s/keys :req-un [::msg-type ::validation/id])
                    #(if (= :move (:msg-type %))  (:data %) true)))

(s/def ::reply-type #{:state :move :end :error})
(s/def ::outcome #{:draw :win :disconnect})
(s/def ::result (s/keys :req-un [::outcome ::validation/id]))
(s/def ::reply (s/and (s/keys :req-un [::reply-type ::data])
                      #(if (= :end (:reply-type %))
                         (spec/valid? ::outcome (-> % :data :outcome))
                         (any? %))))

;; (s/explain ::msg (->Message :give-up "dumch" nil))
;; (s/explain ::reply (->Reply :move 1))


(comment
  (publish :new (->Request "Bob" 3))
  (publish :new (->Request "Regeda" 3))
  (publish :new (->Request "Dumch" 3))
  )

(comment
  (def tmp-subs
    (rx/subscribe (consume :matched) #(println "matched: " (-> % :game-id))))
  (.dispose tmp-subs)
  )
