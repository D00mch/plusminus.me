(ns plus-minus.test.game.game
  (:require [clojure.test :refer [deftest is testing]]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.spec.test.alpha :as stest]))

(defn ns-specs-test [spec-f]
  (let [result (-> (spec-f) stest/summarize-results)]
    (is (= (:total result) (:check-passed result)))))

(deftest test-app
  (testing "board"
    (ns-specs-test b/test-defns))
  (testing "state"
    (ns-specs-test st/test-defns)))
