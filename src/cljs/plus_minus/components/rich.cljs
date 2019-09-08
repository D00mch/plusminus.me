(ns plus-minus.components.rich)

;; donation link
;; https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=LZCUTSKRWUDZE&source=url

(defn donation []
  [:form {:action "https://www.paypal.com/cgi-bin/webscr",
          :method "post", :target "_top"}
   [:input {:type "hidden", :name "cmd", :value "_s-xclick"}]
   [:input {:type "hidden", :name "hosted_button_id", :value "LZCUTSKRWUDZE"}]
   [:input.disable-selection
    {:type "image", :border "0", :name "submit",
     :width 150, :height 62 ;;750 309
     :alt "Donate with PayPal button",
     :title "PayPal - The safer, easier way to pay online!",
     :src "img/paypal.jpg"}]
   [:img {:alt "", :border "0", :width "1", :height "1",
          :src "https://www.paypal.com/en_KZ/i/scr/pixel.gif"}]])

(defn donation-ru []
  [:form.top-mar
   {:method "POST", :action "https://money.yandex.ru/quickpay/confirm.xml"}
   [:input {:type "hidden", :name "receiver", :value "410012912961469"}]
   [:input {:type "hidden", :name "formcomment", :value "Проект «Плюс Минус»: математическая головоломка"}]
   [:input {:type "hidden", :name "short-dest", :value "Игра Плюс-Минус"}]
   [:input {:type "hidden", :name "label", :value "$order_id"}]
   [:input {:type "hidden", :name "quickpay-form", :value "donate"}]
   [:input {:type "hidden", :name "targets", :value "На поддержку игры"}]
   [:input {:type "hidden", :name "sum", :value "399", :data-type "number"}]
   [:input {:type "hidden", :name "comment", :value "На расходы по хостингу, дизайн, новые фичи"}]
   [:input {:type "hidden", :name "need-fio", :value "false"}]
   [:input {:type "hidden", :name "need-email", :value "false"}]
   [:input {:type "hidden", :name "need-phone", :value "false"}]
   [:input {:type "hidden", :name "need-address", :value "false"}]
   [:label.disable-selection
    [:input {:type "radio", :name "paymentType", :value "PC"}] "Yandex.Money"]
   [:br]
   [:label.disable-selection
    [:input {:type "radio", :name "paymentType", :value "AC"}] "Bank Card"]
   [:br]
   [:input.button.is-small {:type "submit", :value "Send!"
                            :style {:width 100 :margin-top 10}}]])

(defn donate-justify []
  [:p.content.disable-selection {:style {:text-align nil, :margin-top 10}}
   "Your help will be match appreciated!" [:br]
   "You will continue seeing no adds :)"])

(defn donate-explained []
  [:div.content.disable-selection
   [:h5 "Consider Dontaion"]
   [:p
    "This project is free. No adds. All feature are opened for everyone."
    [:br]
    "With your help, this project may grow sky-high :)"]])
