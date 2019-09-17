(ns plus-minus.routes.services.auth
  (:require [plus-minus.db.core :as db]
            [plus-minus.validation :as validate]
            [plus-minus.utils :as utils]
            [plus-minus.common.response :as response]
            [ring.util.http-response :as ring-response]
            [buddy.hashers :as hashers]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]))

(defn- handle-reg-exc [e]
  (let [duplicate? (->> (utils/ex-chain e)
                        (map ex-message)
                        (filter #(.startsWith % "ERROR: duplicate key"))
                        seq)]
    (if duplicate?
      (response/e-precondition "user with the selected ID already exists")
      (response/e-internal e "server error occured while adding the user"))))

(defn enrich-session [response session {id :id admin :admin}]
  (assoc response :session
         (assoc session :identity id, :roles (when admin #{:admin}))))

(defn register! [{session :session :as req} user]
  (pprint req)
  (if-let [errors (validate/registration-errors user)]
    (response/e-precondition "precondition failed" {:validation errors})
    (try
      (db/create-user! (-> user
                           (assoc  :email nil)
                           (dissoc :pass-confirm)
                           (update :pass hashers/encrypt)))
      (-> {:result :ok}
          (ring-response/ok)
          (enrich-session session user))
      (catch Exception e (handle-reg-exc e)))))

#_(str "Basic " (.encodeToString (java.util.Base64/getEncoder) 
                               (.getBytes "dumch:1Q2w3epl")))

(defn decode-auth [encoded]
  (let [auth (second (.split encoded " "))]
    (-> (.decode (java.util.Base64/getDecoder) auth)
        (String. (java.nio.charset.Charset/forName "UTF-8"))
        (.split ":"))))

(defn update-last-login!
  "non blocking; logs on error"
  [id]
  (future
    (try
      (db/update-user! {:id id :last_login (java.time.LocalDateTime/now)})
      (catch Exception e
        (log/error "can't update last_login for id" id e)))))

(defn- authenticate! [[id pass]]
  (when-let [user (db/get-user {:id id})]
    (when (hashers/check pass (:pass user))
      (update-last-login! id)
      user)))

(defn login! [{:keys [session] :as req} auth]
  (pprint req)
  (if-let [user (authenticate! (decode-auth auth))]
    (-> {:result :ok}
        (ring-response/ok)
        (enrich-session session user))
    (ring-response/unauthorized {:result :unauthorized
                                 :message "login failure"})))

(defn logout! [req]
  (pprint req)
  (-> {:result :ok}
      (ring-response/ok)
      (assoc :session nil)))

(defn change-pass! [id new-pass]
  (if-let [errors (validate/change-pass-errors new-pass)]
    (response/e-precondition "precondition failed" {:validation errors})
    (if-let [_ (db/get-user {:id id})]
      (response/try-with-wrap-internal-error
       :fun (fn []
              (db/update-user! {:id id :pass (hashers/encrypt new-pass)})
              {:result :ok})
       :msg "server error occured while changing password")
      (ring-response/unauthorized {:result :unauthorized
                                   :message "user does not exist"}))))

(defn delete-account! [identity]
  (db/delete-user! {:id identity})
  (-> {:result :ok}
      (ring-response/ok)
      (assoc :session nil)))
