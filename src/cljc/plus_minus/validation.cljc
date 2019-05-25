(ns plus-minus.validation
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str])
  #?(:cljs (:require-macros [plus-minus.validation :refer [defspec]])))

(def ^:private spec-msgs (atom {}))

(defmacro defspec [name rule & [human-msg]]
  `(do (s/def ~name ~rule)
       (when ~human-msg
         (swap! spec-msgs assoc ~name
                (str/replace ~human-msg #"\s{2}" " ")))))

(defspec ::range-2-20
  #(<= 2 (count %) 20)
  "Entity must be between 2 and 20 symbols")

(defspec ::id
  (s/and string? ::range-2-20 #(re-matches #"^[a-zA-Z0-9.\-_%+1]+" %))
  "Login must consist of Latin characters and symbols: - _ % +")

(defspec ::pass
  (s/and string? #(re-matches #"^(?=.*\d)(?=.*[a-z])(?=.*[A-Z]).{4,20}$" %))
  "Password must be between 4 and 20 characters,
   and must include at least one upper case letter,
   one lower case letter, and one numeric digit.")

(defspec ::pass-confirm
  #(= (:pass %) (:pass-confirm %))
  "Passwords do not match")

(defspec :unq/person
  (s/merge ::pass-confirm (s/keys :req-un [::id ::pass]))
  "Invalid person")

(defn- form->key-errors [form spec]
  (when-let [problems (::s/problems (s/explain-data spec form))]
    (let [ids  (-> form keys set)]
      (->> (for [problem problems
                 :let [specs (-> problem :via)
                       msg   (get @spec-msgs (last specs))
                       id    (->> specs
                                  (map (comp keyword name))
                                  (filter ids)
                                  last)]]
             [id msg])
           (into {})))))

(defn registration-errors [params]
  (form->key-errors params :unq/person))
