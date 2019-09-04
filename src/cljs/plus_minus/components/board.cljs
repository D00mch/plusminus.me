(ns plus-minus.components.board
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [reagent.core :as r]
            [plus-minus.components.common :as c]))

(defn game-settings
  "on-change - fn [game-size]"
  [& {}]
  (let [active (r/atom false)]
    (fn [& {state :state on-change :on-change size-range :size-range
            label :label :or {label (str "Board size: " (s/rows state))}}]
      [:div.dropdown
       {:class (str (when (> (c/screen-height) (c/screen-width)) "is-up") " "
                    (when @active "is-active"))
        :on-click #(reset! active (not @active))}
       [:div.dropdown-trigger
        [:button.button {:aria-haspopup "true", :aria-controls "dropdown1"}
         [:span label]
         [:span.icon.is-small
          [:i {:class "fas fa-angle-right", :aria-hidden "true"}]]]]
       [:div {:class "dropdown-menu", :id "dropdown1", :role "menu"}
        [:div.dropdown-content {:style {:max-width 65}}
         (for [row-size size-range]
           ^{:key (str "adi-" row-size)}
           [:a.dropdown-item
            {:on-click (fn [] (on-change row-size))
             :class (when (= row-size (s/rows state)) "is-active")}
            row-size])]]])))

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
       hrz-turn :hrz-turn :as state}     :game-state
      cell-bg                            :cell-bg
      board-width                        :board-width}]
  [:div.board.grid
   {:style {:max-width (or board-width "450px")}}
   (for [y (range r)]
     [:div.row {:key y}
      (for [x (range r)]
        (let [i      (b/xy->index x y r)
              valid  (s/valid-move? state i)
              turn   (and valid (= usr-hrz hrz-turn))
              hidden (some #{i} moves)]
          [:div.cell {:style {:margin 4
                              :visibility (when hidden "hidden")
                              :background (cond
                                            (and valid turn) "#209cee"
                                            valid            "#ee1f1f"
                                            cell-bg          cell-bg
                                            :else            "Gainsboro")}
                      :key i
                      :on-click #(on-click turn state i)
                      :class (when valid "pulse")}
           [:div.inner.disable-selection {:style {:color (when valid "white")}}
            (nth cells i)]]))])])
