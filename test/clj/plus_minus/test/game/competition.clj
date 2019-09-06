(ns plus-minus.test.game.competition
  (:require [clojure.test :refer [deftest is testing]]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as st]
            [clojure.spec.alpha :as s]
            [clojure.math.combinatorics :as combo]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.game.game :as g]))

(def agressive-bot
  {:bot-name "AgressiveBot"
   :move-fn  #(g/move-bot % 3)})

(def safe-bot
  {:bot-name "Safeguard"
   :move-fn  #(g/move-bot-safe % 3)})

(def max-bot
  {:bot-name "Maxim"
   :move-fn #'g/move-max})

(def rand-bot
  {:bot-name "Radion"
   :move-fn  #(let [moves (st/valid-moves %)]
                (st/move % (nth moves (-> moves count rand))))})

(def wise-bot
  {:bot-name "Vas@"
   :move-fn (fn [{mvs :moves {r :row-size} :board :as state}]
              (let [max-mvs (* r r)
                    mvs%    (int (* 100 (/ (count mvs) max-mvs )))]
                (cond (< mvs% 30) (g/move-bot state 3)
                      (< mvs% 50) (g/move-bot state 3)
                      (< mvs% 70) (g/move-bot-safe state 1)
                      :else       (g/move-bot-safe state 1))))})

(defn play-game [{b1 :bot-name f1 :move-fn} {b2 :bot-name f2 :move-fn} state]
  (let [b1-hrz (> (rand 2) 1)]
    (loop [{hrz :hrz-turn :as state} state]
      (if (st/moves? state)
        (recur ((if (= hrz b1-hrz) f1 f2)
                state))
        {:state state
         :hrz   (if b1-hrz b1 b2)
         :win   (let [b1-result (g/on-game-end state b1-hrz)]
                  (case b1-result
                    :win b1
                    :lose b2
                    :draw))}))))

(defn ->percent [all names->values]
  (->> (seq names->values)
       (map (fn [[name val]]
              [name (int (* 100 (/ val all)))]))))

(defn competition []
  (let [all        1000
        states     #_(gen/sample (s/gen ::st/state) all)
        (map st/state-template (take all (repeat 6)))
        gamers     [agressive-bot max-bot rand-bot wise-bot safe-bot]
        game-pairs (combo/combinations gamers 2)]

    (->> game-pairs
         (pmap (fn [[p1 p2]] (pmap #(play-game p1 p2 %) states)))
         (apply concat )
         (reduce (fn [acc {win-name :win}] (update acc win-name #(inc (or % 0)))) {})
         (->percent (* (count game-pairs) all)))))
