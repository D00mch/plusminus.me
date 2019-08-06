(ns plus-minus.test.multiplayer
  (:require [clojure.core.async :as async :refer [<!! <! >! >!! go go-loop chan]]
            [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [plus-minus.game.board :as b]
            [plus-minus.game.game :as g]
            [plus-minus.game.state :as st]
            [plus-minus.multiplayer.contract
             :as contract :refer [->Message ->Reply map->Game]]
            [plus-minus.routes.multiplayer.game :as game]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.routes.multiplayer.reply :as room]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.validation :as validation]
            [clojure.string :as str])
  (:import [plus_minus.multiplayer.contract Game Message]))

(defn- reset-state! []
  (topics/reset-state!)
  (reset! (var-get #'game/id->msgs>) {}))

;; tests are now implemented as go-loops, so it's impossible to have > 1024 players
(def players-num 1000)
(def log? false) ;; for testing with repl

(defn- log [& args] (when log? (log/info (str/join " " args)) #_(apply println args)))

(defn- push! [type id data]
  (topics/push! :msg (->Message type id data)))

(defn- bot-move [state]
  (g/some-move state))

(defn- rand-move [state]
  (if (= 0 (rand-int 2))
    (rand-int (st/size state))
    (bot-move state)))

(defn- imitate-player!
  ":move - fn state->move"
  [reply> player & {move :move-fn :or {move bot-move}}]
  (go-loop [game (atom {})]
    (when-let [{:keys [reply-type data] :as reply} (<! reply>)]
      (log player "gets" reply-type)
      (case reply-type
        :state     (reset! game data)
        :move      (swap! game update :state st/move data)
        :error     (log "error" data)
        :turn-time (log "turn-time")
        :end       (do (async/close! reply>)
                   (log "the end")))
      (when-not (= reply-type :end)
        (if (= player (room/player-turn-id @game))
         (do (>! (topics/in-chan :msg)
                 (->Message :move player (move (:state @game))))
             (log player "moved"))
         (log player "says: not my turn")))
      (recur game))))

(defn- start-a-game! [player1 player2 move-fn]
  (let [replies1> (topics/tap! :reply (chan 1 (filter #(= (:id %) player1))))
        replies2> (topics/tap! :reply (chan 1 (filter #(= (:id %) player2))))]
    (imitate-player! replies1> player1 :move-fn move-fn)
    (imitate-player! replies2> player2 :move-fn move-fn)
    (push! :new player1 3)
    (push! :new player2 3)))

(defn print-replies! []
  (let [replies> (topics/tap! :reply (chan))]
    (go-loop []
      (when-let [v (<! replies>)]
        (if (->> (:data v) (instance? Game))
          (pprint (dissoc (:data v) :created))
          (log/info "getting value" v))
        (recur)))))

(deftest play-games []
  (reset-state!)
  #_(when log? (print-replies!))
  (game/listen!)
  (let [ids      (-> (spec/gen ::validation/id) (gen/sample players-num) distinct)
        id-pairs (partition 2 ids)
        end>     (topics/tap! :reply
                              (chan 1 (comp (filter #(= (:reply-type %) :end))
                                            (drop (- (* 2 (count id-pairs)) 1)))))]
    (doseq [[p1 p2] id-pairs]
      (start-a-game! p1 p2 bot-move))
    (println "last reply:" (<!! end>))
    (is (empty? (deref (var-get #'game/id->msgs>))))
    (game/close!)))

(deftest error-games []
  (reset-state!)
  (game/listen!)
  (let [xf   (comp (filter #(= (:reply-type %) :end)) (drop 1))
        end> (topics/tap! :reply (chan 1 xf))]
    (start-a-game! "bob" "regeda" rand-move)
    (println "last reply:" (<!! end>))
    (is (empty? (deref (var-get #'game/id->msgs>))))
    (game/close!)))


(comment

  ;; TODO: remove, tmp to debug
  (def ex-chan (chan))
  (go-loop []
    (when-let [e (<! ex-chan)]
      (log/error e "shit!")
      (when-let [ed (ex-data e)] (pprint ed))
      (recur)))


  (let [replies> (topics/tap! :reply (chan))]
    (go-loop []
      (when-let [v (<! replies>)]
        (if (->> (:data v) (instance? Game))
          (pprint (dissoc (:data v) :created))
          (pprint v))
        (recur))))

  (do
    (game/listen!)
    (topics/push! :msg (->Message :new "bob" 3))
    (topics/push! :msg (->Message :new "bol" 3)))

  (def game
    {:state
     {:board {:row-size 3, :cells [-3 -5 -2 7 -7 -8 9 -5 9]},
      :start 1,
      :moves [],
      :hrz-points 0,
      :vrt-points 0,
      :hrz-turn true},
     :game-id 8,
     :player1 "bob",
     :player2 "bol",
     :player1-hrz false,
     :updated 1565078256386})

  (do (prn (room/player-turn-id game))
      (prn (st/valid-moves (:state game)))
      (st/state-print (:state game)))

  (let [mv 5
        nm "bol"]
    (push! :move nm mv)
    (def game (update game :state st/move mv)))

  (game/close!)
  (reset-state!)

  (deref (var-get #'game/id->msgs>))
  )
