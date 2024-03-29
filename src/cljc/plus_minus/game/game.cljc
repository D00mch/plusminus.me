(ns plus-minus.game.game
  (:require [clojure.spec.alpha :as s]
            [plus-minus.game.state :as st]))

(defn- move-max [state]
  (->> state st/valid-moves
       (apply max-key #(st/move-val state %))
       (st/move state)))

(defn- points-diff [hrz-turn {:keys [hrz-points vrt-points] :as state}]
  ((if hrz-turn + -) (- hrz-points vrt-points)))

(defn- states-comparator
  "prefers early win; best state at first"
  [hrz]
  (fn [s1 s2]
    (let [m1 (-> s1 :moves count)
          m2 (-> s2 :moves count)
          d1 (points-diff hrz s1)
          d2 (points-diff hrz s2)
          n  (- m1 m2)]
      (if (and (< 0 d1) (< 0 d2))
        (if (= n 0) (- d2 d1) n)
        (- d2 d1)))))

(defn predict
  "choose best move in turns-ahead horizon"
  [state turns-ahead]
  (cond (-> state st/moves? not) (update state :hrz-turn not)
        (<= turns-ahead 0) (move-max state)
        :else (->> (st/valid-moves state)
                   (map #(st/move state %))
                   (map #(predict % (dec turns-ahead)))
                   (apply max-key #(points-diff (:hrz-turn state) %)))))

(defn move-bot [{mvs :moves :as state} & [prediction]]
  (let [{pmvs :moves} (predict state (or prediction 3))
        mv (->> mvs count (nth pmvs))]
    (st/move state mv)))

(defn- scenarios [state turns-ahead]
  (->> (st/valid-moves state)
       (map #(st/move state %))
       (map #(predict % (dec turns-ahead)))))

(defn move-bot-safe
  "just like move-bot, but prevents suicide moves and prefers quick wins"
  [{mvs :moves hrz :hrz-turn :as state} & [prediction]]
  (let [states (scenarios state (or prediction 1))
        states (sort (states-comparator hrz) states)
        {pmvs :moves} (first states)
        mv            (->> mvs count (nth pmvs))]
    #_(doseq [{m :moves :as s} states] (prn "m: " m (points-diff hrz s)))
    (st/move state mv)))

(defn move-clever-bot [{mvs :moves {r :row-size} :board :as state} & [prediction]]
   (let [max-mvs (* r r)
         mvs%    (int (* 100 (/ (count mvs) max-mvs )))]
     (cond (< mvs% 50) (move-bot state (or prediction 3))
           :else       (move-bot-safe state 1))))

(defn some-move [state]
  (-> state move-bot :moves last))

(defn on-game-end [{:keys [hrz-points vrt-points] :as state} usr-hrz-turn]
  (if (= hrz-points vrt-points)
    :draw
    (let [hrz-wins (> hrz-points vrt-points)]
      (if (= usr-hrz-turn hrz-wins) :win :lose))))

(defn play
  "Simulate game til the end with best moves"
  [{:as state}]
  (if (st/moves? state)
    (recur (move-bot state))
    state))

(defn calc-iq [{:keys [win lose draw]}]
  (if (= 0 win lose draw)
    100
    (let [all     (+ win lose draw)
          win%    (-> (* 0.5 draw) (+ win) (/ all))
          mid-iq  100
          grow    15
          cal-iq  (* 2 mid-iq win%)]
      (int (/ (+ (* all cal-iq) (* grow mid-iq))
              (+ grow all))))))
