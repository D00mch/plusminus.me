(ns plus-minus.test.multiplayer
  (:require [clojure.test :refer [deftest is testing]]
            [plus-minus.routes.multiplayer.topics :as topics
             :refer [->Reply ->Message]]
            [plus-minus.routes.multiplayer.room :as room]
            [plus-minus.validation :as validation]
            [beicon.core :as rx]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [clojure.pprint :refer [pprint]]
            [plus-minus.game.game :as game])
  (:import [io.reactivex.observers TestObserver]
           [io.reactivex.disposables CompositeDisposable Disposable]
           [java.util.concurrent CountDownLatch]))


;;************************* STATE *************************

(def ^{:private true} log? false) ;; for testing with repl

(defn- subscribe-all []
  (def matcher-subs (plus-minus.routes.multiplayer.matcher/subscribe))
  (def room-subs (plus-minus.routes.multiplayer.room/subscribe)))

(defn- unsubscribe-all []
  (.dispose ^Disposable matcher-subs)
  (.dispose ^Disposable room-subs))

(defn- reset-states! []
  (unsubscribe-all)
  (room/reset-state!)
  (topics/reset-state!))

;;**************************************************

(defn- log [& args]
  (when log? (apply println args)))

(defn- push! [type id data]
  (topics/publish :msg (->Message type id data)))

(defn- on-player-gets-message [player game latch move]
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
        (if (push! :move player (move (:state @game)))
          (log player "moved")
          (log player "failed to move"))
        (log player "says: not my turn")))))

(defn- bot-move [state]
  (game/some-move state))

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
  (subscribe-all)
  (let [ids      (-> (spec/gen ::validation/id) (gen/sample 1000) distinct)
        id-pairs (partition 2 ids)
        latch    (CountDownLatch. (-> (count id-pairs) (* 2)))
        games    (->> id-pairs
                      (map (fn [[p1 p2]] (start-a-game p1 p2 latch bot-move)))
                      doall)]
    (.await latch)
    (prn "about to dispose")
    (doseq [g games] (.dispose ^Disposable g))
    (unsubscribe-all)
    (is (empty? @room/rooms))))

(deftest play-with-errors []
  (subscribe-all)
  (let [latch (CountDownLatch. 2)
        game  (start-a-game "bob" "regeda" latch rand-move)]
    (.await latch)
    (prn "about to dispose")
    (.dispose ^Disposable game)
    (unsubscribe-all)
    (is (empty? @room/rooms))))
