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

#_(str "Basic " (.encodeToString (java.util.Base64/getEncoder) (.getBytes "user:pass")))

(defn- decode-auth [encoded]
  (let [auth (or (some-> encoded (.split " ") second)
                 (log/error "Cant decode header header: " encoded)
                 (throw (ex-info "Bad header" {:header encoded})))]
    (-> (.decode (java.util.Base64/getDecoder) auth)
        (String. (java.nio.charset.Charset/forName "UTF-8"))
        (.split ":"))))

(defn login! [{:keys [session]} auth]
  (try
    (let [[id pass]   (decode-auth auth)
         user        (or (db/get-user {:id id})
                         (throw (ex-info "Unknown login" {:field :id})))]
     (when-not (hashers/check pass (:pass user))
       (throw (ex-info "Wrong password" {:field :pass})))
     (-> {:result :ok}
         (assoc :session (assoc session :identity id))))
    (catch clojure.lang.ExceptionInfo e
      (response/unauthorized {:result  :unauthorized
                              :field   (:field (ex-data e))
                              :message (ex-message e)}))))

(defn logout! []
  (-> {:result :ok}
      (response/ok)
      (assoc :session nil)))

(defn delete-account! [identity]
  (db/delete-user! identity)
  (-> {:result :ok}
      (response/ok)
      (assoc :session nil)))
