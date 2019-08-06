(ns plus-minus.components.login
  (:require [reagent.core :as r]
            [plus-minus.app-db :as db]
            [goog.crypt.base64 :as b64]
            [clojure.string :as str]
            [ajax.core :as ajax]
            [plus-minus.game.bot :as bot]
            [plus-minus.components.common :as c]))

(defn- encode-auth [user pass]
  (->> (str user ":" pass) (b64/encodeString) (str "Basic ")))

(defn- login! [fields styles]
  (let [{:keys [id pass]} @fields]
    (swap! styles dissoc :errors)
    (swap! styles assoc :loading :loading)
    (ajax/POST "api/login"
               {:headers {"authorization" (encode-auth (str/trim id) pass)}
                :handler #(do
                            (db/remove! :modal)
                            (db/put! :identity id)
                            (reset! fields nil)
                            (bot/init-game-state))
                :error-handler
                #(let [{message :message} (get % :response)]
                   (swap! styles dissoc :loading)
                   (swap! styles assoc-in [:errors :id] message))})))

(defn- login-form []
  (let [fields (r/atom {})
        styles (r/atom {:id {:auto-focus true}})]
    (fn []
      [c/modal
       :style  {:style {:width 400}}
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
                 :on-save (fn [_] (login! fields styles))]]
       :footer (c/do-or-close-footer
                :name   "Login"
                :on-do  #(login! fields styles)
                :styles styles)])))

(defn login-button []
  [:a.button.is-light
   {:on-click #(db/put! :modal login-form)}
   "Login"])

(defn logout! []
  (ajax/POST "/api/logout"
             {:handler (fn [] (db/remove! :identity))}))
