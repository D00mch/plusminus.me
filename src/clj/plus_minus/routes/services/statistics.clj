(ns plus-minus.routes.services.statistics
  (:require [plus-minus.db.core :as db]
            [plus-minus.common.response :as resp]
            [plus-minus.routes.multiplayer.persist :as persist]))

;; online statistics ns
;; here will be logic with caches and pagination

(def ^:const stats-count 25)

(defn get-stats [id]
  (resp/try-with-wrap-internal-error
   :fun #(persist/get-stats id)
   :msg "server error occured while getting online stats"))

(defn- insert-user-stats [stats user]
  (let [compare' #(compare (:iq %2) (:iq %1))
        i (java.util.Collections/binarySearch stats user compare')
        i (if (< i 0) (- (+ i 1)) i)]
    (distinct (apply conj (drop i stats) user (take i stats)))))

(defn- get-top-stats-with-user [id]
  (let [stats  (db/get-online-stats-limit {:limit stats-count})]
    (if id
      (insert-user-stats stats (persist/get-stats id))
      stats)))

(defn get-all-online-stats [id]
  (resp/try-with-wrap-internal-error
   :fun #(array-map :data (get-top-stats-with-user id))
   :msg "server error occured while getting all onlne statistics"))
