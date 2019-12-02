(ns plus-minus.routes.oauth2
  (:require [plus-minus.config :refer [env]]
            [plus-minus.db.core :as db]
            [ring.util.response :refer [redirect]]
            [clj-http.client :as http]
            [clj-oauth2.client :as oauth2]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [mount.core :refer [defstate]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.string :as str]
            [buddy.hashers :as hashers]
            [plus-minus.validation :as validation]
            [plus-minus.routes.services.auth :as auth]))

;; For now just getting user email and generating user from it.
;; Need to rewrite all the auth to fit oauth2 standard.

(defstate ^:private oauth-params
  :start {:redirect-uri       (str (:domain env) "/oauth/callback")
          :client-id          (:oauth-consumer-key env)
          :client-secret      (:oauth-consumer-secret env)
          :scope              ["https://www.googleapis.com/auth/userinfo.email"]
          :authorization-uri  "https://accounts.google.com/o/oauth2/auth"
          :access-token-uri   "https://accounts.google.com/o/oauth2/token"
          :access-query-param :access_token
          :grant-type         "authorization_code"
          :access-type        "online"
          :approval_prompt    ""})

(defstate ^:private auth-request
  :start (oauth2/make-auth-request oauth-params))

(defn- email->name
  "extracts string before @, taking no more than 20 symbols (db id max)"
  [email]
  (->> email (re-find #"^[^@]*") (take 20) str/join))

(defn- user->with-pass [user]
  (assoc user :pass (gen/generate (s/gen ::validation/pass))))

(defn- create-user! [email login]
  (let [user (db/get-user {:id login})]
    (if user
      (create-user! email (gen/generate (s/gen ::validation/id)))
      (let [user (assoc user :email email, :id login)]
        (db/create-user! (-> (user->with-pass user)
                             (update :pass hashers/encrypt)))
        ;; TODO: send pass and login in email
        user))))

(defn- email->user! [email]
  (if-let [user (db/get-user {:email email})]
    user
    (create-user! email (email->name email))))

(defn save-token! [{session :session :as req}]
  (let [token      (oauth2/get-access-token oauth-params (:params req) auth-request)
        token-info (http/get "https://www.googleapis.com/oauth2/v1/tokeninfo"
                             {:query-params {:access_token (:access-token token)}
                              :as :json})
        user       (-> token-info :body :email email->user!)]
    (auth/enrich-session (redirect "/app") session user)))

(defn routes []
  ["/oauth"
   {:middleware [wrap-keyword-params
                 wrap-params]}
   ["/init" {:get (fn [_] (redirect (:uri auth-request)))}]
   ["/callback" {:get save-token!}]])
