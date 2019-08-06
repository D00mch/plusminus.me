(ns plus-minus.core
  (:require
   [reagent.core :as r]
   [goog.events :as events]
   [goog.history.EventType :as HistoryEventType]
   [markdown.core :refer [md->html]]
   [plus-minus.ajax :as ajax]
   [plus-minus.components.registration :as reg]
   [plus-minus.components.login :as login]
   [plus-minus.app-db :as db]
   [plus-minus.game.bot :as bot]
   [ajax.core :refer [GET POST]]
   [reitit.core :as reitit]
   [clojure.string :as string])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :active (when (= page (db/get :page)) "is-active")}
   title])

(defn account-actions [id]
  (r/with-let [expanded? (r/atom false)]
    [:div.buttons>div.dropdown
     {:class (when @expanded? "is-active")}
     [:div.dropdown-trigger
      [:button.button
       {:on-click      #(swap! expanded? not)
        :aria-haspopup "true"
        :aria-controls "dropdown-menu"}
       [:span "Sign Out"]
       [:span.icon.is-small
        [:i.fas.fa-angle-down
         {:aria-hidden "true"}]]]
      [:div#dropdown-menu.dropdown-menu
       {:role "menu"}
       [:div.dropdown-content
        [:a.dropdown-item
         {:on-click login/logout!}
         "Logout"]
        [:a.dropdown-item
         {:on-click #(reg/delete-account! (db/get :identity))}
         "Delete account"]]]]]))

(defn user-menu []
  (if-let [id (db/get :identity)]
    [account-actions id]
    [:div.navbar-end
     [:div.buttons
      (reg/registration-button)
      (login/login-button)]]))

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
       [nav-link "#/" "Home" :home]
       [nav-link "#/about" "About" :about]]
      [user-menu]]]))

(defn about-page []
  [:section.section>div.container>div.content
   [:img {:src "/img/warning_clojure.png"}]])

(defn digit-form []
  [:div.box {:style {:display "flex"
                     :align-items "center"
                     :justify-content "center"
                     :width 40
                     :height 40}}
   [:p "1"]])

(defn home-page []
  [bot/game-matrix])

(def pages
  {:home #'home-page
   :about #'about-page})

(defn modal []
  (when-let [session-modal (db/get :modal)]
    [session-modal]))

(defn page []
  [:div
   [modal]
   [(pages (db/get :page))]])

;; -------------------------
;; Routes

(def router
  (reitit/router
    [["/" :home]
     ["/about" :about]]))

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

(defn init! []
  (prn "initing")
  (ajax/load-interceptors!)
  #_(fetch-docs!)
  (hook-browser-navigation!)
  (db/put! :identity js/identity)
  (bot/init-game-state)
  (mount-components))
