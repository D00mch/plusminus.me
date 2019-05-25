(ns plus-minus.routes.services.auth
  (:require [plus-minus.db.core :as db]
            [plus-minus.validation :as validate]
            [plus-minus.utils :as utils]
            [ring.util.http-response :as response]
            [buddy.hashers :as hashers]
            [clojure.tools.logging :as log]))

(defn- handle-reg-exc [e]
  (let [duplicate? (->> (utils/ex-chain e)
                        (map ex-message)
                        (filter #(.startsWith % "ERROR: duplicate key"))
                        seq)]
    (if duplicate?
      (response/precondition-failed
       {:result  :error
        :message "user with the selected ID already exists"})
      (do
        (log/error e)
        (response/internal-server-error
         {:result  :error
          :message "server error occured while adding the user"})))))

(defn register! [{session :session} user]
  (if-let [errors (validate/registration-errors user)]
    (response/precondition-failed {:result  :error
                                   :message "precondition failed"
                                   :validation errors})
    (try
      (db/create-user! (-> user
                           (dissoc :pass-confirm)
                           (update :pass hashers/encrypt)))
      (-> {:result :ok}
          (response/ok)
          (assoc :session (assoc session :identity (:id user))))
      (catch Exception e (handle-reg-exc e)))))
