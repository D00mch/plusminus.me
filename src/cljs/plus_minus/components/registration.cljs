(ns plus-minus.components.registration
  (:require [reagent.core :as r]
            [plus-minus.app-db :as app-db]
            [ajax.core :as ajax]
            [plus-minus.components.common :as c]
            [plus-minus.validation :as validation]))

;; TODO: complete
(defn- register!
  "fields - r/atom with map with edit-texts fields;
  styles - r/atom with styles for this edit-texts"
  [fields styles]
  (prn "register pressed with fields " fields)
  (if-let [errors (validation/registration-errors fields)]
    (do (prn "validation errors: " errors)
        (swap! styles assoc :errors errors))
    (when-not (:errors @styles)
      (swap! styles assoc :loading :loading)
      (ajax/POST "/api/register"
                 {:header {"Accept" "application/transit+json"}
                  :params @fields
                  :handler #(do (app-db/put! :identity (:id @fields))
                                (swap! styles dissoc :loading)
                                (reset! fields {})
                                (app-db/remove! :modal))
                  :error-handler
                  #(let [resp (:response %)]
                     (swap! styles dissoc :loading)
                     (swap! styles assoc-in [:errors :id] (:message resp))
                     (prn "resp == " resp))}))))

(defn registration-form []
  (let [fields (r/atom {})
        styles (r/atom {})]
    (fn []
      [c/modal
       :style  {:style {:width 400}}
       :header [:div  "Plus-minus registration"]
       :body   [:div
                [c/input
                 :hint   "enter login"
                 :id     :id
                 :fields fields
                 :styles styles]
                [c/input
                 :type   "password"
                 :hint   "enter password"
                 :id     :pass
                 :fields fields
                 :styles styles]
                [c/input
                 :type   "password"
                 :hint   "confirm the password"
                 :id     :pass-confirm
                 :fields fields
                 :styles styles]
                ]
       :footer [:div
                [:a.button.is-primary
                 {:class (when (get @styles :loading) "is-loading")
                  :on-click #(register! fields styles)}
                 "Register"]
                [:a.button.is-danger
                 {:on-click #(app-db/remove! :modal)}
                 "Cancel"]]])))

(defn registration-button []
  [:a.button.is-light
   {:on-click #(app-db/put! :modal registration-form)}
   "register"])
