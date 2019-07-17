(ns user
  "Userspace functions you can run by default in your local REPL."
  (:require
    [plus-minus.config :refer [env]]
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]
    [mount.core :as mount]
    [plus-minus.figwheel :refer [start-fw stop-fw cljs]]
    [plus-minus.core :refer [start-app]]
    [plus-minus.db.core]
    [conman.core :as conman]
    [luminus-migrations.core :as migrations]))

(set! *warn-on-reflection* true)

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(defn start
  "Starts application.
  You'll usually want to run this on startup."
  []
  (mount/start-without #'plus-minus.core/repl-server))

(defn stop
  "Stops application."
  []
  (mount/stop-except #'plus-minus.core/repl-server))

(defn restart
  "Restarts application."
  []
  (stop)
  (start))

(defn restart-db
  "Restarts database."
  []
  (mount/stop #'plus-minus.db.core/*db*)
  (mount/start #'plus-minus.db.core/*db*)
  (binding [*ns* 'plus-minus.db.core]
    (conman/bind-connection plus-minus.db.core/*db* "sql/queries.sql")))

(defn reset-db
  "Resets database."
  []
  (migrations/migrate ["reset"] (select-keys env [:database-url])))

(defn migrate
  "Migrates database up for all outstanding migrations."
  []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback
  "Rollback latest database migration."
  []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration
  "Create a new up and down migration file with a generated timestamp and `name`."
  [name]
  (migrations/create name (select-keys env [:database-url])))

(defn sa "start all" [] (start) (start-fw) (cljs))
