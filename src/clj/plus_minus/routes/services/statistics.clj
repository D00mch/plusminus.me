(ns plus-minus.routes.services.statistics
  (:require [plus-minus.db.core :as db]
            [plus-minus.common.response :as resp]))

;; here will be logic with caches and pagination

(defn get-all-online-stats []
  (resp/try-with-wrap-internal-error
   :fun #(array-map :data (db/get-all-online-stats))
   :msg "server error occured while getting all onlne statistics"))
