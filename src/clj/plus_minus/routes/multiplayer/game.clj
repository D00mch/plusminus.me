(ns plus-minus.routes.multiplayer.game
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async :refer [>! <! go go-loop chan]]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [plus-minus.routes.multiplayer.reply :as reply]
            [plus-minus.routes.multiplayer.topics :as topics]))

;; communication is set up with multiplayer/topics.clj

(def ^:private id->msgs> (atom {}))
(def ^:private turn-ms 10000)

(defn- add-new-games!
  "new-games> - channel with contract/Game, matched games;
  here we add chan with contract/Message into id->msgs> atom"
  [new-games>]
  (go-loop []
      (when-let [{id1 :player1, id2 :player2 :as game} (<! new-games>)]
      (let [game-msg> (chan)
            game-end> (reply/pipe-replies! game game-msg> (topics/in-chan :reply))]
        (go (<! game-end>)
            (async/close! game-msg>)
            (swap! id->msgs> dissoc id1, id2))
        (swap! id->msgs> assoc id1 game-msg>, id2 game-msg>))
      (recur))))

(defn- pipe-messages>replies>!
  "takes games-messages> chan which pass all game-related msgs of all the players"
  [games-messages>]
  (go-loop []
    (when-let [{:keys [id] :as msg} (<! games-messages>)]
      (if-let [msg> (get @id->msgs> id)]
        (>! msg> msg)
        (log/error "can't find msg> chan for id" id))
      (recur))))

(defn listen! []
  (let [all-msgs>  (topics/tap! :msg (chan 1 nil))
        new-games> (matcher/pipe-games! all-msgs> (chan))
        moves>     (topics/tap! :msg (chan 1 (filter #(not= (:msg-type %) :new))))]
    (add-new-games! new-games>)
    (pipe-messages>replies>! moves>)))
