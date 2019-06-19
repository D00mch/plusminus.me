(ns plus-minus.components.board
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [plus-minus.game.game :as g]
            [plus-minus.app-db :as db]
            [plus-minus.cookies :as cookies]
            [ajax.core :as ajax]

            [reanimated.core :as anim]

            [plus-minus.components.common :as c]
            [reagent.core :as r]))

(defn- change-state [state]
  (db/put! :game-state state)
  (cookies/set! :game-state state)
  (when (db/get :identity)
    (ajax/PUT "api/game/state"
              {:params {:id (db/get :identity)
                        :state state}
               :handler #()})))

(defn- move [state mv]
  (change-state (s/move state mv)))

(defn- user-turn [{hrz :hrz-turn}]
  (= (db/get :usr-hrz-turn) hrz))

(def ^:private stats-handler
  #(->> % :statistics (db/put! :game-statistics)))

(defn- send-end-game [state usr-gave-up]
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
  (js/setTimeout f 1000))

(defn- reset-watchers!
  "watcher make moves, resets game on end and ivalid state"
  []
  (remove-watch db/state :bot)
  (add-watch
   db/state :bot
   (fn [key atom old-state new-state]
     (let [{:keys [board] :as state} (:game-state @atom)
           reset-game #(change-state (s/state-template
                                      (or (:row-size board) 4)))]
       (cond (-> state s/valid-state? not)
             (do (js/alert "Game resets due to ivalid game-state")
                 (reset-game))

             (-> state s/moves? not)
             (after-delay
              #(do (js/alert (end-game-msg state))
                   (send-end-game state false)
                   (reset-game)))

             (not (user-turn state))
             (after-delay #(change-state (g/move-bot state))))))))

(defn- load-stats! [id]
  (ajax/GET "api/game/statistics"
            {:params {:id id}
             :handler stats-handler}))

(defn init-game-state []
  (db/put! :usr-hrz-turn true)
  (reset-watchers!)
  (if-let [cookie-state (cookies/get :game-state)]
    (change-state cookie-state)
    (ajax/GET "api/game/state"
              {:handler #(-> % :state change-state)
               :params {:id (db/get :identity)}
               :error-handler #(let [{message :message} (get % :response)]
                                 (change-state (s/state-template 4)))})))

(defn alert-form [row-size]
  (let [styles (r/atom {:id {:auto-focus true}})]
    (fn []
     [c/modal
      :style  {:style {:width 400}}
      :header [:div "Admit defeat?"]
      :body [:div [:label "You started the game and there are free moves to make, so if you restart the game it will count as defeat."]]
      :footer (c/do-or-close-footer
               :styles styles
               :name "Defeat"
               :on-do (fn []
                        (db/remove! :modal)
                        (send-end-game (db/get :game-state) true)
                        (change-state (s/state-template row-size))))])))

(defn game-settings []
  [:div.control>div.select {:style {:margin-bottom 16}}
   [:select {:on-change #(let [row-size (int (.. % -target -value))
                               moves    (-> :game-state db/get :moves seq)]
                           (if moves
                             (db/put! :modal (alert-form row-size))
                             (db/put! :game-state (s/state-template row-size))))
             :value (db/get-in [:game-state :board :row-size] 4)}
    (for [row-size (range b/row-count-min b/row-count-max)]
      [:option row-size])]])

(defn scors []
  [:div.board.scors
   [:div.scor (str "You: " (db/get-in [:game-state :hrz-points]))]
   [:div.scor (str "He: " (db/get-in [:game-state :vrt-points]))]])

(defn game-stats []
  (let [id    (db/get :identity)
        stats (db/get :game-statistics)]
    (cond (and id (not stats)) (load-stats! id)
          (and (not id))       (db/remove! :game-statistics))
    (if stats
      [:div.board.stats
       [:div.scor (str "Win: "  (:win stats))]
       [:div.scor (str "Lose: " (:lose stats))]
       [:div.scor (str "Draw: " (:draw stats))]]
      [:div.board.stats [:label "Authorize to see statistics"]])))

(defn game-matrix [& {usr-hrz :usr-hrz :or {usr-hrz true}}]
  (let [{{r :row-size, cells :cells :as board} :board
         start :start
         moves :moves
         hrz-turn :hrz-turn :as state} (:game-state @db/state)
        {xl :x yl :y} (b/coords r (s/last-move state))]
    [:div.board.grid
     (for [y (range r)]
       [:div.row
        (for [x (range r)]
          (let [i      (b/xy->index x y r)
                valid  (s/valid-move? state i)
                turn   (and valid (= usr-hrz hrz-turn))
                hidden (some #{i} moves)]
            [:div.box {:style {:margin 5
                               ;;:opacity 0.3
                               :visibility (when hidden "hidden")
                               :background (when valid "#209cee")}
                       :on-click #(if turn
                                    (move state i)
                                    (js/alert "Can't make this move!"))}
             [:div.inner {:style {:color (when valid "white")}}
              (nth cells i)]]))])]))
