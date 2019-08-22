(ns plus-minus.components.registration
  (:require [reagent.core :as r]
            [plus-minus.app-db :as app-db]
            [ajax.core :as ajax]
            [plus-minus.components.common :as c]
            [plus-minus.validation :as validation]))

(defn- register!
  "fields - r/atom with map with edit-texts fields;
  styles - r/atom with styles for this edit-texts"
  [fields styles on-reg]
  (swap! styles assoc :errors (validation/registration-errors @fields))
  (when-not (:errors @styles)
    (swap! styles assoc :loading :loading)
    (ajax/POST "/api/register"
               {:header {"Accept" "application/transit+json"}
                :params @fields
                :handler #(do (app-db/put! :identity (:id @fields))
                              (swap! styles dissoc :loading)
                              (reset! fields {})
                              (app-db/remove! :modal)
                              (on-reg))
                :error-handler
                #(let [resp (:response %)]
                   (swap! styles dissoc :loading)
                   (swap! styles assoc-in [:errors :id] (:message resp)))})))

(defn- registration-form [on-reg]
  (let [fields (r/atom {})
        styles (r/atom {:id {:auto-focus true}})]
    (fn []
      [c/modal
       :header [:div  "Plus-minus registration"]
       :body   [:div
                [c/input
                 :id     :id
                 :hint   "enter login"
                 :fields fields
                 :styles styles]
                [c/input
                 :id     :pass
                 :type   "password"
                 :hint   "enter password"
                 :fields fields
                 :styles styles]
                [c/input
                 :id     :pass-confirm
                 :type   "password"
                 :hint   "confirm the password"
                 :fields fields
                 :styles styles
                 :on-save (fn [_] (register! fields styles on-reg))]]
       :footer (c/do-or-close-footer
                :name   "Register"
                :on-do  #(register! fields styles on-reg)
                :styles styles)])))

(defn registration-button [on-reg]
  [:a.button.is-light
   {:on-click #(app-db/put! :modal (registration-form on-reg))}
   "Register"])

(defn delete-account! [id]
  (ajax/POST "/api/restricted/delete-account"
             {:handler #(do
                          (app-db/remove! :identity)
                          (app-db/put! :page :home))}))
