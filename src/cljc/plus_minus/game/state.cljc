(ns plus-minus.game.state
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [plus-minus.game.board :as b]
            #?(:clj [clojure.term.colors :as colors])))

(defn state-template [row-size]
  {:board      (b/generate row-size)
   :start      (rand-int (* row-size row-size))
   :moves      []  ;; stack
   :hrz-points 0
   :vrt-points 0
   :hrz-turn   true})

(defn size [{{c :cells} :board}] (count c))
(defn last-move [{s :start mvs :moves}] (or (peek mvs) s))

(defn valid-moves [{:keys [hrz-turn board moves] :as state}]
  (let [{:keys [x y]} (b/bcoords board (last-move state))]
    (->> (range (:row-size board))
         (mapv #(if hrz-turn [% y] [x %]))
         (mapv #(b/bxy->index board (% 0) (% 1)))
         (filterv #(not (some #{%} moves))))))

(defn valid-move? [state m] (->> (valid-moves state) (some #{m})))

(defn state-print [{:keys [board hrz-turn moves hrz-points vrt-points] :as state}]
  (println "h: " hrz-points (if hrz-turn "<- turn" ""))
  (println "v: " vrt-points (if hrz-turn "" "<- turn"))
  (println "moves: " (map #(b/bcoords board %) moves))
  (dotimes [i (b/bcount board)]
    (let [{x  :x y  :y} (b/bcoords board i)
          color?    (valid-move? state i)
          new-line? (= (rem (inc x) (:row-size board)) 0)
          val       (nth (:cells board) i)
          hidden?   (some #{i} moves)
          val       (cond hidden? " *"
                          (neg? val) val
                          :else (str " " val))]
      (print (if color? #?(:clj (colors/red val)
                           :cljs val) val))
      (print "|")
      (when new-line?
        (println)))))

(defn log-last-move [{:keys [board hrz-turn moves] :as state}]
  (if (seq moves)
    (let [last-move-hrz (not hrz-turn)]
      (println "move: " (b/bcoords board (last-move state))
               (nth (:cells board) (last-move state))
               "|" (if last-move-hrz "hrz" "vrt")))
    (println "there are no moves yet."))
  (state-print state)
  (println))

(defn- make-move [{board :board :as state} move]
  (-> state
      (update :moves conj move)
      (update (if (:hrz-turn state) :hrz-points :vrt-points)
              + (nth (:cells board) move))
      (update :hrz-turn not)))

(defn rewind [{moves :moves :as state}
              & {s :steps, log :log :or {s (count moves), log false}}]
  (loop [s (min s (count moves))
         {board :board, moves :moves :as state} state]
    (when log (log-last-move state))
    (if (> s 0)
      (recur
       (dec s)
       (-> state
           (update :moves pop)
           (update (if (:hrz-turn state) :vrt-points :hrz-points)
                   - (nth (:cells board) (peek moves)))
           (update :hrz-turn not)))
      state)))

(defn move-val [{{cells :cells} :board} mv] (nth cells mv))
(defn moves? [state] (-> state valid-moves seq boolean))
(defn move [state mv] (make-move state mv))

(defn simulate-game [{:keys [start moves board hrz-turn]} & {log :log :or {log false}}]
  (loop [[m & mvs] moves
         sstate (assoc (state-template (:row-size board))
                       :start    start
                       :board    board
                       :hrz-turn (if (-> moves count even?) hrz-turn (not hrz-turn)))]
    (when log (log-last-move sstate))
    (if m
      (recur mvs (make-move sstate m))
      sstate)))

(defn rand-move "rand move or nil" [state] (first (shuffle (valid-moves state))))

(defn valid-state? [state] (s/valid? ::state->simulated state))

;;************************* GENERATORS *************************

(defn- moves-generator
  [& {r :row-size, h :hrz-turn
      :or {r (gen/generate (s/gen ::b/row-size))
           h (gen/generate (gen/boolean))}}]
  (gen/fmap
   (fn [board]
     (loop [mcount  (rand-int (* r r))
            h       h
            state   (state-template r)]
       (if (or (= 0 mcount) (-> state valid-moves empty?))
         (vec (:moves state))
         (recur (dec mcount)
                (not h)
                (make-move state (rand-move state))))))
   (b/board-generator :row-size r)))

(defn- state-generator [& {r :row-size, :or {r (gen/generate (s/gen ::b/row-size))}}]
  (gen/fmap
   (fn [b]
     (let [h (gen/generate (gen/boolean))
           m (gen/generate (moves-generator :row-size r, :hrz-turn h))]
       (simulate-game
        (assoc (state-template r)
               :board b
               :hrz-turn (if (-> m count even?) h (not h))
               :moves (vec m)))))
   (b/board-generator :row-size r)))

(defn- move-params-generator []
  (gen/fmap
    (fn [{moves :moves :as state}]
      (let [rstate (rewind state :steps 1)
            mv     (or (peek moves) (first (valid-moves rstate)))]
        [rstate mv]))
    (state-generator)))

;;************************* SPECS *************************

(s/def ::moves
  (s/with-gen
    (s/coll-of ::b/index
               :distinct true
               :max-count b/board-count-max-excl
               :kind vector?)
    moves-generator))

(s/def ::hrz-turn boolean?)
(s/def ::start    ::b/index)
(s/def ::hrz-points int?)
(s/def ::vrt-points int?)

(s/def ::state
  (s/with-gen
    (s/and (s/keys :req-un [::b/board ::moves ::hrz-turn ::start
                            ::hrz-points ::vrt-points])
           #(every? (fn [move] (b/ind-in-board? move (:board %)))
                    (:moves %)))
    state-generator))

(s/def ::new-state
  (s/and ::state
         #(= (:hrz-points %) 0)
         #(= (:vrt-points %) 0)
         #(-> % :moves empty?)))

(s/def ::state->rewinded
  (s/with-gen
    (s/conformer
    (fn [{:keys [moves hrz-turn] :as state}]
      (let [{rhrz-turn :hrz-turn :as rstate} (rewind state)]
        (if (and (s/valid? ::state state)
                 (s/valid? ::new-state rstate)
                 ((if (-> moves count even?) = not=) hrz-turn rhrz-turn))
          rstate
          ::s/invalid))))
    state-generator))

(s/def ::state->simulated
  (s/with-gen
    (s/conformer
     (fn [state] (if (= state (simulate-game state)) state ::s/invalid)))
    state-generator))

(s/def ::move-params
  (s/with-gen
    (s/and (s/cat :state (s/spec ::state->simulated), :move ::b/index)
           #(valid-move? (:state %) (:move %)))
    move-params-generator))

(s/fdef valid-moves
  :args (s/cat :state (s/spec ::state->rewinded))
  :fn   (fn [{{{{r :row-size} :board} :state} :args, mvs :ret}]
          (every? #(< % (* r r)) mvs))
  :ret  ::moves)

(s/fdef move
 :args ::move-params
 :fn   (fn [{{r-board :board, r-moves :moves} :ret
             {{a-board :board, a-moves :moves} :state a-move :move} :args}]
         (and (= r-moves (conj a-moves a-move))
              (= r-board a-board)))
 :ret  ::state)

(s/fdef move-val
  :args ::move-params
  :fn   (fn [{cell :ret, {{{cells :cells} :board} :state, mv :move} :args}]
          (= cell (nth cells mv)))
  :ret  ::b/cell)

(s/fdef moves?
  :args (s/cat :state (s/spec ::state->simulated))
  :ret  boolean?)

(s/fdef last-move
  :args (s/cat :state (s/spec ::state->simulated))
  :fn   (fn [{{{{r :row-size} :board} :state} :args mv :ret}]
           (< mv (* r r)))
  :ret   ::b/index)

(defn instrument []
  (stest/instrument `valid-moves)
  (stest/instrument `move)
  (stest/instrument `move-val)
  (stest/instrument `moves?)
  (stest/instrument `last-move))

(defn test-defns []
  (instrument)
  (-> (stest/enumerate-namespace 'plus-minus.game.state) stest/check))

(comment

  (gen/generate (s/gen ::move-params))

  (def state
    {:board {:row-size 3 :cells [0 1 2
                                 3 4 5
                                 6 7 8]}
     :start 0
     :moves [2 8 7 4 3]
     :hrz-turn false
     :hrz-points 12
     :vrt-points 12})

  (s/explain-data ::state state)
  (make-move state 0)

  (simulate-game state :log true)

  (stest/abbrev-result (first (stest/check `valid-moves)))

  (stest/abbrev-result (first (stest/check `move)))

  (stest/abbrev-result (first (stest/check `move-val)))

  (stest/abbrev-result (first (stest/check `moves?)))

  (stest/abbrev-result (first (stest/check `last-move)))
  )
