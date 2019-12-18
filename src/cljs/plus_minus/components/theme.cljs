(ns plus-minus.components.theme
  (:require
   [plus-minus.cookies :as cookies]
   [plus-minus.app-db :as db]))

(comment
  theme is specified with :theme keyword
  :dark :light)

(def theme-id {:dark 0, :light 1})

(def ^{:private true} pallete
  {:bg                ["#2B2B2B" "#FFFFFF"]
   :text              ["#F2F2F2" "#4E4E4E"]
   :text-gray         ["#BDBDBD" "#4F4F4F"]
   :button            ["#4E4E4E" "#E5E5E5"]
   :text-on-blue      ["#4F4F4F" "#F2F2F2"]
   :text-on-red       ["#F2F2F2" "#F2F2F2"]

   :blue              ["#56CCF2" "#2D9CDB"]
   :red               ["#EB5757" "#EB5757"]
   :yelloy            ["#F2C94C" "#F2C94C"]
   :green             ["#6FCF97" "#6FCF97"]})

(defn theme->id [key]
  (get theme-id key))

(defn id->theme [id]
  (some #(when (= id (second %)) (first %)) theme-id))

(defn set-theme [key]
  (db/put! :theme key)
  (cookies/set! :theme key))

(defn get-theme []
  (if-let [theme (db/get :theme)]
    theme
    (let [theme (or (cookies/get :theme)
                    (-> pallete :bg count rand int id->theme))]
      (set-theme theme)
      theme)))

(defn color [key]
  (let [id (theme->id (get-theme))]
    (nth (get pallete key) id)))
