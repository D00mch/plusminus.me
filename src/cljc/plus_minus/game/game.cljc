(ns plus-minus.game.game
  (:require [clojure.spec.alpha :as s]
            [plus-minus.game.state :as st]))

(defn- move-max [state]
  (->> state st/valid-moves
       (apply max-key #(st/move-val state %))
       (st/move state)))

(defn- points-diff [hrz-turn {:keys [hrz-points vrt-points]}]
  ((if hrz-turn + -) (- hrz-points vrt-points)))

(defn predict [state turns-ahead]
  (cond (-> state st/moves? not) (update state :hrz-turn not)
        (<= turns-ahead 0) (move-max state)
        :else (->> (st/valid-moves state)
                   (map #(st/move state %))
                   (map #(predict % (dec turns-ahead)))
                   (apply max-key #(points-diff (:hrz-turn state) %)))))

(defn move-bot [{mvs :moves :as state}] ;; TODO: spec that state have moves
  (let [{pmvs :moves} (predict state 3)
        mv (->> mvs count (nth pmvs))]
    (st/move state mv)))

(defn on-game-end [{:keys [hrz-points vrt-points] :as state} usr-hrz-turn]
  (if (= hrz-points vrt-points)
    :draw
    (let [hrz-wins (> hrz-points vrt-points)]
      (if (= usr-hrz-turn hrz-wins) :win :lose))))

(defn play
  "Simulate game til the end with best moves"
  [{:as state}]
  (->> (iterate move-bot state)
       (take-while #(st/moves? %))
       last
       move-bot))

(comment
  (def game-state (atom (st/state-template 3)))

  (add-watch game-state :bot
             (fn [key atom old-state new-state]
               (let [{:keys [hrz-turn] :as state} @atom]
                 (st/state-print state)
                 (cond (-> state st/moves? not) (do (remove-watch atom :bot)
                                                    (println "the end")),
                       (not hrz-turn) (reset! atom (move-bot state))))))

  (remove-watch game-state :bot)
  )
