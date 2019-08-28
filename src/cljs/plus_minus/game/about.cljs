(ns plus-minus.game.about)

(defn component []
  [:div.content
   [:h5 "Rules"]
   [:p
    "In this turn based game you need to get more points than your oppenent." [:br]
    "Select a number from the colored line and this number will be added to your points." [:br]
    "Perky oppenent will have to choose from the perpendicular line  where you've just moved."]

   [:h5 "Project state"]
   [:p
    "This project is under active development." [:br]
    "Feel free to contact me if you have any issues or propositions"]
   [:a {:href "mailto:arturdumchev@gmail.com"} "arturdumchev@gmail.com"]
   [:br]
   [:a {:href "https://github.com/liverm0r/Plus-Minus-Fullstack/"} "github"]
   [:p "published with "
    [:a {:href "https://m.do.co/c/edb551a6bfca"} "digitalocean.com"]]])
