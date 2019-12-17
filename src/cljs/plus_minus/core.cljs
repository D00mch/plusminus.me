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
   [plus-minus.game.statistics :as stats]
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
   [:div.column.is-half
    [about/component]]
   [:div.column.is-half
    [rich/donate-explained]
    [:div.flex
     [:img {:src "/img/warning_clojure.png"}]
     [:div.flex.column.left-mar
      [:div [rich/donation]]
      [:div {:style {:margin-left 10}} [rich/donation-ru]]]]]])

(defn single-page []
  [bot/game-component])

(defn- menu-item [name]
  [:a {:href (str "#/" name)
       :style {:margin-top 10, :margin-right 10}
       :class (<class
               #(with-meta
                  {:color (color :text)}
                  {:pseudo {:hover {:color (color :blue)}}}))} name])

(defn home-page []
  [:div.flex {:style {:justify-content "center"
                      :align-items "center"
                      :height "100vh"}}
   [:div
    [:div.flex.column
     [menu-item "single"]
     [menu-item "multiplayer"]
     [menu-item "management"]
     [menu-item "user"]
     [menu-item "about"]]]])

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

(defn statistics-page []
  (stats/stats-component))

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
   :multiplayer #'multiplayer-page
   :statistics  #'statistics-page})

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
     ["/about" :about]
     ["/multiplayer" :multiplayer]
     ["/statistics" :statistics]]))

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
  (stats/init-stats!)
  (mount-components))

