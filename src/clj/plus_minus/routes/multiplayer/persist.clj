(ns plus-minus.routes.multiplayer.persist
  (:require [plus-minus.db.core :as db]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :refer [->Reply ->Result] :as c]
            [clojure.core.async :refer [>! <! >!! chan alts! go-loop close!]]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as s]
            [clojure.spec.alpha :as spec]
            [plus-minus.validation :as validation]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.game.game :as game]
            [plus-minus.multiplayer.contract :as contract]))

(def ^:private end-xform
  (filter #(= (:reply-type %) :end)))

(defn get-stats [id]
  (or (db/get-online-stats {:id id})
      {:id id, :iq 100, :statistics contract/stats-initial}))

(defn- upsert [{id :id {:keys [outcome cause game]} :data} & [connection]]
  (let [stats (:statistics (c/stats game id))
        stats (if (= outcome :draw)
                 (update stats :draw inc)
                 (update-in stats [outcome cause] inc))
        iq    (game/calc-iq (contract/stats-summed stats))
        data  {:id id, :iq iq, :statistics stats}]
    (if connection
      (db/upsert-online-stats! connection data)
      (db/upsert-online-stats! data))))

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

(comment

  ;; fill db with mock statistics
  (let [unique-users (-> (spec/gen ::validation/id) (gen/sample 100) distinct)]
    (doseq [user unique-users
            :let [stats (-> (spec/gen ::contract/statistics) gen/generate)]]
      (db/upsert-online-stats!
       #_println
       {:id user
        :iq (game/calc-iq (contract/stats-summed stats))
        :statistics stats})))
  )
