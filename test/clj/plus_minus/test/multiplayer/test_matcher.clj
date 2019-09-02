(ns plus-minus.test.multiplayer.test-matcher
  (:require [clojure.core.async :as async :refer [<!! <! >! >!! go go-loop chan close!]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.common.async :refer [get-with-timeout!!]]
            [plus-minus.validation :as validation]
            [plus-minus.multiplayer.contract :refer [->Message]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.game.board :as b]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [mount.core :as mount]
            [plus-minus.db.core]
            [plus-minus.config :refer [env]]
            [luminus-migrations.core :as migrations]
            [clojure.spec.alpha :as s])
  (:import [plus_minus.multiplayer.contract Game]))

(def ^:private id-gen (spec/gen ::validation/id))

(use-fixtures
  :once (fn [f]
          (mount/start
           #'plus-minus.config/env
           #'plus-minus.db.core/*db*)
          (migrations/migrate ["migrate"] (select-keys env [:database-url]))
          (f)))

(defn- in-out-matcher-channels [active-games]
  (let [in>     (chan)
        out>    (chan)
        drop>   (chan 10)
        _       (matcher/pipe-games! in> out> drop> active-games true)]
    [in> out> drop>]))

(deftest matching-game
  (let [[u1 u2]    ["u1" "u2"]
        games      (atom {})
        [in> out>] (in-out-matcher-channels games)
        size       (+ 1 b/row-count-min)]
    (testing "right users matched and game with right size created"
      (>!! in> (->Message :new u1 b/row-count-max))
      (>!! in> (->Message :new u1 size))
      (>!! in> (->Message :new "regedar" b/row-count-min))
      (>!! in> (->Message :new u2 size))
      (let [{{{r :row-size} :board} :state p1 :player1 p2 :player2 :as game}
            (get-with-timeout!! out>)]
        (swap! games assoc p1 game, p2 game)
        (is (= u1 p1))
        (is (= u2 p2))
        (is (= size r))))
    (testing "new request while playing should be ignored"
      (>!! in> (->Message :new u2 size))
      (>!! in> (->Message :new "regedar" size))
      (>!! in> (->Message :new u2 size))
      (let [game (get-with-timeout!! out>)] (is (nil? game))))
    (testing "old request cleared on new game"
      (swap! games dissoc u1 u2)
      (>!! in> (->Message :new "regedar" b/row-count-max)) ;; try match first u1:new
      (let [game (get-with-timeout!! out>)] (is (nil? game))))
    (close! in>)))

(deftest matching-quick
  (let [[u1 u2 u3] ["u1" "u2" "u3"]
        [in> out>] (in-out-matcher-channels (atom {}))
        size       b/row-count-min
        match-f    (fn [s1 s2 name expected-size-f]
                     (testing name
                       (>!! in> (->Message :new u1 s1))
                       (>!! in> (->Message :new u2 s2))
                       (let [{{{r :row-size} :board} :state p1 :player1 p2 :player2}
                             (get-with-timeout!! out>)]
                         (is (= u1 p1))
                         (is (= u2 p2))
                         (is (expected-size-f r)))))]
    (match-f :quick :quick "matching two quick games" #(s/valid? ::b/row-size %))
    (match-f :quick size "matching quick with size" (partial = size))
    (match-f size :quick "matching size with quick" (partial = size))
    (testing "quick can be dropped"
      (>!! in> (->Message :new u3 :quick))
      (>!! in> (->Message :drop u3 :quick))
      (>!! in> (->Message :new u1 :quick))
      (>!! in> (->Message :new u2 :quick))
      (let [{p1 :player1 p2 :player2} (get-with-timeout!! out>)]
        (is (= u1 p1))
        (is (= u2 p2))))))

(deftest matching-games
  (let [users          (-> (spec/gen ::validation/id) (gen/sample 2000) distinct)
        [in> out>]     (in-out-matcher-channels (atom {}))
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
        [in> out> drop>] (in-out-matcher-channels (atom {}))
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
