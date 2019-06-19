(ns plus-minus.routes.services.common
  (:require [ring.util.http-response :as response]
            [clojure.tools.logging :as log]))

(defn e-precondition [msg]
  (log/error msg)
  (response/precondition-failed {:result :error, :message msg}))

(defn e-internal [e msg]
  (response/internal-server-error {:result :error, :message msg}))

(defn try-with-wrap-internal-error [& {:keys [fun msg]}]
  (try (response/ok (fun))
       (catch Exception e
         (e-internal e msg))))
