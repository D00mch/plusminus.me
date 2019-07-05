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

(def ^:private thread-pool
  (Executors/newFixedThreadPool
   (+ 2 (. (Runtime/getRuntime) availableProcessors))))

(defn- reset-to-default! []
  (dosync (ref-set room/rooms {})
          (ref-set room/player-id->room-id {})
          (ref-set room/waiting-rooms {})))

(defn- submit-get-or-create! [size id]
  (let [latch (promise)]
    (.submit thread-pool
             (fn []
               (Thread/sleep (+ 10 (rand-int 50)))
               (dosync
                (room/get-or-create! size id)
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

    (is (= 0 (count @room/waiting-rooms)))
    (is (= (* (count size-vars) (/ count-per-size 2)) (count @room/rooms)))))


