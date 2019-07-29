(ns plus-minus.test.multiplayer
  (:require [clojure.test :refer [deftest is testing]]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.routes.multiplayer.game :as game]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Reply ->Message map->Game]]
            [plus-minus.routes.multiplayer.room :as room]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.validation :as validation]
            [beicon.core :as rx]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.pprint :refer [pprint]]
            [plus-minus.game.game :as g]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [clojure.tools.logging :as log])
  (:import [io.reactivex.observers TestObserver]
           [io.reactivex.disposables CompositeDisposable Disposable]
           [java.util.concurrent CountDownLatch]))

(defn- reset-state! []
  (topics/reset-state!)
  (dosync (ref-set (var-get #'game/id->msgs) {})))

(def ^{:private true} log? true) ;; for testing with repl

(defn- log [& args] (when log? (apply println args)))

(defn- push! [type id data]
  (topics/publish :msg (->Message type id data)))

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

(defn- imitate-player
  ":move - fn state->move"
  [reply-obs player latch & {move :move-fn
                             :or {move bot-move}}]
  (rx/subscribe
   (->> reply-obs (rx/subscribe-on (rx/scheduler :thread)))
   (on-player-gets-message player (atom nil) latch move)
   #(println "imitate-player on-error" %)))

(defn- start-a-game [player1 player2 latch move-fn]
  (let [replies1 (->> (topics/consume :reply) (rx/filter #(= (:id %) player1)))
        replies2 (->> (topics/consume :reply) (rx/filter #(= (:id %) player2)))
        dis1     (imitate-player replies1 player1 latch :move-fn move-fn)
        dis2     (imitate-player replies2 player2 latch :move-fn move-fn)]
    ;; now when players subscribed, we can publish start game events
    (push! :new player1 3)
    (push! :new player2 3)
    (doto (CompositeDisposable.) (.add dis1) (.add dis2))))

(deftest play-games []
  (reset-state!)
  (let [game-disp (game/subscribe-message-processing)
        ids      (-> (spec/gen ::validation/id) (gen/sample 3) distinct)
        id-pairs (partition 2 ids)
        latch    (CountDownLatch. (-> (count id-pairs) (* 2)))
        games    (->> id-pairs
                      (map (fn [[p1 p2]] (start-a-game p1 p2 latch bot-move)))
                      doall)]
    (.await latch)
    (prn "about to dispose")
    (doseq [g games] (.dispose ^Disposable g))
    (is (empty? (deref (var-get #'game/id->msgs))))
    (.dispose game-disp)))

#_(deftest play-with-errors []
  (let [latch (CountDownLatch. 2)
        game  (start-a-game "bob" "regeda" latch bot-move)]
    (.await latch)
    (prn "about to dispose")
    (.dispose ^Disposable game)
    (is (empty? @room/rooms))))

;; TODO: test give-up and state-request

;;;; new

;; (do
;;   (def game-disp (game/subscribe-message-processing))
;;   (push! :new "bob" 3)
;;   (push! :new "bol" 3))

;;  (def game
;;    (map->Game
;;     {:state {:board {:row-size 3, :cells [-5 -7 -4 -7 -1 0 6 -8 7]}, :start 3, :moves [], :hrz-points 0, :vrt-points 0, :hrz-turn true}, :game-id 11, :created #inst "2019-07-29T11:41:49.251-00:00", :player1 "bob", :player2 "bol", :player1-hrz false}))

;;  (do (prn (room/player-turn-id game))
;;      (prn (st/valid-moves (:state game)))
;;      (st/state-print (:state game)))

;;  (let [mv 4
;;        nm "bol"]
;;    (push! :move nm mv)
;;    (def game (update game :state st/move mv)))

;;  (do (.dispose game-disp)
;;      (topics/reset-state!))
