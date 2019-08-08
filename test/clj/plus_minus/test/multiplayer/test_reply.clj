(ns plus-minus.test.multiplayer.test-reply
  (:require [clojure.core.async :refer
             [<!! <! >! >!! go go-loop chan close! alts!! timeout]]
            [plus-minus.common.async :refer [get-with-timeout!!]]
            [clojure.spec.alpha :as spec]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.validation :as validation]
            [plus-minus.multiplayer.contract :refer [->Message]]
            [plus-minus.routes.multiplayer.reply :as reply]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [clojure.test :refer [deftest is testing]]
            [plus-minus.game.state :as st]
            [plus-minus.routes.multiplayer.timer :as timer]
            [plus-minus.game.game :as game])
  (:import [plus_minus.multiplayer.contract Game Reply]))

(deftest basic-replies
  (let [game (matcher/initial-state 3 "p1" "p2")
        in>  (chan)
        out> (chan)
        mvs> (chan)
        st>  (chan 1 (drop 2)) ;; drop first 2 states from init
        tt>  (chan)
        _    (go-loop []
               (when-let [{type :reply-type :as reply} (<! out>)]
                 (case type
                   :move      (>! mvs> reply)
                   :state     (>! st> reply)
                   :turn-time (>! tt> reply)
                   nil)
                 (recur)))
        end> (reply/pipe-replies! game in> out>)]

    (testing "can get two move replies"
      (>!! in> (->Message :move
                          (reply/player-turn-id game)
                          (game/some-move (:state game))))
      (is (instance? Reply (do (get-with-timeout!! mvs>)
                               (get-with-timeout!! mvs>)))))

    (testing "can get :state reply after :state message"
      (>!! in> (->Message :state "p1" nil))
      (let [{type :reply-type} (get-with-timeout!! st>)]
        (is (= type :state))))

    (testing "can get :turn-time reply after :turn-time message"
      (>!! in> (->Message :turn-time "p1" nil))
      (let [{type :reply-type time :data} (get-with-timeout!! tt>)]
        (is (= type :turn-time))
        (is (> time (- timer/turn-ms 2000)))))

    (testing "game ends after :give-up message"
      (>!! in> (->Message :give-up "p1" nil))
      (prn "about to start waiting end>")
      (is (= :end (get-with-timeout!! end>))))

    (close! in>)))

#_(deftest drop-when-game-started
  (testing "just give-up the game if user drops it after start"
    (let [game (matcher/initial-state 3 "p1" "p2")
          in>  (chan 10)
          out> (chan 10)
          end> (reply/pipe-replies! game in> out>)]
      (>!! in> (->Message :drop "p1" nil))
      )))

(comment

  (do
    (def game
      {:state {:board {:row-size 3,:cells [4 2 -5 9 -8 -7 -5 -5 8]},
               :start 6,:moves [],:hrz-points 0,:vrt-points 0,:hrz-turn true},
       :game-id 0,
       :created (System/currentTimeMillis),
       :updated (System/currentTimeMillis),
       :player1 "bob",:player2 "regeda",
       :player1-hrz true})
    (def in> (chan))
    (def out> (chan))
    (go-loop [v (<! out>)]
      (println v)
      (when v (recur (<! out>))))
    (def end> (reply/pipe-replies! game in> out>))
    )

  (do (prn (reply/player-turn-id game))
      (prn (st/valid-moves (:state game)))
      (st/state-print (:state game)))

  (let [mv 8
        nm "bob"
        ;; nm "regeda"
        ] (>!! in> (->Message :move nm mv))
       (def game (update game :state st/move mv)))

  (>!! in> (->Message :turn-time "regeda" nil))
  (>!! in> (->Message :give-up "regeda" nil))

  (clojure.core.async.impl.protocols/closed? in>)
  (<!! end>)
  (do
    (async/close! end>)
    (async/close! in>)
    (async/close! out>))
  )
