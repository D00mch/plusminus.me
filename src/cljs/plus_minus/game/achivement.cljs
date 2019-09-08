(ns plus-minus.game.achivement
  (:require [reagent.core :as r]
            [plus-minus.game.progress :refer [achivements]]))

;; icons https://fontawesome.com/icons?d=gallery&q=achi

(def ^{:private true, :cons true} icon-done "far fa-check-circle")
(def ^{:private true, :cons true} icon-hover "fas fa-angle-down")

(defn- requirement-text [{:keys [type value]}]
  (cond
    (= type :iq)         (str "IQ " value " is required")
    (= type :win-streak) (str "win " value " games in a row")))

(defn- ach-dropdown [{{earn :name} :earn, n :ach-name, req :require} done active]
  [:div.dropdown
   {:class (when (= @active n) "is-active")
    :on-click #(reset! active (if (= @active n) nil n))
    :style {:margin-top 10}}
   [:div.dropdown-trigger
    [:button.button {:aria-haspopup "true", :aria-controls "dropdown-menu2"}
     (when done [:span.icon.is-small>i {:class icon-done}])
     [:span n]
     [:span.icon.is-small
      [:i {:class icon-hover}]]]]
   [:div {:class "dropdown-menu", :id "dropdown-menu2", :role "menu"}
    [:div.dropdown-content
     [:div.dropdown-item [:p earn]]
     [:hr.dropdown-divider]
     [:div.dropdown-item
      [:p (requirement-text req)]]]]])

(defn component [_]
  (let [active (r/atom nil)]
    (fn [progress]
      [:div.flex.column
       [:label.disable-selection {:style {:margin-bottom 10}} "Achivements:"]
       (doall
        (for [{n :ach-name :as a} achivements]
          ^{:key (str "achivment-" n)}
          [ach-dropdown a (some #{(:ach-name a)} progress) active]))])))
