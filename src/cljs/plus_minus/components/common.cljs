(ns plus-minus.components.common
  (:require [clojure.string :as str]
            [plus-minus.app-db :as db]
            [reagent.core :as r]))

(defn modal [& {:keys [header body footer style]}]
  [:div.modal.is-active
   [:div.modal-background
    {:on-click #(db/remove! :modal)}]
   [:div.modal-card
    (merge {:style {:width "95vw" :max-width "400px"}} style)
    [:header.modal-card-head header]
    [:section.modal-card-body body]
    [:footer.modal-card-foot footer]]])

(defn info-modal [title body]
  (fn []
    [modal
     :style  {:style {:width "95vw"
                      :max-width "400px"}}
     :header [:div title]
     :body   [:div [:label body]]
     :footer [:div
              [:a.button.is-primary
               {:on-click #(db/remove! :modal)}
               "Close"]]]))

(defn show-info-modal! [title body]
  (db/put! :modal (info-modal title body)))

(defn do-or-close-footer [& {:keys [name on-do styles]}]
  [:div
   [:a.button.is-primary
    {:class (when (get @styles :loading) "is-loading")
     :on-click on-do}
    name]
   [:a.button.is-danger
    {:on-click #(db/remove! :modal)}
    "Cancel"]])

(defn input [& {:keys [type hint on-save on-stop id fields styles]
                :or {type "text"}}]
  (let [stop #(when on-stop (on-stop))
        save #(-> (get @fields id) str str/trim on-save)]
    (fn []
      (prn "styles changed to " @styles)
      [:div.field
       [:div.control
        {:class (get-in @styles [id :class])}
        [:input.input.is-primary
         {:type        type
          :placeholder hint
          :value       (id @fields)
          :auto-focus  (get-in @styles [id :auto-focus])
          :on-blur     save
          :on-change   #(swap! fields assoc id (-> % .-target .-value))
          :on-key-down #(case (.-which %)
                          13 (save)
                          27 (stop)
                          nil)}]
        [:p.is-medium {:style {:color "red"}}
         (get-in @styles [:errors id])]]])))

(defn timer-comp [millis-remains label]
  (fn []
    (r/with-let [time (r/atom (quot (+ millis-remains 500) 1000))]
      (when (> @time 0) (js/setTimeout #(swap! time dec) 1000))
      [:div label @time])))

(defn show-snack! [text & [time]]
  (js/setTimeout #(db/remove! :snack) (or time 3000))
  (db/put! :snack
           (fn [] [:div.flex.center.column
                   {:style {:background "WhiteSmoke"}}
                   [:div.board
                    {:style {:color "#209cee", :font-size 15}}
                    text]])))

(defn play-sound [path]
  (def audio (js/Audio. path))
  (.play audio))
