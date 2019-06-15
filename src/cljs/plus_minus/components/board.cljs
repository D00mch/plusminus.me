(ns plus-minus.components.board
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [plus-minus.game.game :as g]
            [plus-minus.app-db :as db]

            [reanimated.core :as anim]
            ))

(defn init-game-state []
  (db/put! :usr-hrz-turn true)
  (remove-watch db/state :bot)
  (add-watch
   db/state :bot
   (fn [key atom old-state new-state]
     (let [{:keys [board] :as state} (:game-state @atom)]
       (cond (-> state s/moves? not)
             (js/setTimeout
              #(do (js/alert (end-game-msg state))
                   (swap! atom assoc :game-state (s/state-template
                                                  (or (:row-size board) 4))))
              1000),
             (not (user-turn state))
             (do (js/setTimeout
                  #(swap! atom assoc :game-state (g/move-bot state))
                  1000))))))
  )

(defn- move [state mv]
  (db/put! :game-state (s/move state mv)))

(defn- user-turn [{hrz :hrz-turn}]
  (= (db/get :usr-hrz-turn) hrz))

(defn- end-game-msg [{:keys [hrz-points vrt-points hrz-turn]}]
  (if (= hrz-points vrt-points)
    "Draw"
    (let [hrz-wins (> hrz-points vrt-points)]
      (if (= (db/get :usr-hrz-turn) hrz-wins)
        "You win"
        "You lose"))))

(defn game-settings []
  [:div.control>div.select {:style {:margin-bottom 16}}
   [:select {:on-change #(let [row-size (int (.. % -target -value))]
                           (db/put! :game-state (s/state-template row-size)))}
    (for [row-size (range b/row-count-min b/row-count-max)]
      [:option row-size])]])

(defn scors []
  [:div.board.scors
   [:div.scor (str "You: " (db/get-in [:game-state :hrz-points]))]
   [:div.scor (str "He: " (db/get-in [:game-state :vrt-points]))]])

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
