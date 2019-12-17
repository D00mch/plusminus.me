(ns plus-minus.components.theme
  (:require
   [plus-minus.app-db :as db]))

(comment
  theme is specified with :theme keyword
  :dark :light)

;; dark 0, light 1
(def ^{:private true} pallete
  {:bg     ["#2B2B2B" "#FFFFFF"]
   :text   ["#F2F2F2" "#4F4F4F"]
   :button ["#4E4E4E" "#E5E5E5"]

   :blue   ["#56CCF2" "#2D9CDB"]
   :red    ["#EB5757" "#EB5757"]
   :yelloy ["#F2C94C" "#F2C94C"]
   :green  ["#6FCF97" "#6FCF97"]
   })

(defn theme->id [key]
  (case key
    :dark 0,
    :light 1))

(defn color [key]
  (let [id (or (some-> (db/get :theme) theme->id) 0)]
    (nth (get pallete key) id)))

(defn set-theme [key] (db/put! :theme key))
