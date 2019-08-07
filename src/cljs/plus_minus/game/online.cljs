(ns plus-minus.game.online
  (:require [plus-minus.game.state :as s]
            [plus-minus.game.game :as g]
            [plus-minus.app-db :as db]
            [plus-minus.cookies :as cookies]
            [plus-minus.components.board :as board]
            [ajax.core :as ajax]
            [plus-minus.components.common :as c]
            [reagent.core :as r]))

(defn init-waiting-state! []
  (db/put! :online-state
           (update-in
            (s/state-template (db/get :online-row 3))
            [:board :cells]
            #(mapv (fn [_] "X") %))))

(defn game-component []
  [:section.section>div.container>div.content
   [:div {:style {:display :flex
                  :flex-direction :column
                  :flex-wrap :wrap}}
    [board/game-settings
     :state     (:online-state @db/state)
     :on-change #(let [row-size %
                       moves    (-> :game-state db/get :moves seq)]
                   #_(if moves
                     (db/put! :modal (alert-restart row-size))
                     (change-state (s/state-template row-size))))]
    [board/scors
     :state   (:online-state @db/state)
     :usr-hrz true]
    [board/matrix
     :on-click  (fn [turn? state index]
                  #_(if turn?
                    (move state index)
                    (js/alert "Can't make this move")))
     :game-state (:online-state @db/state)
     :user-hrz   true]
    [:a.board.play.button.is-light {:on-click #(js/alert "bob")}
     "start new game!"]
    ]])
