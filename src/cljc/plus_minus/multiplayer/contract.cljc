(ns plus-minus.multiplayer.contract
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.validation :as validation]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  #?(:cljs (:require-macros [cljs.core :refer [defrecord]])))

;; request new game with :new (Message :new id size),
;; when someone else requested :new with the same size, game will be matched

;; if you want to request a game with new size, you have to pass :drop msg first
;; or you will initiate two games at the same time.

;; :turn-time will immediatelly return millis till the turn end

(def ^:const ping-ms 7000)
(def ^:const turn-ms 15000)
(def ^:const size-multiplier 0.2) ;; 15 sec for row 5, 27 - for 9

(defn calc-turn-ms [row-size]
  (int (* turn-ms row-size size-multiplier)))

;; ************************  COMMON
(s/def ::count (s/or :n zero? :n pos-int?))

;; ************************  MOCKS
(defrecord MockReply [mock-type remain$ prey]) ;; how match influence agressor has
(s/def ::remain$ ::count)
(s/def ::mock-type #{:board-pink
                     :board-small
                     :alert-good-luck
                     :alert-you-gonna-lose
                     :laughter})
(s/def ::prey ::validation/id)
(s/def ::mock (s/keys :req-un [::mock-type ::remain$ ::prey]))

(defonce mock-info
  {:board-pink           {:price 20 :name "color board in pink"}
   :board-small          {:price 15 :name "decrease board size"}
   :alert-good-luck      {:price 10 :name "wish good luck"}
   :alert-you-gonna-lose {:price 10 :name "warn about near lose"}
   :laughter             {:price 5  :name "turn on laughter"}})

(defn mock-price [type] (get-in mock-info [type :price]))
(defn mock-name  [type] (get-in mock-info [type :name]))

;; ************************  USER MESSAGES
(defrecord Message [msg-type, ^String id, data])
(s/def ::msg-type #{:new :state :move :give-up :turn-time :drop :mock})
(s/def ::game-request (s/or :size ::b/row-size, :opts #{:quick}))
(s/def ::msg (s/and
              (s/keys :req-un [::msg-type ::validation/id])
              #(case (:msg-type %)
                 :new  (s/valid? ::game-request (:data %))
                 :drop (s/valid? ::game-request (:data %))
                 :move (s/valid? ::b/index (:data %))
                 :mock (s/valid? ::mock-type (:data %))
                 true)))

(defn row-number [row]
  (if (number? row) row (gen/generate (s/gen ::b/row-size))))

#_(s/valid? ::msg (->Message :new "bob" :quick))

;; ************************  STATISTICS
(s/def ::give-up  ::count)
(s/def ::time-out ::count)
(s/def ::no-moves ::count)
(s/def ::draw     ::count)

(s/def ::stat (s/keys :req-un [::give-up ::time-out ::no-moves]))
(s/def ::win  ::stat)
(s/def ::lose ::stat)
(s/def ::influence ::count)

(s/def ::statistics (s/keys :req-un [::win ::lose ::draw ::influence]))

(def stats-initial {:win       {:give-up 0, :time-out 0, :no-moves 0},
                    :lose      {:give-up 0, :time-out 0, :no-moves 0},
                    :draw      0
                    :influence 50})

(defn stats-sum [stats key]
  (->> (get stats key) (map second) (reduce +)))

(defn stats-summed [{:keys [draw] :as stats}]
  (merge stats
         {:draw draw
          :win  (stats-sum stats :win)
          :lose (stats-sum stats :lose)}))

;; ************************  MATCHED: CREATED GAMES
(defrecord UsersStats [stats1 stats2])
(s/def ::stats1 ::statistics)
(s/def ::stats2 ::statistics)
(s/def ::users-stats (s/keys :req-un [::stats1 ::stats2]))

(defrecord Game [state game-id player1 player2 ^boolean player1-hrz
                 users-stats
                 created updated])

(defn stats-key [{p1 :player1 p2 :player2, :as game} id]
  (condp = id
    p1 :stats1
    p2 :stats2))

(defn stats [game id] ((stats-key game id) (:users-stats game)))

(def ^:const influence-on-win 50)

(defn influence-game-path [game id]
  [:users-stats (stats-key game id) :statistics :influence])

(defn influence-get [game id]
  (get-in game (influence-game-path game id)))

(defn influence++ [game winner]
  (update-in game (influence-game-path game winner) + influence-on-win))

(defn turn-id [game]
  (if (= (-> game :state :hrz-turn) (:player1-hrz game))
    (:player1 game)
    (:player2 game)))

(defn other-id [{p1 :player1 p2 :player2 :as game} p]
  (if (= p1 p) p2 p1))

(defn row-size [game]
  (-> game :state :board :row-size))

(defn game->time [game]
  (calc-turn-ms (row-size game)))

(s/def ::game-id number?)
(s/def ::player1 ::validation/id)
(s/def ::player2 ::validation/id)
(s/def ::player1-hrz boolean?)
(s/def ::created pos?)
(s/def ::updated pos?)
(s/def ::game (s/and (s/keys :req-un [::st/state ::game-id ::created ::updated
                                      ::player1 ::player2 ::player1-hrz
                                      ::usesrs-stats])
                     #(not= (:player1 %) (:player2 %))))

;; ************************ REPLY TO USER

(defrecord Result [outcome, cause, game])
(s/def ::outcome #{:draw :win :lose})
(s/def ::cause #{:give-up :time-out :no-moves})
(s/def ::result (s/keys :req-un [::outcome ::validation/id ::cause ::game]))

(defrecord Reply [reply-type, ^String id, data])
(s/def ::reply-type #{:state :move :end :error :turn-time :drop :cant-drop :mock})
(s/def ::errors #{:invalid-move :not-your-turn :game-doesnt-exist
                  :invalid-msg :unknown :not-enough-influence})
(s/def ::reply (s/and (s/keys :req-un [::reply-type ::validation/id ::data])
                      #(case (:reply-type %)
                         :end   (s/valid? ::outcome (-> % :data :outcome))
                         :error (s/valid? ::errors (:data %))
                         :mock  (s/valid? ::mock (:data %))
                         (any? %))))
