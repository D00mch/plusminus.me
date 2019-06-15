(ns plus-minus.routes.services.state
  (:require [plus-minus.db.core :as db]
            [ring.util.http-response :as response]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]))


#_(db/upsert-state! 
 {:id "dumch" :cells [1 2 3 4] :start 0 :hrz-turn true :moves []})

