(ns plus-minus.game.mock
  (:require [plus-minus.app-db :as db]
            [plus-minus.multiplayer.contract :as contract]
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
                   [:div.button.is-link.is-outlined
                    {:on-click (fn []
                                 (ws/push-message! :mock type)
                                 (db/remove! :modal))
                     :disabled (when (> price influence) true)}
                    (str name " " price "$")])]
        ]))))

(defn mock-buttons []
  (let [disabled (not= :playing (db/get :online-status))]
    [:div.buttons
    [:span.button.is-info
     {:on-click #(purchase-mock #{:alert-good-luck :alert-you-gonna-lose})
      :disabled disabled}
     "Send Alert"]
     [:span.button.is-warning {:on-click #(purchase-mock #{:board-pink :board-small})
                              :disabled disabled}
     "Impair Board"]
     [:span.button.is-primary {:on-click #(purchase-mock #{:laughter})
                               :disabled disabled}
     "Laugh"]]))

(defn mock-explained []
  [:p
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
        (c/show-snack! (str "your mock " (name type) " applied"))))))

(defmethod mock! :alert-good-luck [_]
  (c/show-info-modal! "Oppenent's mesage" "You oppenent wishes you good luck!"))

(defmethod mock! :alert-you-gonna-lose [_]
  (c/show-info-modal! "Oppenent says:" "Prepare to lose with grace!"))

(defmethod mock! :board-pink [_]
  (c/show-snack! "Opponent tease you with colors!")
  (db/put! :online-cell-color "deeppink")
  (js/setTimeout #(db/remove! :online-cell-color) 5000))

(defmethod mock! :board-small [_]
  (c/show-snack! "Opponent tease you with sizes!")
  (db/put! :online-board-width "250px")
  (js/setTimeout #(db/remove! :online-board-width) 7000))

(defmethod mock! :laughter [_]
  (c/show-snack! "Opponent tease you with laughter!")
  (let [file-num (int (+ 1 (rand 5)))]
    (def audio (js/Audio. (str "sound/child-laugh" file-num ".wav")))
    (.play audio)))
