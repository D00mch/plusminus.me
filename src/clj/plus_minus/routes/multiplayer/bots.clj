(ns plus-minus.routes.multiplayer.bots
  (:require [plus-minus.routes.multiplayer.connection :as multiplayer]
            [plus-minus.multiplayer.contract :as contract :refer
             [->Message map->Message]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.core.async :as async :refer [go-loop <! >!!]]
            [plus-minus.game.board :as b]
            [clojure.tools.logging :as log]
            [plus-minus.game.game :as game]
            [plus-minus.game.state :as st]
            [plus-minus.config :refer [env]]
            [mount.core :as mount]
            [plus-minus.multiplayer.contract :as c]))

(def ^:const bot-name "AggressiveBot") ;; name reserved in database
(def ^:private mocks (seq c/mock-info))

(defn- message! [type data]
  (multiplayer/message (->Message type bot-name data)))

(defn- new! []
  (message! :new (gen/generate (s/gen ::b/row-size))))

(defn- mock! [game]
  (let [{{influence :influence} :statistics} (c/stats game bot-name)
        [type {:keys [price name]}]          (->> mocks count rand int (nth mocks))]
    (when (>= influence price)
      (message! :mock type))))

(defn- consider-mock! [game]
  (when (> 2 (rand 10)) (mock! game)))

(mount/defstate agressive-bot
  :start
  (let [ch         (async/chan (* 2 (quot contract/turn-ms contract/ping-ms)))
        game-state (atom nil)]
    (new!)
    (go-loop []
      (when-let [{type :reply-type data :data :as reply} (<! ch)]
        (<! (async/timeout (+ 700 (rand 500))))
        (log/debug (str type) reply)
        (case type
          :state (reset! game-state data)
          :move  (swap! game-state update :state st/move data)
          :end   (new!)
          nil)
        (when (not= type :end)
          (if (= (contract/turn-id @game-state) bot-name)
            (let [move (-> @game-state :state (game/move-clever-bot) :moves last)]
              (log/debug "about to move with" move)
              (message! :move move))
            (consider-mock! @game-state)))
        (recur)))
    ch)
  :stop (async/close! agressive-bot))

(defmethod multiplayer/on-reply bot-name [reply] (>!! agressive-bot reply))
