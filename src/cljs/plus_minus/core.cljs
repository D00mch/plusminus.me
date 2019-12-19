(ns plus-minus.core
  (:require
   [reagent.core :as r]
   [goog.events :as events]
   [goog.history.EventType :as HistoryEventType]
   [plus-minus.ajax :as ajax]
   [plus-minus.components.registration :as reg]
   [plus-minus.components.login :as login]
   [plus-minus.app-db :as db]
   [plus-minus.game.bot :as bot]
   [plus-minus.game.online :as online]
   [plus-minus.websockets :as ws]
   [plus-minus.components.common :as c]
   [plus-minus.game.about :as about]
   [plus-minus.game.management :as management]
   [plus-minus.components.theme :as theme :refer [color]]
   [herb.core :refer [<class]]
   [reitit.core :as reitit]
   [ajax.core :as a]
   [clojure.string :as string]
   [plus-minus.components.rich :as rich])
  (:import goog.History))

(defn- init-online-state! []
  (when (db/get :identity)
    (ws/ensure-websocket!
     online/has-reply!
     #(do
        (online/initial-state!)
        (ws/push-message! :state)))))

(defn about-page []
  [:div.container>div.column>div.columns
   {:style {:background-color (color :bg), :padding 12}}
   [:div.column.is-half
    [about/component]]
   [:div.column.is-half
    [rich/donate-explained]
    [:div.flex.left-mar
     [:div [rich/donation]]
     [:div {:style {:margin-left 10}} [rich/donation-ru]]]
    [:a {:href (str (if (db/get :dev?) "http://" "https://")
                    (.-host js/location)
                    "/privacy-policy")}
     "privacy policy"]]])

(defn single-page []
  [bot/game-component])

(defn- hover []
  ^{:pseudo {:hover {:color (color :blue)}}}
  {:color (color :text)})

(defn- menu-item [name & on-click]
  [:a {:href (str "#/" name)
       :on-click on-click
       :style {:margin-top 10, :margin-right 10, :font-size 24}
       :class (<class hover)} name])

(defn home-page []
  [:div.center-hv
   [:div>div.flex.column
    [menu-item "single"]
    [menu-item "multiplayer"]
    [menu-item "management" (management/init-managment)]
    [menu-item "user"]
    [menu-item "about"]]])

(defn multiplayer-page []
  (fn []
    (let [anonim        (not (db/get :identity))
          connected (db/get :websocket-connected)]
      (if connected
        (init-online-state!)
        (js/setTimeout #(init-online-state!) 1000))
      (if anonim
        [:section.section>div.container>div.content
         [:label "Authenticate to play with other people"]]
        [online/game-component]))))

(defn managmenet-page []
  (management/component))

(defn- auth-item [name & [onclick href]]
  [:a {:style {:margin-top 10, :margin-right 10, :font-size 24}
       :class (<class hover)
       :href href
       :on-click (or onclick #(c/clear-cache))}
   name])

(defn- on-logged-in []
  (init-online-state!)
  (bot/init-game-state!))

(defn user-page []
  [:div.center-hv
   (if (db/get :identity)
     [:div>div.flex.column
      [auth-item "logout" #(do (ws/close!) (login/logout!))]
      [auth-item "delete account!" #(reg/delete-account! ws/close!)]
      [auth-item "change password" #(db/put! :modal (reg/change-pass-form))]]
     [:div>div.flex.column
      [auth-item "google auth" nil "/oauth/init"]
      [auth-item "register" #(db/put! :modal (reg/registration-form on-logged-in))]
      [auth-item "login" #(db/put! :modal (login/login-form on-logged-in))]])])

(defn modal []
  (when-let [session-modal (db/get :modal)]
    [session-modal]))

(defn snack []
  [:div {:style {:height 25}}
   (when-let [snack (db/get :snack)]
     [snack])])

(defn common-top-el []
  (when-let [el (db/get :common-el)]
    [el]))

;; -------------------------
;; Routes

(def pages
  {:home        #'home-page
   :single      #'single-page
   :about       #'about-page
   :user        #'user-page
   :multiplayer #'multiplayer-page
   :management  #'managmenet-page})

(defn page []
  [:div {:style {:background-color (color :bg)
                 :height "100vh"}}
   [modal]
   [(pages (db/get :page))]
   [common-top-el]])

(def router
  (reitit/router
    [["/" :home]
     ["/single" :single]
     ["/user" :user]
     ["/about" :about]
     ["/multiplayer" :multiplayer]
     ["/management" :management]]))

(defn match-route [uri]
  (prn :uri uri)
  (->> (or (not-empty (string/replace uri #"^.*#" "")) "/")
       (reitit/match-by-path router)
       :data
       :name))
;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (db/put! :page (match-route (.-token event)))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-components []
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (ajax/load-interceptors!)
  (db/put! :identity js/identity)
  (db/put! :dev? (= (.-host js/location) "localhost:3000"))

  ;; init states
  (init-online-state!)
  (bot/init-game-state!)
  (mount-components))

