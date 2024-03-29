(ns plus-minus.game.bot
  (:require [plus-minus.game.state :as s]
            [plus-minus.game.game :as g]
            [plus-minus.app-db :as db]
            [plus-minus.cookies :as cookies]
            [plus-minus.components.board :as board]
            [plus-minus.components.theme :refer [color]]
            [ajax.core :as ajax]
            [herb.core :refer [<class]]
            [plus-minus.components.common :as c]
            [reagent.core :as r]
            [plus-minus.game.board :as b]))

(defn change-state [state & {sync :sync :or {sync true}}]
  (db/put! :game-state state)
  (cookies/set! :game-state state)
  (when (and sync (db/get :identity))
    (ajax/PUT "api/game/state"
              {:params {:id (db/get :identity)
                        :state state}
               :handler #(db/remove! :game-sync-request)})))

(defn- change-state-with-anim [{{c :cells} :board :as state} index & {sync :sync}]
  (board/animate-click! index (nth c index) (:hrz-turn state))
  (c/after-delay (+ board/anim-delay board/anim-time)
                 #(change-state (s/move state index) :sync sync)))

(defn- move [{{c :cells} :board :as state} mv]
  (if-let [id (db/get :identity)]
    (ajax/PUT
     "api/game/move"
     {:url-params {:id id, :move mv}
      :handler #(change-state-with-anim state mv :sync (db/get :game-sync-request))
      :error-handler (fn [_]
                        (prn :bot "erorr")
                        (db/put! :game-sync-request true)
                        (db/put! :modal (c/info-modal
                          "You need to be online in rating game"
                          (str "You can logout to play without rating. "
                               "This is temporarily, we hired highly trained gnomes to fix this issue. 
")))
                        (fn [_] (change-state (s/move state mv))))})
    (change-state-with-anim state mv)))

(defn- user-turn [{hrz :hrz-turn}]
  (= (db/get :usr-hrz-turn) hrz))

(def ^:private stats-handler
  #(->> % :statistics (db/put! :game-statistics)))

(defn- send-end-game! [state usr-gave-up]
  (when (db/get :identity)
    (ajax/PUT "api/game/end"
              {:params {:id      (db/get :identity)
                        :state   state
                        :usr-hrz true
                        :give-up usr-gave-up}
               :handler stats-handler
               })))

(defn- end-game-msg [{:keys [hrz-points vrt-points hrz-turn]}]
  (if (= hrz-points vrt-points)
    "Draw"
    (let [hrz-wins (> hrz-points vrt-points)]
      (if (= (db/get :usr-hrz-turn) hrz-wins)
        "You win"
        "You lose"))))

(defn- after-delay [f]
  (c/after-delay 1250 f))

(defn- reset-watchers!
  "watcher make moves, resets game on end and ivalid state"
  []
  (remove-watch db/state :bot)
  (add-watch
   db/state :bot
   (fn [key atom old-state new-state]
     (when-not (= (:game-state old-state) (:game-state new-state))
       (let [{:keys [board] :as state} (:game-state @atom)
             reset-game #(change-state (s/state-template
                                        (or (:row-size board)
                                            b/row-count-min)))]
         (cond (-> state s/valid-state? not)
               (do (c/show-info-modal! "Game resets" "due to ivalid game-state")
                   (reset-game))

               (-> state s/moves? not)
               (after-delay
                #(do (c/show-info-modal! "Game end" (end-game-msg state))
                     (send-end-game! state false)
                     (after-delay (fn [] (reset-game)))))

               (not (user-turn state))
               (after-delay
                #(move state (-> (g/move-bot-safe state 2) :moves last)))))))))

(defn- load-stats! []
  (if-let [id (db/get :identity)]
    (ajax/GET "api/game/statistics"
              {:params {:id id}
               :handler stats-handler})
    (db/remove! :game-statistics)))

(defn- load-game-state! []
  (let [id    (db/get :identity)
        state (or (cookies/get :game-state)
                  (s/state-template b/row-count-min))]
    (if id
      (ajax/GET "api/game/state"
                {:handler #(-> % :state change-state)
                 :params {:id id}
                 :error-handler #(let [{message :message} (get % :response)]
                                   (change-state state))})
      (change-state state))))

(defn init-game-state! []
  (db/put! :usr-hrz-turn true)
  (load-stats!)
  (load-game-state!)
  (reset-watchers!))

(defn alert-restart [row-size]
  (fn []
    [c/modal
     :header [:div "Admit defeat?"]
     :body [:div [:label "You started the game and there are free moves to make, so if you restart the game it will count as defeat."]]
     :footer (c/do-or-close-footer
              :styles (r/atom {:id {:auto-focus true}})
              :name "Defeat"
              :on-do (fn []
                       (db/remove! :modal)
                       (send-end-game! (db/get :game-state) true)
                       (change-state (s/state-template row-size))))]))

(defn game-stats []
  (let [id    (db/get :identity)
        stats (db/get :game-statistics)
        iq    (:iq stats)]
    (cond
      (not id)
      [:a {:class (<class #(with-meta {:color (color :blue)}
                             {:pseudo {:hover {:color (color :text)}}}))
           :href "#/user"}
       "authorize!"]
      (> iq 0) [:div {:style {:color (color :blue)}}
                (str "iq: " iq)])))

(defn on-click [turn? state index]
  (if turn?
    (move state index)
    (board/show-info-near-cell! index "Can't make this move")))

(defn game-component [& {usr-hrz :usr-hrz :or {usr-hrz true}}]
  [:div.flex {:style
              {:justify-content "center"
               :padding 4
               :align-items "center"
               :width "100vw"}}
   [:div.board.flex.column
    {:style {:justify-content "space-between", :height "100vh"}}
    [:div.flex.disable-selection {:style {:flex-direction "column"}}
     [board/scors
      :state   (db/get :game-state)
      :usr-hrz usr-hrz
      :you     (db/get :identity)
      :he      "IQ rater"]
     [game-stats]]
    [board/matrix
     :on-click  on-click
     :game-state (db/get :game-state)
     :user-hrz   usr-hrz]
    [:div {:style {:grid-row-start 6, :align-self "end", :color (color :button)}
           :on-click #(.back (.-history js/window))}
     [:span.icon.is-small>i {:class "fas fa-chevron-circle-left"}]]]])
