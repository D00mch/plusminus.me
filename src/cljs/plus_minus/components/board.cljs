(ns plus-minus.components.board
  (:require [plus-minus.game.board :as b]
            [plus-minus.game.state :as s]
            [plus-minus.components.theme :refer [color]]
            [reagent.core :as r]
            [reanimated.core :as anim]
            [plus-minus.components.common :as c]
            [plus-minus.app-db :as db]))

(def anim-delay 150)
(def anim-time 300)

(defn game-settings
  "on-change - fn [game-size]"
  [& {}]
  (let [active (r/atom false)]
    (fn [& {state :state on-change :on-change size-range :size-range
            label :label :or {label (str "Size: " (s/rows state))}}]
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

(defn scors [& {{hrz-turn :hrz-turn :as s} :state h :usr-hrz you :you he :he
                :or {you "You", he "He"}}]
  (let [his-p     (if h (:vrt-points s) (:hrz-points s))
        your-p    (if h (:hrz-points s) (:vrt-points s))
        you-moved (= hrz-turn (not h))]
    [:div.board.scors.disable-selection
     [:div.flex {:style {:flex-direction "column"}}
      [:div
       {:style {:color (color :blue), :font-size 24}
        :id    "scors-you"
        :class (if you-moved "rotate" "")} your-p]
      [:div {:style {:color (color :blue)}} (or you "You")]]
     [:div.flex {:style {:flex-direction "column"}}
      [:div.flex
       {:style {:color (color :red), :font-size 24, :justify-content "flex-end"}
        :id    "scors-he"
        :class (if you-moved "" "rotate")} your-p]
      [:div {:style {:color (color :red)}} (or he "He")]]]))

(defn cell-id [index] (str "board-cell" index))

(defn- offset-top [el] (+ (.-offsetTop (.. el -offsetParent)) (.-offsetTop el)))
(defn- offset-left [el] (+ (.-offsetLeft (.. el -offsetParent)) (.-offsetLeft el)))

(defn show-info-near-cell! [index text]
  (let [el-id (cell-id index)
        el   (.getElementById js/document el-id)
        el-w (.-offsetWidth el)
        el-h (.-offsetHeight el)
        top  (+ (offset-top el) (* 0.8 el-h))
        left (+ (offset-left el) (* 0.6 el-w))
        left (if (> left (* 0.5 (c/screen-width))) (- left (* 2 el-w)) left)]
    (c/show-top-el!
     [:div.notification.is-small
      {:style {:position "absolute"
               ;:background-color (color :bg)
               :border-color "black"
               :border-style "solid"
               :border-width 1
               :top (str top "px")
               :left left}}
      [:button.delete {:on-click #(db/remove! :common-el)}] text]
     :delay 2000)))

(defn animate-click! [index v turn?]
  (let [cell        (.getElementById js/document (cell-id index))
        cell-w      (.-offsetWidth cell)
        cell-h      (.-offsetHeight cell)
        points      (.getElementById js/document (if turn? "scors-you" "scors-he"))
        points-top  (offset-top points)
        points-left (+ (* 0.8 (.-offsetWidth points)) (offset-left points))
        top         (r/atom (+ (offset-top cell) (* 0.15 cell-h)))
        left        (r/atom (+ (offset-left cell) (* 0.25 cell-w)))]
    (set! (.. cell -style -visibility) "hidden")
    (c/after-delay anim-delay
     (fn []
       (swap! top - (- @top points-top))
       (swap! left - (- @left points-left))))
    (c/show-top-el!
     (let [top-a   (anim/interpolate-to top {:duration anim-time})
           left-a  (anim/spring left {:mass 2 :stiffness 0.3 :duration anim-time})]
       (fn []
         [:div
          {:style {:position "absolute"
                   :opacity "0.3"
                   :color (color (if turn? :blue :red))
                   :font-size 20
                   :top  @top-a
                   :left @left-a}}
          v]))
     :delay (+ anim-time anim-delay))))

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
              hidden (some #{i} moves)
              id     (cell-id i)
              v      (nth cells i)]
          [:div.cell {:style {:margin 6
                              :visibility (when hidden "hidden")
                              :background (cond
                                            (and valid turn) (color :blue)
                                            valid            (color :red)
                                            cell-bg          cell-bg
                                            :else            (color :button))}
                      :id id
                      :key i
                      :on-click #(on-click turn state i)
                      :class (when valid "pulse")}
           [:div.inner.disable-selection
            {:style {:color (cond (and valid turn) (color :text-on-blue)
                                  (and valid)      (color :text-on-red)
                                  :else            (color :text))}}
            v]]))])])
