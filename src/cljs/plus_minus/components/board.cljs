(ns plus-minus.components.board
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]))

(defn game-settings
  "on-change - fn [game-size]"
  [& {state :state on-change :on-change}]
  [:div.control>div.select {:style {:margin-bottom 16}}
   [:select {:on-change #(on-change (int (.. % -target -value)))
             :value (get-in state [:board :row-size] 4)}
    (for [row-size (range b/row-count-min b/row-count-max)]
      [:option row-size])]])

(defn scors [& {s :state h :usr-hrz you :you he :he
                :or {you "You: ", he "He: "}}]
  [:div.board.scors
   [:div.scor (str you (if h (:hrz-points s) (:vrt-points s)))]
   [:div.scor (str he (if h (:vrt-points s) (:hrz-points s)))]])

(defn matrix
  "on-click - fn [turn? state index]"
  [& {on-click                           :on-click,
      usr-hrz                            :user-hrz
      {{r :row-size,cells :cells} :board
       moves :moves
       hrz-turn :hrz-turn :as state}     :game-state}]
  [:div.board.grid
   (for [y (range r)]
     [:div.row
      (for [x (range r)]
        (let [i      (b/xy->index x y r)
              valid  (s/valid-move? state i)
              turn   (and valid (= usr-hrz hrz-turn))
              hidden (some #{i} moves)]
          [:div.box {:style {:margin 5
                             :visibility (when hidden "hidden")
                             :background (when valid (if turn "#209cee" "#ee1f1f"))}
                     :on-click #(on-click turn state i)}
           [:div.inner {:style {:color (when valid "white")}}
            (nth cells i)]]))])])
