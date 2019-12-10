(ns plus-minus.routes.services.statistics
  (:require [plus-minus.db.core :as db]
            [plus-minus.common.response :as resp]
            [plus-minus.routes.multiplayer.persist :as persist]))

;; online statistics ns
;; here will be logic with caches and pagination

(defn get-stats [id]
  (resp/try-with-wrap-internal-error
   :fun #(persist/get-stats id)
   :msg "server error occured while getting online stats"))

(defn get-all-online-stats []
  (resp/try-with-wrap-internal-error
   :fun #(array-map :data (db/get-all-online-stats))
   :msg "server error occured while getting all onlne statistics"))
