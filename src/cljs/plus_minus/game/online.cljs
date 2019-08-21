(ns plus-minus.game.online
  (:require [plus-minus.game.state :as st]
            [plus-minus.app-db :as db]
            [plus-minus.components.board :as board]
            [plus-minus.multiplayer.contract :as contract]
            [plus-minus.websockets :as ws]
            [reagent.core :as r]
            [plus-minus.components.common :as c]))

(def statuses #{:playing :searching :idle})

(defn- message [type & [data]]
  (into {} (contract/->Message type (db/get :identity) data)))

(defn- push-message! [type & [data]]
  (ws/send-transit-msg! (message type data)))

(defn initial-state! []
  (db/put! :online-row (db/get :online-row 3))
  (db/put! :online-state
           (update-in
            (st/state-template (db/get :online-row 3))
            [:board :cells]
            #(mapv (fn [_] "X") %)))
  (db/put! :online-timer nil)
  (db/put! :online-status :idle)
  (db/put! :online-hrz (= 1 (rand-int 2)))
  (swap! db/state dissoc :online-he))

(defn- put-timer! [& {remains :remains :or {remains contract/turn-ms}}]
  (db/put! :online-timer (c/timer-comp remains "Turn timer: ")))

(defn- on-end! [{outcome :outcome cause :cause}]
  (initial-state!)
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
    (db/put! :modal (c/info-modal title body))))

(defn- on-state! [{p1h :player1-hrz,
                  p1  :player1, p2 :player2,
                  state :state :as game}]
  (prn "gotstate")
  (let [you     (db/get :identity)
        he      (contract/other-id game you)
        you-hrz (or (and p1h (= you p1)) (and (not p1h) (= you p2)))]
    (when-not (db/get :online-timer) (put-timer!))
    (db/put! :online-he he)
    (db/put! :online-status :playing)
    (db/put! :online-state state)
    (db/put! :online-hrz you-hrz)))

(defn- show-error! [error]
  (let [info (case error
               :invalid-move "Can't move here"
               :not-your-turn "Not your turn"
               :game-doesnt-exist (do (initial-state!)
                                      "Game doesn't exist somehow")
               :invalid-msg "Invalid message to the server"
               :unknown "Something terrbly bad has happend, and noone knows what")]
    (db/put! :modal (c/info-modal "Error" info))))

(defn- on-move! [mv]
  (put-timer!)
  (let [state (db/get :online-state)]
    (if (st/valid-move? state mv)
      (db/update! :online-state st/move mv)
      (push-message! :state))))

(defn on-reply! [{type :reply-type, id :id, data :data}]
  (case type
    :state     (on-state! data)
    :move      (on-move! data)
    :end       (on-end! data)
    :error     (show-error! data)
    :turn-time (put-timer! :remains data)
    :drop      (do
                 (initial-state!)
                 (db/put! :modal (c/info-modal "Success!" "search canceled")))
    :cant-drop (db/put!
                :modal
                (c/info-modal "Fail" "Game is already matched or never exist"))))

(defn- top-panel-component []
  (if (= :playing (db/get :online-status))
    [(db/get :online-timer)]
    [board/game-settings
     :state     (:online-state @db/state)
     :on-change (fn [row-size]
                  (db/put! :online-row row-size))]))

(defn- start-new-game! []
  (push-message! :new (db/get :online-row))
  (db/put! :online-status :searching))

(defn- drop-searching-game! []
  (push-message! :drop (db/get :online-row)))

(defn- bottom-panel-component []
  (case (db/get :online-status)
    :playing   [:a.board.play.button.is-danger
                {:on-click #(push-message! :give-up)}
                "give up"]
    :searching [:div
                {:style {:display :flex
                         :flex-direction :column}}
                [:a.board.play.button.is-danger.is-inverted
                 {:on-click drop-searching-game!}
                 "cancel search"]
                [:progress.board.progress.is-small.is-dark
                 {:max 100}]]
    :idle      [:a.board.play.button.is-light
                {:on-click start-new-game!}
                "start new game"]))

(defn game-component []
  [:section
   [:div.flex.center.column 
    [:div.board [top-panel-component]]
    [board/scors
     :state   (db/get :online-state)
     :usr-hrz (db/get :online-hrz)
     :he      (db/get :online-he "He")]

    [board/matrix
     :on-click  (fn [turn? _ index]
                  (if turn?
                    (push-message! :move index)
                    (c/info-modal "Mind your manners" "it's not your turn")))
     :game-state (db/get :online-state)
     :user-hrz   (db/get :online-hrz)]

    [bottom-panel-component]]])

(comment
  (def game
    {:state {:board {:row-size 3,:cells [4 2 -5 9 -8 -7 -5 -5 8]},
             :start 6,:moves [],:hrz-points 0,:vrt-points 0,:hrz-turn true},
     :game-id 0,
     :created (. (js/Date.) (getTime)),
     :updated (. (js/Date.) (getTime)),
     :player1 "bob",:player2 "dumch",
     :player1-hrz true}))
