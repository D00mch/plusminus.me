(ns plus-minus.game.about
  (:require [plus-minus.app-db :as db]
            [plus-minus.components.theme :refer [color]]))

(defn component []
  (let [text {:style {:color (color :text)}}
        title {:style {:color (color :blue)}}]
    [:div.content
     [:h5 title "Rules"]
     [:p text
      "In this turn based game you need to get more points than your oppenent." [:br]
      "Select a number from the colored line and this number will be added to your points." [:br]
      "Perky oppenent will have to choose from the perpendicular line  where you've just moved."]

     [:h5 title "Project state"]
     [:p text
      "This project is under active development." [:br]
      "Feel free to contact me if you have any issues or propositions"]
     [:a (merge text {:href "mailto:arturdumchev@gmail.com"})
      "arturdumchev@gmail.com"]
     [:br]
     [:a {:href "https://github.com/liverm0r/Plus-Minus-Fullstack/"}
      "github"]
     [:p text "published with "
      [:a {:href "https://m.do.co/c/edb551a6bfca"} "digitalocean.com"]]]))
