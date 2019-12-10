(ns plus-minus.test.routes.state
  (:require [plus-minus.game.game :as g]
            [plus-minus.db.core :as db]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is testing]]
            [plus-minus.game.progress :as p]
            [plus-minus.routes.services.state :as state]))

(defn- simulated-game []
  (update (gen/generate (s/gen ::state/result-state))
          :state
          #(g/play %)))

(defn- simulated-games [count]
  (->> (range (inc count))
       (map (fn [_] (simulated-game)))))

  ;; "json to test it with swagger"
  ;; [
  ;;  {
  ;;   "state": {
  ;;      "board": {
  ;;         "row-size": 5,
  ;;         "cells": [-7,7,1,-6,-8,8,-7,5,-9,-7,6,0,-7,0,0,5,6,8,-7,8,5,7,6,-7,-7]},
  ;;      "moves": [21,16,15,19,24,23,8,5,20,22,17,18,3,1,6,9,4,2,7],
  ;;      "hrz-turn": true,
  ;;      "start": 5,
  ;;      "hrz-points": 15,
  ;;      "vrt-points": -7
  ;;      },
  ;;   "usr-hrz": true,
  ;;   "give-up": false,
  ;;   "id": "dumch"
  ;;   }]

(deftest states->stats
  (testing "can reduce random states to proper stats"
    (let [states (simulated-games 20)
          stats  (#'state/states->stats
                  states
                  #_(-> {:id "arturdumchev"} db/get-statistics :statistics)
                  p/empty-stats)]
      (is (s/valid? ::p/statistics (:statistics stats))))))
