(ns plus-minus.components.rich)

;; donation link
;; https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=LZCUTSKRWUDZE&source=url

(defn donation []
  [:form {:action "https://www.paypal.com/cgi-bin/webscr",
          :method "post", :target "_top"}
   [:input {:type "hidden", :name "cmd", :value "_s-xclick"}]
   [:input {:type "hidden", :name "hosted_button_id", :value "LZCUTSKRWUDZE"}]
   [:input {:type "image", :border "0", :name "submit",
            :width 150, :height 62 ;;750 309
            :alt "Donate with PayPal button",
            :title "PayPal - The safer, easier way to pay online!",
            :src "img/paypal.jpg"}]
   [:img {:alt "", :border "0", :width "1", :height "1",
          :src "https://www.paypal.com/en_KZ/i/scr/pixel.gif"}]])

(defn donate-justify []
  [:p {:style {:text-align nil, :margin-top 10}}
   "We need your help!" [:br]
   "You don't see adds," [:br]
   "We don't sell stuff." [:br][:br]
   "The game is free" [:br]
   "As you want it to be!"])
