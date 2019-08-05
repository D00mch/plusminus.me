(ns plus-minus.test.multiplayer
  (:require [clojure.core.async :as async :refer [<!! <! >! go go-loop chan]]
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

(def players-num 2000) ;; TODO: change with 2000 and make it work
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

(defn- try-move [game player move]
  (if (= player (room/player-turn-id game))
    (do (push! :move player (move (:state game)))
        (log player "moved"))
    (log player "says: not my turn")))

;; TODO: remove, tmp to debug
(def ex-chan (chan))
(go-loop []
  (when-let [e (<! ex-chan)]
    (log/error e "shit!")
    (when-let [ed (ex-data e)] (pprint ed))
    (recur)))

(defn- imitate-player!
  ":move - fn state->move"
  [reply> player & {move :move-fn :or {move bot-move}}]
  (go-loop [game {}]
    (when-let [{:keys [reply-type data] :as reply} (<! reply>)]
      (log player "gets" reply)
      (case reply-type
        :state (do (try-move data player move)
                   (recur data))
        :move  (if (not= game {})
                 (let [new-game (update game :state st/move data)]
                   (try-move new-game player move)
                   (recur new-game))
                 (>! ex-chan (ex-info "game not init on move" {:reply reply})))
        :error (do (log "error" data)
                   (>! ex-chan (ex-info "error to player" {:reply reply}))
                   (if (= game {})
                     (>! ex-chan (ex-info "game not init on error" {:reply reply}))
                     (try-move game player move))
                   (recur game))
        :end   (log "the end")))))

(defn- start-a-game! [player1 player2 move-fn]
  (let [replies1> (topics/tap! :reply (chan 1 (filter #(= (:id %) player1))
                                            #(log/error "replies1>" %)))
        replies2> (topics/tap! :reply (chan 1 (filter #(= (:id %) player2))
                                            #(log/error "replies2>" %)))]
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
  (when log? (print-replies!))
  (let [ids      (-> (spec/gen ::validation/id) (gen/sample players-num) distinct)
        id-pairs (partition 2 ids)
        end>     (topics/tap! :reply
                              (chan 1 (comp (filter #(= (:reply-type %) :end))
                                            (drop (- (* 2 (count id-pairs)) 1)))))]
    (game/listen!)
    (doseq [[p1 p2] id-pairs]
      (start-a-game! p1 p2 bot-move))

    ;; if comment this, 'fail... no more than 1024...' will appear too
    (println "last reply" (<!! end>))

    #_(is (empty? (deref (var-get #'game/id->msgs>))))))

(comment

  (do
    (let [replies> (topics/tap! :reply (chan))]
      (go-loop []
        (when-let [v (<! replies>)]
          (if (->> (:data v) (instance? Game))
            (pprint (dissoc (:data v) :created))
            (pprint v))
          (recur))))
    (game/listen!)
    (topics/push! :msg (->Message :new "bob" 3))
    (topics/push! :msg (->Message :new "bol" 3)))

  (def game
    {:state
     {:board {:row-size 3, :cells [8 -6 -6 -9 0 6 3 -8 2]},
      :start 5,
      :moves [],
      :hrz-points 0,
      :vrt-points 0,
      :hrz-turn true},
     :game-id 29,
     :player1 "bob",
     :player2 "bol",
     :player1-hrz true})

  (do (prn (room/player-turn-id game))
      (prn (st/valid-moves (:state game)))
      (st/state-print (:state game)))

  (let [mv 0
        nm "bob"]
    (push! :move nm mv)
    (def game (update game :state st/move mv)))

  (do
    (reset-state!)
    (topics/reset-state!))

  (deref (var-get #'game/id->msgs>))
  )
