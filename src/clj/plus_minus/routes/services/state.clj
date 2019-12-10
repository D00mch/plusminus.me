(ns plus-minus.routes.services.state
  (:require [plus-minus.db.core :as db]
            [plus-minus.common.response :as response]
            [plus-minus.game.state :as st]
            [plus-minus.game.game :as g]
            [plus-minus.game.progress :as p]
            [com.walmartlabs.cond-let :refer [cond-let]]
            [clojure.spec.alpha :as s]
            [plus-minus.routes.services.auth :as auth]
            [plus-minus.validation :as valid]))

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

(s/def ::usr-hrz boolean?)
(s/def ::give-up boolean?)
(s/def ::result-state (s/keys :req-un [::st/state ::usr-hrz ::give-up ::valid/id]))
(s/def ::result-states (s/coll-of ::result-state))

(defn- upsert-stats [id stats]
  (db/upsert-statistics! {:id id :statistics stats}) {:statistics stats})

(defn- result-f [usr-give-up state usr-hrz]
  (if usr-give-up :lose (g/on-game-end state usr-hrz)))

(defn game-end-resp [id state usr-hrz usr-give-up]
  (if (and (st/moves? state) (not usr-give-up))
    (response/e-precondition "can't end the game with free moves and not giving-up")
    (let [result (result-f usr-give-up state usr-hrz)
          stats  (get-stats id)
          stats  (p/on-end stats result)]
      (response/try-with-wrap-internal-error
       :fun #(upsert-stats id stats)
       :msg  "server error occured while saving stats"))))

(defn- states->stats
  "gets vector of states and returns reduced stats"
  [states stats]
  (let [results (map #(result-f (:give-up %) (:state %) (:usr-hrz %)) states)
        stats   stats
        stats   (reduce #(p/on-end %1 %2) stats results)]
    stats))

(defn games-end-resp [states]
  (let [states (filter #(not (st/moves? (:state %))) states)]
    (if (empty? states)
      (response/e-precondition
       "you either passed empty states or states was unfinished")
      (let [id (-> states first :id)]
        (response/try-with-wrap-internal-error
         :fun (fn[] (upsert-stats id (states->stats states (get-stats id))))
         :msg "server error occured while processing accumulated states")))))
