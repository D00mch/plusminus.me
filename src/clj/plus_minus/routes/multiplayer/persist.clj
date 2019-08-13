(ns plus-minus.routes.multiplayer.persist
  (:require [plus-minus.db.core :as db]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :refer [->Reply ->Result] :as c]
            [mount.core :as mount]
            [clojure.core.async :refer [>! <! >!! chan alts! go-loop close!]]
            [clojure.tools.logging :as log]))

(def ^:private initial-stats {:win  {:give-up  0,
                                     :time-out 0,
                                     :no-moves 0},
                              :lose {:give-up  0,
                                     :time-out 0,
                                     :no-moves 0},
                              :draw 0})

(def ^:private end-xform
  (filter #(= (:reply-type %) :end)))

(defn- upsert [{id :id {:keys [outcome cause]} :data} & [connection]]
  (println "about to upsert" id outcome cause)
  (let [stats  (or (-> {:id id} db/get-online-stats :statistics) initial-stats)
        stats+ (if (= outcome :draw)
                 (update stats :draw inc)
                 (update-in stats [outcome cause] inc))]
    (if connection
      (db/upsert-online-stats! connection {:id id, :statistics stats+})
      (db/upsert-online-stats! {:id id, :statistics stats+}))))

(defn subscribe>
  "Loops over topic/reply and presists game-results.
  Optionally pass custom db-connections.
  Returns exit channel"
  [& [connection]]
  (let [ends> (topics/tap! :reply (chan 1 end-xform))]
    (go-loop []
      (when-let [end-reply (<! ends>)]
        (upsert end-reply connection)
        (recur)))
    ends>))

(mount/defstate persist>
  :start (subscribe>)
  :stop  (close! persist>))

;; (>!! stats> 0)
;; (topics/push! :reply (->Reply :end "bob" (->Result :win :no-moves)))
