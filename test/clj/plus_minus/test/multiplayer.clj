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
           [io.reactivex.disposables CompositeDisposable Disposable]))

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

(defn- log [prn & args]
  (when prn (apply println args)))

(defn- push! [type id data]
  (topics/publish :msg (->Message type id data)))

(defn- on-player-gets-message [player game prn]
  (fn [{:keys [reply-type data]}]
    (log prn player "gets" reply-type)
    (case reply-type
      :state (reset! game data)
      :move  (swap! game update :state st/move data)
      :end   (log prn "the end")
      :error (log prn "error " data))
    (when-not (= reply-type :end)
      (if (= player (room/player-turn-id @game))
        (if (push! :move player (game/some-move (:state @game)))
          (log prn player "moved")
          (log prn player "failed to move"))
        (log prn player "says: not my turn")))))

(defn- imitate-player [reply-obs player & {prn :print :or {prn false}}]
  (rx/subscribe
   (->> reply-obs (rx/subscribe-on (rx/scheduler :thread)))
   (on-player-gets-message player (atom nil) prn)
   #(println "imitate-player on-error" %)))

(defn- start-a-game [player1 player2]
  (let [replies1 (->> (topics/consume :reply) (rx/filter #(= (:id %) player1)))
        replies2 (->> (topics/consume :reply) (rx/filter #(= (:id %) player2)))
        dis1     (imitate-player replies1 player1 :print false)
        dis2     (imitate-player replies2 player2 :print false)]
    ;; now when players subscribed, we can publish start game events
    (push! :new player1 3)
    (push! :new player2 3)
    (doto (CompositeDisposable.) (.add dis1) (.add dis2))))

(deftest play-games []
  (subscribe-all)
  (let [ids      (-> (spec/gen ::validation/id) (gen/sample 100) distinct)
        id-pairs (partition 2 ids)
        games    (->> id-pairs (map (fn [[id1 id2]] (start-a-game id1 id2))) doall)]
    ;; TODO: fix with TestObserver
    (Thread/sleep 1000) ;; Sorry, no time to wirte lots of reifies for TestObserver
    (prn "about to dispose")
    (doseq [g games] (.dispose ^Disposable g))
    (unsubscribe-all)
    (is (empty? @room/rooms))))
