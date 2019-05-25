(ns plus-minus.app
  (:require [plus-minus.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
