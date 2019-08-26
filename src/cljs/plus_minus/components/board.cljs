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
                :or {you "You", he "He"}}]
  (let [his-p  (if h (:vrt-points s) (:hrz-points s))
        your-p (if h (:hrz-points s) (:vrt-points s))]
    [:div.board.scors
     [:div.tags.has-addons {:style {:margin 3}}
      [:span.tag.is-light you]
      [:span.tag {:class (if (> his-p your-p) "is-danger" "is-light")} your-p]]
     [:div.tags.has-addons {:style {:margin 3}}
      [:span.tag.is-light he]
      [:span.tag.is-light his-p]]]))

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
          [:div.cell {:style {:margin 4
                              :visibility (when hidden "hidden")
                              :background (when valid (if turn "#209cee" "#ee1f1f"))}
                      :on-click #(on-click turn state i)}
           [:div.inner.disable-selection {:style {:color (when valid "white")}}
            (nth cells i)]]))])])
