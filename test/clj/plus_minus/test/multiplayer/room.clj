(ns plus-minus.test.multiplayer.room
  (:require [clojure.test :refer [deftest is testing]]
            [plus-minus.validation :as validation]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as game]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as spec]
            [plus-minus.routes.multiplayer.room :as room])
  (:import [java.util.concurrent Executors TimeUnit]))

(def ^:private game-deletion-sec 1)

(def ^:private thread-pool
  (Executors/newFixedThreadPool
   (+ 2 (. (Runtime/getRuntime) availableProcessors))))

(defn- submit [task]
  (let [latch (promise)]
    (.submit thread-pool
             (fn []
               (task)
               (deliver latch :done)))
    latch))

(defn- await-promises [p] (->> p (map deref) doall))

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

(defn- submit-get-or-create!
  "return promise"
  [size id]
  (submit (fn []
            (Thread/sleep (+ 5 (rand-int 30)))
            (dosync
             (binding [room/*turn-time-sec* game-deletion-sec]
               (room/get-or-create! size id))))))

(deftest get-or-create!
  (reset-to-default!)
  (let [size-vars      (range b/row-count-min b/row-count-max-excl)
        count-per-size 50
        sizes          (for [s size-vars
                             c (repeat count-per-size s)]
                         c)
        ids            (gen/sample (spec/gen ::validation/id) (count sizes))
        submitted      (doall (map #(submit-get-or-create! %1 %2) sizes ids))]

    (await-promises submitted)

    (testing "all games have joined with two players; there are no wating rooms"
      (is (= 0 (count @room/size->room))))
    (testing "created as mutch rooms as expected"
      (is (= (/ (count sizes) 2) (count @room/rooms))))

    (testing "all states has waiting status after creation"
      (is (= (/ (count sizes) 2)
             (->> (map second @room/rooms)
                  (map :status)
                  (filter #(= % :wait))
                  count))))

    ;; wait until turn-time elapsed
    (Thread/sleep (* 1000 game-deletion-sec))

    (testing "all games are removed after time elapsed"
      (is (= 0 (count @room/rooms))))
    (testing "all players are removed after time elapsed"
      (is (= 0 (count @room/player->room))))
    ))

;;************************* FIRST STATE EXCHANGE *************************

(defn- create-game! [size player1 player2]
  (dosync (room/get-or-create! size player1))
  (dosync (room/get-or-create! size player2)))

(defn- submit-state-req!
  "return promise"
  [id]
  (submit #(do (Thread/sleep (+ 5 (rand-int 10)))
               (room/state-request! id))))

(deftest state-request!
  (reset-to-default!)
  (let [ids   (-> ::validation/id spec/gen (gen/sample 800) distinct)
        games (doseq [[id1 id2] (partition 2 ids)]
                (create-game! (gen/generate (spec/gen ::b/row-size)) id1 id2))
        tasks (->> ids (map #(submit-state-req! %)) doall)]
    (await-promises tasks)
    (testing "all games have active status after requested by all the players"
      (is (= (-> ids count (quot 2))
             (->> (map second @room/rooms)
                  (map :status)
                  (filter #(= % :active))
                  count))))
    (remove-timers!)))

;;************************* MOVES *************************

(defn- submit-moves! [player1 player2 played-game game-id]
  (submit
   #(do
      (Thread/sleep (+ 5 (rand-int 30)))
      (room/state-request! player1)
      (room/state-request! player2)
      (doseq [mv (-> played-game :moves)
              :let [id (room/player-turn-id (get @room/rooms game-id))]]
        (room/move! [:move id mv])))))

(deftest moves!
  (reset-to-default!)
  (let [ids (-> (spec/gen ::validation/id) (gen/sample 100) distinct)
        id-pairs (partition 2 ids)
        moves    (-> (for [[player1 player2] id-pairs
                           :let [game-id (create-game! 3 player1 player2)
                                 game    (get @room/rooms game-id)
                                 played  (-> game :game-state game/play)]]
                       (submit-moves! player1 player2 played game-id))
                     doall)]
    (await-promises moves)
    (testing "all moves made -> all games played"
      (is (= (count @room/rooms) 0)))))


;;************************* GAME JUDGE *************************

(deftest judge! []
  (let [player1 (gen/generate (spec/gen ::validation/id))
        player2 (gen/generate (spec/gen ::validation/id))
        size    (gen/generate (spec/gen ::b/row-size))]

    (testing "when game wasnt requested, result is disconnect"
      (let [game-id (create-game! size player1 player2)
            game    (get @room/rooms game-id)]
        (is (= (room/game-end! game-id)
               [:end [:disconnect (:player1-id game)]]))))

    (testing "when both players seen game - lose one whose turn"
      (let [game-id (create-game! size player1 player2)
            game    (get @room/rooms game-id)]
        (room/state-request! player1)
        (room/state-request! player2)
        (is (= (room/game-end! game-id)
               [:end [:win (room/player-turn-id game)]]))))))
