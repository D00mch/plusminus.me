(ns plus-minus.test.multiplayer.room
  (:require [clojure.test :refer [deftest is testing]]
            [plus-minus.validation :as validation]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [plus-minus.routes.multiplayer.room :as room])
  (:import [java.util.concurrent Executors TimeUnit]))

(def ^:private game-deletion-sec 1)

(def ^:private thread-pool
  (Executors/newFixedThreadPool
   (+ 2 (. (Runtime/getRuntime) availableProcessors))))

(defn- remove-timers! []
  (->> (map second @room/rooms)
       (map :turn-timer)
       (map #(.cancel % true))
       doall))

(defn- reset-to-default! []
  (remove-timers!)
  (dosync (ref-set room/rooms {})
          (ref-set room/player->room {})
          (ref-set room/size->room {})))


;;************************* GAME CREATION *************************

(defn- submit-get-or-create! [size id]
  (let [latch (promise)]
    (.submit thread-pool
             (fn []
               (Thread/sleep (+ 10 (rand-int 50)))
               (dosync
                (binding [room/*turn-time-sec* game-deletion-sec]
                  (room/get-or-create! size id))
                (deliver latch :done))))
    latch))

(deftest get-or-create!
  (reset-to-default!)
  (let [size-vars      (range b/row-count-min b/row-count-max-excl)
        count-per-size 20
        sizes          (for [s size-vars
                             c (repeat count-per-size s)]
                         c)
        ids            (gen/sample (spec/gen ::validation/id) (count sizes))
        submitted      (doall (map #(submit-get-or-create! %1 %2) sizes ids))]

    (->> submitted (map deref) doall)

    (testing "all games have joined with two players; there are no wating rooms"
      (is (= 0 (count @room/size->room))))
    (testing "created as mutch rooms as expected"
      (is (= (/ (count sizes) 2) (count @room/rooms))))

    (testing "all states has waiting status after creation"
      (is (= (/ (count sizes) 2)
             (->> (map second @room/rooms)
                  (map :status)
                  (filter #(= % ::room/wait))
                  count))))

    ;; wait until turn-time elapsed
    (Thread/sleep (* 1000 game-deletion-sec))

    (testing "all games are removed after time elapsed"
      (is (= 0 (count @room/rooms))))
    (testing "all players are removed after time elapsed"
      (is (= 0 (count @room/player->room))))
    ))

;;************************* FIRST STATE EXCHANGE *************************

;; TODO: may be create games, turn timers off and than request the states.

(deftest state-request!
  (reset-to-default!)

  (let [sizes (gen/sample (spec/gen ::b/row-size) 100)
        ids   (gen/sample (spec/gen ::validation/id) (* 2 (count sizes)))
        games (map #(dosync (room/get-or-create! %1 %2))
                   (concat sizes sizes)
                   ids)]
    (doall games)
    (doseq [id ids]
      (room/state-request! id))

    (testing "all games have active status after requested by all the players"
      (is (= (count sizes)
             (->> (map second @room/rooms)
                  (map :status)
                  (filter #(= % ::room/active))
                  count))))

    (remove-timers!)))
