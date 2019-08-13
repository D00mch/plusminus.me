(ns plus-minus.test.multiplayer.persist
  (:require
   [plus-minus.db.core :as db]
   [luminus-migrations.core :as migrations]
   [clojure.test :refer [use-fixtures deftest is testing]]
   [clojure.java.jdbc :as jdbc]
   [clojure.core.async :refer [>!!]]
   [plus-minus.config :refer [env]]
   [plus-minus.multiplayer.contract :refer [->Reply ->Result] :as c]
   [plus-minus.routes.multiplayer.persist]
   [mount.core :as mount]
   [plus-minus.routes.multiplayer.persist :as persist]
   [plus-minus.routes.multiplayer.topics :as topics]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'plus-minus.config/env
     #'plus-minus.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(deftest test-saving-replies
  (jdbc/with-db-transaction [t-con db/*db*]
    (jdbc/db-set-rollback-only! t-con)
    (let [name  "someName"
          stop> (persist/subscribe> t-con)]
      (topics/push! :reply (->Reply :end name (->Result :win :no-moves)))
      (Thread/sleep 1000)
      (let [stats (db/get-online-stats t-con {:id name})]
        (is (some? stats))
        (>!! stop> 1)))))
