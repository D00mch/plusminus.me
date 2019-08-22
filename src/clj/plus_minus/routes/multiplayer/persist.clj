(ns plus-minus.routes.multiplayer.persist
  (:require [plus-minus.db.core :as db]
            [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :refer [->Reply ->Result] :as c]
            [clojure.core.async :refer [>! <! >!! chan alts! go-loop close!]]
            [clojure.tools.logging :as log]
            [plus-minus.common.response :as response]
            [clojure.spec.alpha :as s]
            [clojure.spec.alpha :as spec]
            [plus-minus.validation :as validation]
            [clojure.spec.gen.alpha :as gen]
            [plus-minus.game.game :as game]
            [plus-minus.multiplayer.contract :as contract]))

(def ^:private end-xform
  (filter #(= (:reply-type %) :end)))

(defn- upsert [{id :id {:keys [outcome cause]} :data} & [connection]]
  (println "about to upsert" id outcome cause)
  (let [stats (or (-> {:id id} db/get-online-stats :statistics) contract/stats-initial)
        stats (if (= outcome :draw)
                 (update stats :draw inc)
                 (update-in stats [outcome cause] inc))
        iq     (game/calc-iq (contract/stats-summed stats))
        data   {:id id, :iq iq, :statistics stats}]
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

(defn get-stats [id]
  (response/try-with-wrap-internal-error
  :fun #(-> {:id id} db/get-online-stats)
  :msg "server error occured while getting online stats"))

(comment

  (>!! stats> 0)
  (topics/push! :reply (->Reply :end "bob" (->Result :win :no-moves)))

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
