(ns plus-minus.components.registration
  (:require [reagent.core :as r]
            [plus-minus.app-db :as app-db]
            [ajax.core :as ajax]
            [plus-minus.components.common :as c]
            [plus-minus.validation :as validation]))

(defn- errors-handler [styles]
  #(let [resp (:response %)]
     (swap! styles dissoc :loading)
     (swap! styles assoc-in [:errors :id] (:message resp))))

(defn- make-req! [styles req]
  (when-not (:errors @styles)
    (swap! styles assoc :loading :loading)
    (req)))

(defn- register!
  "fields - r/atom with map with edit-texts fields;
  styles - r/atom with styles for this edit-texts"
  [fields styles on-reg]
  (swap! styles assoc :errors (validation/registration-errors @fields))
  (make-req!
   styles
   #(ajax/POST "/api/register"
               {:header {"Accept" "application/transit+json"}
                :params @fields
                :handler (fn []
                           (app-db/put! :identity (:id @fields))
                           (swap! styles dissoc :loading)
                           (reset! fields {})
                           (app-db/remove! :modal)
                           (on-reg))
                :error-handler (errors-handler styles)})))

(defn- change-pass!
  [fields styles]
  (swap! styles assoc :errors (validation/change-pass-errors (:pass @fields)))
  (make-req!
   styles
   #(ajax/POST "/api/restricted/change-pass"
               {:header {"Accept" "application/transit+json"}
                :params @fields
                :handler (fn []
                           (swap! styles dissoc :loading)
                           (reset! fields {})
                           (app-db/remove! :modal))
                :error-handler (errors-handler styles)})))

(defn registration-form [on-reg]
  (let [fields (r/atom {})
        styles (r/atom {:id {:auto-focus true}})]
    (fn [
]
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

(defn- alert-delete [on-delete]
  (fn []
    (c/modal
     :header [:div "Warning!"]
     :body   [:div [:label "Are you sure you want to completelly wipe out all your data?"]]
     :footer (c/do-or-close-footer
              :styles (r/atom {:id {:auto-focus true}})
              :name   "Delete!"
              :on-do  (fn []
                        (app-db/remove! :modal)
                        (on-delete))))))

(defn delete-account! [on-delete]
  (c/clear-cache)
  (app-db/put!
   :modal
   (alert-delete #(ajax/POST "/api/restricted/delete-account"
                             {:handler (fn []
                                         (on-delete)
                                         (app-db/remove! :identity)
                                         (app-db/put! :page :home))}))))

(defn change-pass-form []
  (let [fields (r/atom {})
        styles (r/atom {:id {:auto-focus true}})]
    (fn []
      [c/modal
       :header [:div "Changing password"]
       :body   [:div
                [c/input
                 :id     :pass
                 :type   "password"
                 :hint   "new password"
                 :fields fields
                 :styles styles]]
       :footer (c/do-or-close-footer
                :name "Change!"
                :on-do #(change-pass! fields styles)
                :styles styles)])))
