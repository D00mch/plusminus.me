(ns plus-minus.game.management
  (:require [plus-minus.game.statistics :as stats]
            [plus-minus.game.state :as s]
            [plus-minus.game.board :as b]
            [plus-minus.game.progress :refer [achivements]]
            [plus-minus.game.bot :as bot]
            [plus-minus.game.online :as online]
            [plus-minus.websockets :as ws]
            [plus-minus.components.board :as board]
            [herb.core :refer [<class]]
            [plus-minus.app-db :as db]
            [plus-minus.components.theme :as theme :refer [color]]))

(def ^{:private true, :cons true} icon-done "far fa-check-circle")
(def ^{:private true, :cons true} icon-lock "fa fa-lock")

(defn- init-online-state! []
  (when (db/get :identity)
    (ws/ensure-websocket!
     online/has-reply!
     #(do
        (online/initial-state!)
        (ws/push-message! :state)))))

(defn init-managment []
  (init-online-state!)
  (stats/init-stats!))

(defn single-row-size []
  [board/game-settings
   :size-range (or (db/get-in [:game-statistics :opened-rows])
                   [b/row-count-min])
   :state      (:game-state @db/state)
   :on-change  #(let [row-size %
                      moves    (-> :game-state db/get :moves seq)]
                  (if moves
                    (db/put! :modal (bot/alert-restart row-size))
                    (bot/change-state (s/state-template row-size))))])

(defn multiplayer-row-size []
  [board/game-settings
   :label      (db/get :online-row)
   :size-range (cons :quick (range b/row-count-min b/row-count-max-excl))
   :state      (:online-state @db/state)
   :on-change  (fn [row-size]
                 (db/put! :online-row row-size)
                 (online/initial-state!))])

(defn- requirement-text [{:keys [type value]}]
  (cond
    (= type :iq)         (str "require: iq " value)
    (= type :win-streak) (str "require: win " value " games in a row")))

(defn- achivements-comp [achivements text-style text2-style]
  (let [progress (db/get-in [:game-statistics :progress])]
    (for [{title :ach-name :as a} achivements
          :let [{{earn :name} :earn, req :require} a]]
      ^{:key (str "achivment-" title)}
      [:div {:style {:display "flex"
                     :margin-top 4
                     :flex-direction "column"}}
       [:div.flex {:style {:justify-content "space-between"}}
        [:div text2-style title]
        [:span.icon.is-small>i
         {:class (if (some #{(:ach-name a)} progress)
                   icon-done
                   icon-lock)
          :style {:margin-top 42
                  :color (color :text-gray)}}]]
       [:div text-style (requirement-text req)]
       [:div text-style earn]])))

(defn component []
  (let [mar-top 12
        text    {:style {:color (color :text-gray)}}
        title   {:style {:color (color :blue) :font-size 24 :margin-top mar-top}}
        text2   (assoc-in title [:style :color] (color :text))
        #_{:style {:color (color :text) :font-size 24 :margin-top 12}}]
    [:section.section.disable-selection {:style {:background-color (color :bg)}}
     [:div.container>div.columns
      [:div.flex.column
       [:div {:style {:max-width 600}}
        ;; size
        [:div title "GAME SIZE"]
        [:div.flex {:style {:justify-content "space-between"}}
         [:div text2 "single"]
         [:div title (single-row-size)]]
        (if (db/get :identity)
          [:div.flex {:style {:justify-content "space-between"}}
           [:div text2 "multiplayer"]
           [:div title (multiplayer-row-size)]]
          [:div {:style {:color (color :red), :margin-top mar-top}}
           "Authorize to set up" [:br] " multiplayer"])
        [:br] [:br]
        ;; achivements
        [:div title "ACHIVEMENTS"]
        (achivements-comp achivements text text2)]]

      [:div {:style {:max-width 600}}
       [:div.flex.column.disable-selection
        ;; theme
        [:div title "THEME"]
        (let [theme (theme/get-theme)
              dark  (color (if (= theme :dark) :blue :text))
              light  (color (if (= theme :dark) :text :blue))
              hover (fn [txt hov]
                      #(with-meta {:color txt}
                        {:pseudo {:hover {:color hov}}}))]
          [:div.flex {:style {:justify-content "flex-start"
                              :margin-top 8}}
           [:a  {:on-click #(theme/set-theme :dark)
                 :class (<class (hover dark light))}
            "dark"]
           [:a {:style {:margin-left 22}
                :on-click #(theme/set-theme :light)
                :class (<class (hover light dark))}
            "light"]])
        [:br]
        ;; online statistics
        [:div title "ONLINE STATISTICS"]
        [stats/stats-component]]]]
     [:div {:style {:grid-row-start 6, :align-self "end", :color (color :button)}
            :on-click #(.back (.-history js/window))}
      [:span.icon.is-small>i {:class "fas fa-chevron-circle-left"}]]
     ]))
