(ns plus-minus.test.multiplayer
  (:require [clojure.core.async :as async :refer [>!! <!! <! >! go go-loop chan]]
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
  (:import [plus_minus.multiplayer.contract Game Message]
           java.util.concurrent.CountDownLatch))

(defn- reset-state! []
  (topics/reset-state!)
  (reset! (var-get #'game/id->msgs>) {}))

(def ^{:private true} log? false) ;; for testing with repl

(defn- log [& args] (when log? (log/info (str/join " " args)) #_(apply println args)))

(defn- push! [type id data]
  (topics/push! :msg (->Message type id data)))

(defn- on-player-gets-message
  "game is atom, latch - CountDownLatch"
  [player game latch move]
  (fn [{:keys [reply-type data]}]
    (log player "gets" reply-type)
    (case reply-type
      :state (reset! game data)
      :move  (swap! game update :state st/move data)
      :end   (do
               (.countDown ^CountDownLatch latch)
               (log "the end"))
      :error (log "error " data))
    (when-not (= reply-type :end)
      (if (= player (room/player-turn-id @game))
        (do (push! :move player (move (:state @game)))
            (log player "moved"))
        (log player "says: not my turn")))))

(defn- bot-move [state]
  (g/some-move state))

(defn- rand-move [state]
  (if (= 0 (rand-int 2))
    (rand-int (st/size state))
    (bot-move state)))

(defn- imitate-player!
  ":move - fn state->move"
  [reply> player latch & {move :move-fn :or {move bot-move}}]
  (let [on-reply (on-player-gets-message player (atom nil) latch move)]
    (go-loop []
      (when-let [r (<! reply>)]
        (on-reply r)
        (recur)))))

(defn- start-a-game! [player1 player2 latch move-fn]
  (let [replies1> (topics/tap! :reply (chan 1 (filter #(= (:id %) player1))))
        replies2> (topics/tap! :reply (chan 1 (filter #(= (:id %) player2))))]
    (imitate-player! replies1> player1 latch :move-fn move-fn)
    (imitate-player! replies2> player2 latch :move-fn move-fn)
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
  (let [ids      (-> (spec/gen ::validation/id) (gen/sample 1000) distinct)
        id-pairs (partition 2 ids)
        latch    (CountDownLatch. (-> (count id-pairs) (* 2)))]
    (game/listen!)
    (doseq [[p1 p2] id-pairs]
      (start-a-game! p1 p2 latch bot-move))
    (.await latch)
    (prn "about to dispose")
    (is (empty? (deref (var-get #'game/id->msgs>))))))

;; #_(deftest play-with-errors []
;;   (let [latch (CountDownLatch. 2)
;;         game  (start-a-game "bob" "regeda" latch bot-move)]
;;     (.await latch)
;;     (prn "about to dispose")
;;     (.dispose ^Disposable game)
;;     (is (empty? @room/rooms))))

;; ;; TODO: test give-up and state-request

;; ;;;; new

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
