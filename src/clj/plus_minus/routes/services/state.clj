(ns plus-minus.routes.services.state
  (:require [plus-minus.db.core :as db]
            [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [ring.util.http-response :as response]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]))

(defn- get-or-generate [id]
  (let [{:keys [start moves hrz_turn cells] :as db-state} (db/get-state {:id id})
        db-state (when db-state
                   (s/simulate-game
                    {:start start
                     :moves moves
                     :hrz-turn hrz_turn
                     :board {:row-size (-> cells count Math/sqrt int)
                             :cells cells}}))]
    (or db-state (s/state-template 4))))

(defn get-state [id]
  (try
    (response/ok {:result (get-or-generate id)})
    (catch Exception e
      (log/error e)
      (response/internal-server-error
       {:result :error
        :message "server error occured while getting state"}))))

(defn upsert-state [id {{c :cells} :board :as state}]
  (try
    (db/upsert-state! (assoc state :cells c, :id id))
    (response/ok {:result :ok})
    (catch Exception e
      (log/error e)
      (response/internal-server-error
       {:result :error
        :message "server error occured while saving state"}))))

#_(db/upsert-state!
 {:id "dumch" :cells [1 2 3 4] :start 0 :hrz-turn true :moves []})

