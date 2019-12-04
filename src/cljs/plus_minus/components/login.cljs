(ns plus-minus.components.login
  (:require [reagent.core :as r]
            [plus-minus.app-db :as db]
            [goog.crypt.base64 :as b64]
            [clojure.string :as str]
            [ajax.core :as ajax]
            [plus-minus.components.common :as c]))

(defn- encode-auth [user pass]
  (->> (str user ":" pass) (b64/encodeString) (str "Basic ")))

(defn- login! [fields styles on-login]
  (let [{:keys [id pass]} @fields]
    (swap! styles dissoc :errors)
    (swap! styles assoc :loading :loading)
    (ajax/POST "api/login"
               {:headers {"authorization" (encode-auth (str/trim id) pass)}
                :handler #(do
                            (db/remove! :modal)
                            (db/put! :identity id)
                            (reset! fields nil)
                            (on-login))
                :error-handler
                #(let [{message :message} (get % :response)]
                   (swap! styles dissoc :loading)
                   (swap! styles assoc-in [:errors :id] message))})))

(defn- login-form [on-login]
  (fn []
    (let [fields (r/atom {})
          styles (r/atom {:id {:auto-focus true}})]
      (fn []
        [c/modal
         :header [:div "Plus-minus login"]
         :body   [:div
                  [c/input
                   :id     :id
                   :hint   "enter login"
                   :fields fields
                   :styles styles]
                  [c/input
                   :id      :pass
                   :type    "password"
                   :hint    "enter password"
                   :fields  fields
                   :styles  styles
                   :on-save (fn [_] (login! fields styles on-login))]]
         :footer (c/do-or-close-footer
                  :name   "Login"
                  :on-do  #(login! fields styles on-login)
                  :styles styles)]))))

(defn login-button [on-login]
  [:a.button.is-light
   {:on-click #(db/put! :modal (login-form on-login))}
   "Login"])

(defn logout! []
  (c/clear-cache)
  (ajax/POST "/api/logout"
             {:handler (fn []
                         (db/remove! :identity)
                         (db/remove! :game-statistics))}))
