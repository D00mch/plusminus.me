(ns plus-minus.test.db.core
  (:require
    [plus-minus.db.core :refer [*db*] :as db]
    [luminus-migrations.core :as migrations]
    [clojure.test :refer [use-fixtures deftest is testing]]
    [clojure.java.jdbc :as jdbc]
    [plus-minus.config :refer [env]]
    [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
      #'plus-minus.config/env
      #'plus-minus.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-users
  (jdbc/with-db-transaction [t-conn *db*]
    (jdbc/db-set-rollback-only! t-conn)
    (is (= 1 (db/create-user!
               t-conn
               {:id         "1"
                :pass       "pass"})))
    (is (= {:id        "1",
            :email      nil,
            :admin      nil,
            :last_login nil,
            :is_active  nil,
            :pass      "pass"}
           (db/get-user t-conn {:id "1"})))))
