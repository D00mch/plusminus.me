(ns plus-minus.utils)

(defn ex-chain [^Exception e]
  (take-while some? (iterate ex-cause e)))
