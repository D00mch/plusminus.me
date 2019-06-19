(ns plus-minus.routes.services.state
  (:require [plus-minus.db.core :as db]
            [plus-minus.utils :as utils]
            [plus-minus.game.board :as b]
            [plus-minus.routes.services.common :as common]
            [plus-minus.game.state :as s]
            [plus-minus.game.game :as g]
            [clojure.tools.logging :as log]
            [clojure.spec.alpha :as spec]
            [clojure.pprint :refer [pprint]]))

(defn- get-or-generate [id]
  (or (-> {:id id} db/get-state :state) (s/state-template 4)))

(defn get-state [id]
  (common/try-with-wrap-internal-error
   :fun (fn [] {:state (get-or-generate id)})
   :msg  "server error occured while getting state"))

(defn upsert-state [id state]
  (common/try-with-wrap-internal-error
   :fun (fn []
          (db/upsert-state! {:id id, :state state})
          {:result :ok})
   :msg "server error occured while saving state"))

(spec/def ::win (spec/or :n zero? :n pos-int?))
(spec/def ::lose (spec/or :n zero? :n pos-int?))
(spec/def ::draw (spec/or :n zero? :n pos-int?))
(spec/def ::statistics (spec/keys :req-un [::win ::lose ::draw]))

(defn get-stats [id]
  (common/try-with-wrap-internal-error
   :fun #(-> {:id id} db/get-statistics (dissoc :player_id))
   :msg "server error occured while getting stats"))

(defn game-end [id state usr-hrz usr-give-up]
  (if (and (s/moves? state) (not usr-give-up))
    (common/e-precondition "can't end the game with free moves and not giving-up")
    (let [stats     (or (-> {:id id} db/get-statistics :statistics)
                        {:win 0 :lose 0 :draw 0})
          result    (if usr-give-up :lose (g/on-game-end state usr-hrz))
          new-stats (update stats result inc)]
      (common/try-with-wrap-internal-error
       :fun #(do (db/upsert-statistics! {:id id :statistics new-stats})
                 {:statistics new-stats})
       :msg  "server error occured while saving stats"))))

#_(defn make-move [mv]
  (let [state ])
  )

#_(db/upsert-statistics! {:id "dumch" :statistics {:win 0 :lose 0 :draw 0}})

#_(db/upsert-state! {:id "dumch",
                   :state {:board {:cells [3,6,3,-8,-1,7,3,-1,-4,-8,4,1,-2,8,3,9] 
                                   :row-size 2}
                           :start 6
                           :moves []
                           :hrz-points 0
                           :vrt-points 0
                           :hrz-turn true}})
