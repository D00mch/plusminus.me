(ns plus-minus.routes.services.state
  (:require [plus-minus.db.core :as db]
            [plus-minus.common.response :as response]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as g]
            [plus-minus.game.progress :as p]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [clojure.spec.alpha :as s]
            [plus-minus.routes.services.auth :as auth]))

;; current game state with bot

(defn- get-or-generate [id]
  (or (-> {:id id} db/get-state :state) (st/state-template 4)))

(defn get-state [id]
  (response/try-with-wrap-internal-error
   :fun (fn []
          (auth/update-last-login! id)
          {:state (get-or-generate id)})
   :msg  "server error occured while getting state"))

(defn upsert-state [id state]
  (response/try-with-wrap-internal-error
   :fun (fn []
          (db/upsert-state! {:id id, :state state})
          {:result :ok})
   :msg "server error occured while saving state"))

(defn move [id mv]
  (cond-let
    :let [old-state (-> {:id id} db/get-state :state)]
    (not old-state)
    (response/e-precondition "can't move without first providing the state")

    :let [valid (st/valid-move? old-state mv)]
    (not valid)
    (response/e-precondition "invalid move")

    :else
    (upsert-state id (st/move old-state mv))))

(defn- get-stats [id]
  (or (-> {:id id} db/get-statistics :statistics)
      p/empty-stats))

(defn get-stats-resp [id]
  (response/try-with-wrap-internal-error
   :fun #(let [stats (get-stats id)] {:statistics (p/check-stats stats)})
   :msg "server error occured while getting stats"))



(defn game-end-resp [id state usr-hrz usr-give-up]
  (if (and (st/moves? state) (not usr-give-up))
    (response/e-precondition "can't end the game with free moves and not giving-up")
    (let [result (if usr-give-up :lose (g/on-game-end state usr-hrz))
          stats  (update (get-stats id) result inc)
          stats  (p/on-end stats result)]
      (response/try-with-wrap-internal-error
       :fun #(do (db/upsert-statistics! {:id id :statistics stats})
                 {:statistics stats})
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
