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
   [reitit.core :as reitit]
   [clojure.string :as string])
  (:import goog.History))

(defn- close-expanded! [expanded?] (reset! expanded? false))

(defn nav-link [uri title page expanded?]
  [:a.navbar-item
   {:href   uri
    :on-click #(close-expanded! expanded?)
    :active (when (= page (db/get :page)) "is-active")}
   title])

(defn- init-online-state! []
  (when (db/get :identity)
    (ws/ensure-websocket!
     online/on-reply!
     #(do
        (online/initial-state!)
        (ws/push-message! :state)))))

(defn account-actions [expanded?]
  [:div.navbar-item.has-dropdown.is-hoverable
   [:a.navbar-link "Sign Out"]
   [:div.navbar-dropdown
    [:a.navbar-item
     {:on-click #(do (close-expanded! expanded?)
                     (ws/close!)
                     (login/logout!))}
     "Logout"]
    [:a.navbar-item
     {:on-click #(do (close-expanded! expanded?)
                     (ws/close!)
                     (reg/delete-account! (db/get :identity)))}
     "Delete account!"]]])

(defn- on-logged-in [expanded?]
  (init-online-state!)
  (close-expanded! expanded?)
  (bot/init-game-state!))

(defn user-menu [expanded?]
  (if (db/get :identity)
    [account-actions expanded?]
    [:div.navbar-end>div.navbar-item>div.buttons
     (reg/registration-button #(on-logged-in expanded?))
     (login/login-button #(on-logged-in expanded?))]))

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
    [:nav.navbar.is-info>div.container
     [:div.navbar-brand
      [:a.navbar-item {:href "/" :style {:font-weight :bold}} "Plus-minus"]
      [:span.navbar-burger.burger
       {:data-target :nav-menu
        :on-click #(swap! expanded? not)
        :class (when @expanded? :is-active)}
       [:span][:span][:span]]]
     [:div#nav-menu.navbar-menu
      {:class (when @expanded? :is-active)}
      [:div.navbar-start
       [nav-link "#/" "Home" :home expanded?]
       [nav-link "#/about" "About" :about expanded?]
       [nav-link "#/multiplayer" "Multiplayer" :multiplayer expanded?]
       [nav-link "#/statistics" "Statistics" :statistics expanded?]]
      [user-menu expanded?]]]))

(defn about-page []
  [:session.session>div.container>div.column>div.columns
   [:div.column.is-half
    [about/component]]
   [:div.column.is-half
    [:img {:src "/img/warning_clojure.png"}]]])

(defn home-page []
  [bot/game-component])

(defn multiplayer-page []

  (cond
    (not (db/get :identity))      [:section.section>div.container>div.content
                                   [:label "Authenticate to play with other people"]]
    (db/get :websocket-connected) [online/game-component]
    :else                         [:section.section>div.container>div.content
                                   [:p
                                    "Loading multiplayer state..."
                                    [:br]
                                    "Try to reload the page if it's taking too long"]]))

(defn statistics-page []
  (stats/stats-component))

(def pages
  {:home        #'home-page
   :about       #'about-page
   :multiplayer #'multiplayer-page
   :statistics  #'statistics-page})

(defn modal []
  (when-let [session-modal (db/get :modal)]
    [session-modal]))

(defn snack []
  [:div {:style {:height 25}}
   (when-let [snack (db/get :snack)]
     [snack])])

(defn page []
  [:div
   [modal]
   [snack]
   [(pages (db/get :page))]])

;; -------------------------
;; Routes

(def router
  (reitit/router
    [["/" :home]
     ["/about" :about]
     ["/multiplayer" :multiplayer]
     ["/statistics" :statistics]]))

(defn match-route [uri]
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
#_(defn fetch-docs! []
  (GET "/docs" {:handler #(db/put! :docs %)}))

(defn mount-components []
  (r/render [#'navbar] (.getElementById js/document "navbar"))
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! [dev?]
  (db/put! :dev? dev?)
  (ajax/load-interceptors!)
  #_(fetch-docs!)
  (hook-browser-navigation!)
  (db/put! :identity js/identity)
  (init-online-state!)
  (bot/init-game-state!)
  (stats/init-stats!)
  (mount-components))
