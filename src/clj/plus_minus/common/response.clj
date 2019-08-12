(ns plus-minus.common.response
  (:require [ring.util.http-response :as response]
            [clojure.tools.logging :as log]))

(defn e-precondition [msg & [data]]
  (log/error msg)
  (response/precondition-failed (merge {:result :error, :message msg} data)))

(defn e-internal [e msg]
  (log/error e msg)
  (response/internal-server-error {:result :error, :message msg}))

(defn try-with-wrap-internal-error [& {:keys [fun msg]}]
  (try (response/ok (fun))
       (catch Exception e
         (e-internal e msg))))
