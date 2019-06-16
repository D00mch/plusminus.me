(ns plus-minus.game.board
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]))

(comment
  board is a square matrix, represanted as [];
  below - board example with row-size 3

  7 3 8 | <-- this is row
  2 6 3
  4 9 1 | {:x 0, :y 2} -> 4

  represented in code as {:row-size n, :board [...]})

(def row-count-min 3)
(def row-count-max 9)

(def row-count-max-excl   (inc row-count-max))
(def board-count-min      (int (Math/pow row-count-min 2)))
(def board-count-max-excl (int (Math/pow row-count-max-excl 2)))

(defn bcount [{c :cells}] (count c))

(defn xy-in-board? [x y {:keys [row-size]}]
  (and (< -1 x row-size) (< -1 y row-size)))

(defn ind-in-board? [i {:keys [cells]}]
  (< -1 i (count cells)))

(defn generate [size]
  {:row-size size,
   :cells    (repeatedly (* size size) #(-> 19 rand int (- 9)))})

(defn xy->index  [x y row-size] (+ (* y row-size) x))
(defn bxy->index [board x y]    (xy->index x y (:row-size board)))

(defn bget
  "get value from the board"
  [board x y]
  (nth (:cells board) (bxy->index board x y)))

(defn coords
  "get x and y from the index (nth in board)"
  [row-size i]
  {:x (mod i row-size) :y (quot i row-size)})

(defn bcoords [{row :row-size} i] (coords row i))

;;************************* SPECS *************************

(defn board-generator [& {r :row-size, :or {r (gen/generate (s/gen ::row-size))}}]
  (gen/fmap
   (fn [v] {:row-size r, :cells v})
   (gen/vector (s/gen ::cell) (* r r))))

(s/def ::row-size (s/int-in row-count-min row-count-max-excl))
(s/def ::cell (s/int-in -9 10))
(s/def ::square #(-> % count Math/sqrt (mod 1) (== 0)))
(s/def ::cells (s/& (s/+ ::cell) ::square))
(s/def ::board
  (s/with-gen
    (s/and (s/keys :req-un [::row-size ::cells])
           #(== (-> % :row-size (Math/pow 2)) (-> % :cells count)))
    board-generator))
(s/def ::index (s/int-in 0 board-count-max-excl))
(s/def ::coord (s/int-in 0 row-count-max))
(s/def ::board-with-coords
  (s/with-gen
    (s/and (s/cat :board (s/spec ::board), :x ::coord, :y ::coord)
           #(xy-in-board? (:x %) (:y %) (:board %)))
    #(gen/fmap
      (fn [{r :row-size :as board}] [board (rand-int r) (rand-int r)])
      (board-generator))))

(s/fdef generate
  :args (s/cat :size ::row-size)
  :fn   #(== (-> % :args :size) (-> % :ret :row-size))
  :ret  ::board)

(s/fdef bxy->index
  :args ::board-with-coords
  :fn   #(ind-in-board? (-> % :ret) (-> % :args :board))
  :ret  ::index)

(s/fdef bget
  :args ::board-with-coords
  :ret  ::cell)

(s/fdef bcoords
  :args (s/and (s/cat :board (s/spec ::board), :i ::index)
               #(ind-in-board? (:i %) (:board %)))
  :fn   #(xy-in-board? (-> % :ret :x) (-> % :ret :y) (-> % :args :board)))


(defn instrument []
  (stest/instrument `generate)
  (stest/instrument `bxy->index)
  (stest/instrument `bget)
  (stest/instrument `bcoords))

(defn test-defns []
  (instrument)
  (-> (stest/enumerate-namespace 'plus-minus.game.board) stest/check))

(comment
  (stest/abbrev-result (first (stest/check `generate)))

  (stest/abbrev-result (first (stest/check `bxy->index)))

  (stest/abbrev-result (first (stest/check `bget)))

  (stest/abbrev-result (first (stest/check `bcoords)))
)
