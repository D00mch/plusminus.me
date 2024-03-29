(ns plus-minus.game.mock
  (:require [plus-minus.app-db :as db]
            [plus-minus.multiplayer.contract :as contract]
            [plus-minus.components.theme :refer [color]]
            [plus-minus.websockets :as ws]
            [plus-minus.components.common :as c]))

(defn- purchase-mock [types]
  (let [{influence :influence :as stats} (db/get :online-user-stats)]
    (db/put!
     :modal
     (fn[]
       [c/modal
        :style  {:style {:width "95vw" :max-width "700px"}}
        :header [:div "Do you want to purchase a mock?"]
        :body   [:div [:p
                       "Mock will be applied immediately, "
                       "if you have enough influence."
                       [:br]
                       (str "Your influence: " influence "$")]]
        :footer [:div.buttons
                 (for [type types
                       :let [name  (contract/mock-name type)
                             price (contract/mock-price type)]]
                   [:div.button.is-link
                    {:style {:background-color (color :blue)
                             :color (color :text-on-blue)}
                     :on-click (fn []
                                 (ws/push-message! :mock type)
                                 (db/remove! :modal))
                     :disabled (when (> price influence) true)}
                    (str name " " price "$")])]
        ]))))

(defn mock-buttons []
  (let [disabled (not= :playing (db/get :online-status))
        btn-style {:font-size 14}]
    [:div.buttons
    [:span.button.is-info
     {:on-click #(purchase-mock #{:alert-good-luck :alert-you-gonna-lose})
      :style (assoc btn-style
                    :background-color (color :blue)
                    :color (color :text-on-blue))
      :disabled disabled}
     "SEND ALERT"]
     [:span.button.is-warning
      {:on-click #(purchase-mock #{:board-pink :board-small})
       :style btn-style
       :disabled disabled}
     "IMPAIR BOARD"]
     [:span.button.is-primary
      {:on-click #(purchase-mock #{:laughter})
       :style (assoc btn-style
                     :color (color :text-on-blue))
       :disabled disabled}
     "LAUGH"]]))

(defn mock-explained []
  [:p.disable-selection
   "Use mocks to neutralize enemy morale." [:br]
   "You can bye mock on your influence." [:br] [:br]
   "“The supreme art of war is to subdue the enemy " [:br]
   " without fighting.”
― Sun Tzu, The Art of War"])

(defmulti mock! (fn [{type :mock-type}] type))

#_(mock! {:mock-type :laughter})

(defn on-reply! [{type :mock-type, prey :prey remains :remain$, :as mock-reply}]
  (let [you   (db/get :identity)]
    (if (= prey you)
      (mock! mock-reply)
      (do
        (db/assoc-in! [:online-user-stats :influence] remains)
        (c/show-snack! (str "your mock " (name type) " applied") 4500)))))

(defn- rand-text [& texts]
  (nth texts (int (rand (count texts)))))

(defmethod mock! :alert-good-luck [_]
  (c/show-info-modal! "Oppenent's mesage:"
                      (rand-text "I wish you good luck!"
                                 "God bless you!"
                                 "Have a good game!")))

(defmethod mock! :alert-you-gonna-lose [_]
  (c/show-info-modal! "Oppenent says:"
                      (rand-text "Prepare to lose with grace!"
                                 "I will destroy you!"
                                 "Get ready to be crushed!")))

(defmethod mock! :board-pink [_]
  (c/show-snack! "Opponent tease you with colors!" 5000)
  (db/put! :online-cell-color "deeppink")
  (js/setTimeout #(db/remove! :online-cell-color) 5000))

(defmethod mock! :board-small [_]
  (c/show-snack! "Opponent tease you with sizes!" 5000)
  (db/put! :online-board-width "250px")
  (js/setTimeout #(db/remove! :online-board-width) 7000))

(defmethod mock! :laughter [_]
  (c/show-snack! "Opponent tease you with laughter!" 5000)
  (let [file-num (int (+ 1 (rand 8)))]
    (c/play-sound (str "sound/child-laugh" file-num ".wav"))))
