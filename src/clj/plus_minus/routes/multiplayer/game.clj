(ns plus-minus.routes.multiplayer.game
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [>! <! go go-loop chan alts!]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.routes.multiplayer.reply :as reply]
            [plus-minus.routes.multiplayer.topics :as topics]))

;; communication is set up with multiplayer/topics.clj

(defonce ^:private id->msgs> (atom {}))
(defonce ^:private exit> (chan))

(defn close! []
  (async/>!! exit> 1)
  (doseq [[_ msg>] @id->msgs>] (async/close! msg>))
  (reset! id->msgs> {}))

(defn- add-new-games!
  "new-games> - channel with contract/Game, matched games;
  here we add chan with contract/Message into id->msgs> atom"
  [new-games> exit>]
  (go-loop []
    (let [[v ch] (alts! [exit> new-games>] :priority true)]
      (cond
        (= ch exit>) (log/info "exit signal received in add-new-games!")
        v (when-let [{id1 :player1, id2 :player2 :as game} v]
            (let [msg> (chan)
                  end> (reply/pipe-replies! game msg> (topics/in-chan :reply))]
              (swap! id->msgs> assoc id1 msg>, id2 msg>)
              (go (<! end>)
                  (async/close! msg>)
                  (swap! id->msgs> dissoc id1, id2)))
            (recur))))))

(defn- pipe-messages->replies>!
  "takes games-messages> chan which pass all game-related msgs of all the players"
  [games-messages> exit>]
  (go-loop []
    (let [[v ch] (alts! [exit> games-messages>])]
      (cond
        (= ch exit>) (log/info "exit signal received in pipe-messages>replies!")
        v            (let [{:keys [id] :as msg} v]
                       (if-let [msg> (get @id->msgs> id)]
                         (>! msg> msg)
                         (log/error "can't find msg> chan for id" id))
                       (recur))))))

(defn listen! []
  (let [all-msgs>  (topics/tap! :msg (chan 1 nil))
        new-games> (matcher/pipe-games! all-msgs> (chan))
        moves>     (topics/tap! :msg (chan 1 (filter #(not= (:msg-type %) :new))))
        ex1>        (chan)
        ex2>        (chan)]
    (go (<! exit>)
        (>! ex1> 1)
        (>! ex2> 1)
        (async/close! ex1>)
        (async/close! ex2>))
    (add-new-games! new-games> ex1>)
    (pipe-messages->replies>! moves> ex2>)))
