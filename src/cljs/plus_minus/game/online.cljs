(ns plus-minus.game.online
  (:require [plus-minus.game.state :as st]
            [plus-minus.app-db :as db]
            [plus-minus.game.board :as b]
            [plus-minus.components.board :as board]
            [plus-minus.multiplayer.contract :as contract]
            [plus-minus.websockets :as ws]
            [plus-minus.game.mock :as mock]
            [plus-minus.components.common :as c]
            [ajax.core :as ajax]
            [clojure.string :as str]))

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
  (db/put! :online-row (db/get :online-row b/row-count-min))
  (generate-state!)
  (db/put! :online-timer nil)
  (db/put! :online-status :idle)
  (db/put! :online-hrz (= 1 (rand-int 2)))
  (swap! db/state dissoc :online-he))

(defn- calc-remain []
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
    (db/put! :online-timer (c/timer-comp remains "Turn timer: "))))

(defn- update-user-stats! [game]
  (db/put! :online-user-stats (:statistics (contract/stats game (db/get :identity)))))

(defmethod has-reply! :end [{{outcome :outcome cause :cause game :game} :data}]
  (initial-state!)
  (update-user-stats! game)
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
    (db/put! :modal (c/info-modal title body))
    (js/clearTimeout (db/get :online-warn-timer))))

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
  (let [state (db/get :online-state)]
      (if (st/valid-move? state mv)
        (db/update! :online-state st/move mv)
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

(defn- top-panel-component []
  [:div.flex.board.space-between
   [:div
    (if (= :playing (db/get :online-status))
      [(db/get :online-timer)]
      [board/game-settings
       :label      (str/replace (str "Board size: " (db/get :online-row)) #"[\s]:" " ")
       :size-range (cons :quick (range b/row-count-min b/row-count-max-excl))
       :state      (:online-state @db/state)
       :on-change  (fn [row-size]
                     (db/put! :online-row row-size)
                     (initial-state!))])]
   [:span.dot {:style {:height 9 :width 9 :border-radius "50%" :margin-top 10,
                       :background-color (if (ws/connected?) "green" "red")}}]
   [:div.tags.has-addons {:style {:margin 3}}
    [:span.tag.is-medium "influence$"]
    [:span.tag.is-info.is-medium {:class "is-light"}
     (db/get-in [:online-user-stats :influence] "..")]]])

(defn- start-new-game! []
  (ws/push-message! :new (db/get :online-row))
  (db/put! :online-status :searching))

(defn- drop-searching-game! []
  (ws/push-message! :drop (db/get :online-row)))

(defn- new-game-panel-component []
  (case (db/get :online-status)
    :playing   [:a.board.play.button.is-danger
                {:on-click #(ws/push-message! :give-up)}
                "give up"]
    :searching [:div
                {:style {:display :flex
                         :flex-direction :column}}
                [:a.board.play.button.is-danger.is-inverted
                 {:on-click drop-searching-game!}
                 "cancel search"]
                [:progress.board.progress.is-small.is-dark
                 {:max 100
                  :style {:margin-bottom "10px"}}]]
    :idle      [:a.board.play.button.is-light
                {:on-click start-new-game!}
                "start new game"]
    [:a.board.play.button {:disabled true} "connecting..."]))

(defn game-component []
  [:section.section>div.container>div.columns
   [:div.flex.column
    [board/scors
     :state   (db/get :online-state)
     :usr-hrz (db/get :online-hrz)
     :he      (db/get :online-he "He")]
    [board/matrix
     :on-click    (fn [turn? _ index]
                    (if turn?
                      (ws/push-message! :move index)
                      (c/info-modal "Mind your manners" "it's not your turn")))
     :game-state  (db/get :online-state)
     :cell-bg     (db/get :online-cell-color)
     :board-width (db/get :online-board-width)
     :user-hrz    (db/get :online-hrz)]]

   [:div.flex.column
    [top-panel-component]
    [new-game-panel-component]
    [mock/mock-buttons]
    [mock/mock-explained]]])

(comment
  (def game
    {:state {:board {:row-size 3,:cells [4 2 -5 9 -8 -7 -5 -5 8]},
             :start 6,:moves [],:hrz-points 0,:vrt-points 0,:hrz-turn true},
     :game-id 0,
     :created (. (js/Date.) (getTime)),
     :updated (. (js/Date.) (getTime)),
     :player1 "bob",:player2 "dumch",
     :player1-hrz true}))
