(ns plus-minus.game.online
  (:require [plus-minus.game.state :as st]
            [plus-minus.app-db :as db]
            [plus-minus.game.board :as b]
            [plus-minus.game.statistics :as stats]
            [plus-minus.components.board :as board]
            [plus-minus.components.theme :refer [color]]
            [plus-minus.multiplayer.contract :as contract]
            [plus-minus.websockets :as ws]
            [plus-minus.game.mock :as mock]
            [plus-minus.components.common :as c]
            [ajax.core :as ajax]))

(def statuses #{:playing :searching :idle})

(defmulti has-reply! :reply-type)

(defn- load-user-stats! []
  (ajax/GET "api/restricted/user-stats"
            {:handler (fn [r]
                        (println {:result r})
                        (db/put! :online-user-stats (-> r :statistics)))
             :error-handler nil #_(c/show-snack! "can't load your influence, sorry")}))

(defn- generate-state! []
  (let [row (contract/row-number (db/get :online-row))]
    (db/put! :online-state
             (update-in
              (st/state-template row)
              [:board :cells]
              #(mapv (fn [_] "?") %)))))

(defn initial-state! []
  (load-user-stats!)
  (db/put! :online-row (db/get :online-row :quick))
  (generate-state!)
  (db/put! :online-timer nil)
  (db/put! :online-mocks-shown false)
  (db/put! :online-status :idle)
  (db/put! :online-hrz (= 1 (rand-int 2)))
  (swap! db/state dissoc :online-he))

(defn- calc-remain []
  (contract/calc-turn-ms
   (db/get-in [:online-state :board :row-size] b/row-count-min)))

(defn- max-time-millis []
  (contract/calc-turn-ms
   (db/get-in [:online-state :board :row-size] b/row-count-min)))

(defn- put-timer! [& {remains :remains}]
  (let [remains      (or remains (calc-remain))
        warn-timer   (db/get :online-warn-timer)
        timeout-warn 5000]
    (when (> remains timeout-warn)
      (js/clearTimeout warn-timer)
      (db/put! :online-warn-timer
               (js/setTimeout #(c/play-sound "sound/time-warn.flac")
                              (- remains timeout-warn))))
    (db/put! :online-timer (c/timer-comp remains "turn timer: "))
    (db/put! :online-timer-line (c/line-timer-comp remains (max-time-millis) 100))))

(defn- update-user-stats! [game]
  (db/put! :online-user-stats (:statistics (contract/stats game (db/get :identity)))))

(defmethod has-reply! :end [{{outcome :outcome cause :cause game :game} :data}]
  (let [[title body]
        (case outcome
          :win ["You win!" (case cause
                             :give-up "Your opponent couldn't resist and surrender"
                             :time-out "Time elapsed as your oppenent linger"
                             "By having more points in the end of the game")]
          :draw ["Draw" "This time your opponent was lucky"]
          :lose ["You lose" (case cause
                              :give-up "Sometimes it's better to give up"
                              :time-out "Unfortunately, turn time elapsed"
                              "By having less points in the end of the game")])]
    (update-user-stats! game)
    (stats/init-stats!)
    (db/put! :modal (c/info-modal title body))
    (js/clearTimeout (db/get :online-warn-timer))
    (c/after-delay 1500 #(initial-state!))))

(defmethod has-reply! :state
  [{{p1h :player1-hrz,p1  :player1, p2 :player2,state :state :as game} :data}]
  (when (st/start? state) (c/play-sound "sound/ring-bell.wav"))
  (let [you     (db/get :identity)
        he      (contract/other-id game you)
        you-hrz (or (and p1h (= you p1)) (and (not p1h) (= you p2)))]
    (when-not (db/get :online-timer) (put-timer! ))
    (update-user-stats! game)
    (db/put! :online-he he)
    (db/put! :online-status :playing)
    (db/put! :online-state state)
    (db/put! :online-hrz you-hrz)))

(defmethod has-reply! :move [{mv :data}]
  (put-timer!)
  (let [{{c :cells} :board :as state} (db/get :online-state)]
      (if (st/valid-move? state mv)
        (do
          (board/animate-click!
           mv (nth c mv) (= (db/get :online-hrz) (:hrz-turn state)))
          (c/after-delay
           (+ board/anim-delay board/anim-time)
           #(db/update! :online-state st/move mv)))
        (ws/push-message! :state))))

(defmethod has-reply! :mock [{mock :data}] (mock/on-reply! mock))

(defmethod has-reply! :error [{error :data}]
  (let [info (case error
               :invalid-move "Can't move here"
               :not-your-turn "Not your turn"
               :game-doesnt-exist (do (initial-state!)
                                      "Game doesn't exist somehow")
               :invalid-msg "Invalid message to the server"
               :unknown "Something terrbly bad has happend, and noone knows what")]
    (db/put! :modal (c/info-modal "Error" info))))

(defmethod has-reply! :turn-time [{data :data}] (put-timer! :remains data))

(defmethod has-reply! :drop [{data :data}]
  (initial-state!)
  (db/put! :modal (c/info-modal "Search canceled" (or data "may be next time..."))))

(defmethod has-reply! :cant-drop [_]
  (initial-state!)
  (db/put! :modal (c/info-modal "Fail" "Game is already matched or never exist")))

(defn- start-new-game! []
  (ws/push-message! :new (db/get :online-row))
  (db/put! :online-status :searching))

(defn- drop-searching-game! []
  (ws/push-message! :drop (db/get :online-row)))

(defn- game-cycle-button []
  (let [style {:background-color (color :button)
               :color (color :text)
               :margin-bottom 10}]
    (case (db/get :online-status)
      :playing   [:a.button.is-info
                  {:on-click #(ws/push-message! :give-up)
                   :style (assoc style
                                 :background-color (color :red)
                                 :color (color :text-on-red))}
                  "GIVE UP"]
      :searching [:div
                  {:style {:display :flex
                           :flex-direction :column}}
                  [:a.board.button.is-danger
                   {:on-click drop-searching-game!
                    :style style}
                   "cancel search"]
                  [:progress.board.progress.is-small.is-dark
                   {:max 100
                    :style {:margin-bottom "10px"}}]]
      :idle      [:a.button.is-info
                   {:on-click start-new-game!
                    :style style}
                   "NEW GAME"]
      [:a.board.play.button {:disabled true} "connecting..."])))

(defn- on-click [turn? _ index]
  (if (= :playing (db/get :online-status))
    (if turn?
      (ws/push-message! :move index)
      (board/show-info-near-cell! index "You can't go here!"))
    (board/show-info-near-cell! index "press 'new game' to start")))

(defn- comp-board []
  [board/matrix
   :on-click    on-click
   :game-state  (db/get :online-state)
   :cell-bg     (db/get :online-cell-color)
   :board-width (db/get :online-board-width)
   :user-hrz    (db/get :online-hrz)])

(defn- comp-scors []
  [board/scors
   :state   (db/get :online-state)
   :usr-hrz (db/get :online-hrz)
   :you     (db/get :identity)
   :he      (db/get :online-he "He")])

(defn- comp-buttons []
  (if (db/get :online-mocks-shown)
    [mock/mock-buttons]
    [game-cycle-button]))

(defn- comp-mock-btn []
  [:div {:style {:position "absolute", :top "50%", :left "50%"
                 :transform "translate(-50%, -50%) "}}
   (if (db/get :online-mocks-shown) "X" "$")])

(defn game-component []
  [:div.center-hv {:style {:padding 16}}
   [:div.board {:style
                {:display "grid"
                 :width "100%", :height "100%"
                 :grid-template-columns "1fr 1fr 1fr"
                 :grid-template-rows "16% 5% 1% 58% 15% 5%"}}
    [:div {:style {:grid-column-start 1, :grid-column-end 4, :grid-row-start 1}}
     [comp-scors]]
    [:span.dot {:style {:height 9 :width 9 :border-radius "50%" :margin-top 10,
                        :background-color (if (ws/connected?) "green" "red")
                        :justify-self "center", :align-self "start"
                        :grid-row-start 1, :grid-column-start 2}}]
    [:div {:style {:grid-column-start 1, :grid-column-end 4, :grid-row-start 2}}
     (when (= :playing (db/get :online-status)) [(db/get :online-timer)])]
    [:div {:style {:grid-column-start 1, :grid-column-end 4,
                   :grid-row-start 3, :align-self "center"}}
     (when (= :playing (db/get :online-status)) [(db/get :online-timer-line)])]
    [:div {:style {:grid-row-start 4, :grid-column-start 1, :grid-column-end 4}}
     [comp-board]]
    [:div {:style {:grid-row-start 5, :justify-self "center", :align-self "center"
                   :grid-column-start 1, :grid-column-end 4}}
     [comp-buttons]]
    [:div {:style {:grid-row-start 6, :align-self "end", :color (color :button)}
           :on-click #(.back (.-history js/window))}
     [:span.icon.is-small>i {:class "fas fa-chevron-circle-left"}]]
    [:div {:style
           {:border-radius "50%"
            :background-color (color :blue), :color (color :text-on-blue)
            :grid-column-start 2, :grid-row-start 6
            :width "5vh", :heigh "5vh", :position "relative"
            :padding-top 10, :padding-bottom 10, :justify-self "center"}
           :on-click #(db/update! :online-mocks-shown not)}
     [comp-mock-btn]]
    [:div {:style {:color (color :blue)
                   :margin-bottom -2
                   :justify-self "end", :align-self "end"
                   :grid-row-start 6, :grid-column-start 3}}
     (when (db/get :online-mocks-shown)
       (str "money: $" (db/get-in [:online-user-stats :influence] "..")))]]])

(comment
  (def game
    {:state {:board {:row-size 3,:cells [4 2 -5 9 -8 -7 -5 -5 8]},
             :start 6,:moves [],:hrz-points 0,:vrt-points 0,:hrz-turn true},
     :game-id 0,
     :created (. (js/Date.) (getTime)),
     :updated (. (js/Date.) (getTime)),
     :player1 "bob",:player2 "dumch",
     :player1-hrz true}))
