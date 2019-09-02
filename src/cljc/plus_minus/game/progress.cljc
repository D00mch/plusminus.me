(ns plus-minus.game.progress
  (:require [clojure.spec.alpha :as s]
            [plus-minus.game.game :as g]
            [plus-minus.game.board :as b]))

(s/def ::ach-name #{"Clever guy" "Egghead" "Genius" "Lucky Devil" "Legend"})

(s/def ::count (s/or :n zero? :n pos-int?))
(s/def ::win ::count)
(s/def ::lose ::count)
(s/def ::draw ::count)
(s/def ::win-streak ::count)
(s/def ::iq pos-int?)
(s/def ::progress (s/coll-of ::ach-name :distinct true))
(s/def ::opened-rows (s/coll-of ::b/row-size :distinct true))
(s/def ::statistics (s/keys :req-un [::win ::lose ::draw
                                     ::iq ::win-streak ::opened-rows]
                            :opt-un [::progress]))

(s/def ::type #{:iq :win-streak})
(s/def ::require (s/keys :req-un [::type]))
(s/def ::achivment (s/keys :req-un [::ach-name ::require]))

(def achivements
  [{:ach-name "Clever guy"
    :require {:type :iq, :value 110}
    :earn
    {:type :rows, :value 6
     :name "You are clever enough to play 6-size matrix."}}
   {:ach-name "Egghead"
    :require {:type :iq, :value 120}
    :earn
    {:type :rows, :value 7
     :name "You have superior intelligence, see if 7-size matrix fits you."}}
   {:ach-name "Genius"
    :require {:type :iq, :value 140}
    :earn
    {:type :rows, :value 8
     :name "You are smarter than 98% of the population, enjoy playing 8-size matrix."}}
   {:ach-name "Lucky Devil"
    :require {:type :win-streak, :value 5}
    :earn
    {:type :rows, :value 9
     :name "You are lucky enough to play 9-size matrix!"}}
   {:ach-name "Legend"
    :require {:type :iq, :value 180}
    :earn
    {:type :custom
     :name (str "You are standing in line with people like Einstein, Newton, Tesla."
                "Your name will be put on the main page of the site.")}}])

(def empty-stats {:win 0 :lose 0 :draw 0 :iq 100 :win-streak 0})

(defn- update-achivs [{progress :progress :as stats}]
  (let [achives  (for [{n :ach-name {:keys [type value]} :require} achivements
                       :let [user-value (get stats type)]]
                   (when (>= user-value value) n))
        achives  (filter some? achives)
        progress (distinct (concat progress achives))]
    (assoc stats :progress progress)))

(defn- update-row-sizes [{progress :progress, :as stats}]
  (let [opened-rows (cons 5 (for [{n :ach-name, {t :type, v :value} :earn} achivements
                                  :when (= t :rows)
                                  :when (some #{n} progress)]
                              v))]
    (assoc stats :opened-rows opened-rows)))

(defn- update-win-streak [stats result]
  (if (= result :win)
    (update stats :win-streak inc)
    (assoc stats :win-streak 0)))

(defn check-stats [stats]
  (-> stats
      (assoc :iq (g/calc-iq stats))
      (update :iq #(or % 100))
      (update :win-streak #(or % 0))
      update-achivs
      update-row-sizes))

(defn on-end [stats result]
  (-> stats check-stats (update-win-streak result)))
