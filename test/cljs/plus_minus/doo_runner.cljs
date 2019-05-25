(ns plus-minus.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [plus-minus.core-test]))

(doo-tests 'plus-minus.core-test)

