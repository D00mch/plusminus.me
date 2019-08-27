(ns plus-minus.test.multiplayer.test-matcher
  (:require [clojure.core.async :as async :refer [<!! <! >! >!! go go-loop chan close!]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.common.async :refer [get-with-timeout!!]]
            [plus-minus.validation :as validation]
            [plus-minus.multiplayer.contract :refer [->Message]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.game.board :as b]
            [clojure.test :refer [deftest is testing]])
  (:import [plus_minus.multiplayer.contract Game]))

(def ^:private id-gen (spec/gen ::validation/id))

(defn- in-out-matcher-channels []
  (let [in>     (chan)
        out>    (chan)
        drop>   (chan 10)
        _       (matcher/pipe-games! in> out> drop> true)]
    [in> out> drop>]))

(deftest matching-game
  (let [[u1 u2]    (-> id-gen (gen/sample 3) distinct)
        [in> out>] (in-out-matcher-channels)
        size       (+ 1 b/row-count-min)]
    (testing "right users matched and game with right size created"
      (>!! in> (->Message :new u1 size))
      (>!! in> (->Message :new "regedar" b/row-count-min))
      (>!! in> (->Message :new u2 size))
      (let [{{{r :row-size} :board} :state p1 :player1 p2 :player2 :as game}
            (get-with-timeout!! out>)]
        (is (= p1 u1))
        (is (= p2 u2))
        (is (= size r))))
    (close! in>)))

(deftest matching-games
  (let [users          (-> (spec/gen ::validation/id) (gen/sample 2000) distinct)
        [in> out>]     (in-out-matcher-channels)
        games-expected (quot (count users) 2)
        finish>        (chan 1 (drop (- games-expected 1)))]
    (testing (str "as match as " games-expected " games created")
      (go-loop []
        (when-let [v (<! out>)]
          (>! finish> v)
          (recur)))
      (doseq [user users]
        (>!! in> (->Message :new user b/row-count-min)))
      (is (instance? Game (get-with-timeout!! finish>))))
    (close! in>)))

(deftest dropping-game
  (let [[u1 u2 leaver]   ["u1" "u2" "leaver"]
        [in> out> drop>] (in-out-matcher-channels)
        size             b/row-count-min]
    #_(go-loop []
      (when-let [r (<! drop>)]
        (println r)
        (recur)))
    (testing "right users matched when leaver dropped"
      (>!! in> (->Message :new leaver size))
      (>!! in> (->Message :drop leaver size))
      (>!! in> (->Message :new u1 size))
      (>!! in> (->Message :new u2 size))
      (let [{id :id type :reply-type :as drop-reply} (<!! drop>)]
        (is (= id leaver))
        (is (= type :drop)))
      (let [{p1 :player1 p2 :player2} (get-with-timeout!! out>)]
        (is (and (= p1 u1) (= p2 u2)))))

    (testing "right users matched when leaver dropped without first requesting"
      (>!! in> (->Message :drop leaver size))
      (>!! in> (->Message :new u1 size))
      (>!! in> (->Message :new u2 size))
      (let [{id :id type :reply-type :as drop-reply} (<!! drop>)]
        (is (= id leaver))
        (is (= type :cant-drop)))
      (let [{p1 :player1 p2 :player2 :as game} (get-with-timeout!! out>)]
        (is (and (= p1 u1) (= p2 u2)))))

    (testing "matched when dropped previous game"
      (let [size2 (if (= size b/row-count-min) 5 b/row-count-min)]
        (>!! in> (->Message :new u1 size2))
        (>!! in> (->Message :drop u1 size2))
        (>!! in> (->Message :new u1 size))
        (>!! in> (->Message :new u2 size))
        (let [{id :id type :reply-type :as drop-reply} (<!! drop>)]
          (is (= id u1))
          (is (= type :drop)))
        (let [{p1 :player1 p2 :player2 :as game} (get-with-timeout!! out>)]
          (is (and (= p1 u1) (= p2 u2))))))

    (close! in>)))
