(ns plus-minus.components.common
  (:require [clojure.string :as str]
            [plus-minus.app-db :as db]
            [herb.core :refer [<class]]
            [plus-minus.components.theme :refer [color]]
            [reagent.core :as r]))

(defn modal [& {:keys [header body footer style]}]
  (let [theme-style {:background-color (color :bg), :color (color :text)}]
    [:div.modal.is-active.disable-selection
    [:div.modal-background
     {:on-click #(db/remove! :modal)}]
    [:div.modal-card
     (merge {:style {:width "95vw" :max-width "400px"}} style)
     [:header.modal-card-head {:style theme-style} header]
     [:section.modal-card-body {:style theme-style} body]
     [:footer.modal-card-foot {:style theme-style} footer]]]))

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
        [:input.input
         {:style {:background-color "lightgray"
                  :color "gray"}
          :type        type
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

(defn line-timer-comp [millis-remains max diff]
  (fn []
    (r/with-let [time (r/atom millis-remains)]
      (when (> @time 0) (js/setTimeout #(swap! time - diff) diff))
      [:progress.progress.board
       {:value @time :max (- max 100)
        :class (condp < (/ max @time)
                 5     "is-danger"
                 2     "is-warning"
                 "is-success")
        :style {:height 2 :margin 0 :margin-top 5}}])))

(defn show-snack! [text & [time]]
  (js/setTimeout #(db/remove! :snack) (or time 3000))
  (db/put! :snack
           (fn [] [:div.flex.center.column.disable-selection
                   {:style {:background "WhiteSmoke"}}
                   [:div.board
                    {:style {:color "#209cee", :font-size 15}}
                    text]])))

(defn play-sound [path] (.play (js/Audio. path)))

(defn screen-width [] (.. js/document -body -offsetWidth))
(defn screen-height [] (.. js/document -body -offsetHeight))

(defn after-delay [delay f]
  (js/setTimeout f delay))

(defn show-top-el! [el & {delay :delay :or {delay 1500}}]
  (js/clearTimeout (db/get :common-el-timer))
  (db/put! :common-el-timer (after-delay delay #(db/remove! :common-el)))
  (db/put! :common-el (fn [] el)))

(defn clear-cache []
  (.then (.keys js/caches)
         (fn [cs] (.forEach cs #(.delete js/caches %)))))


(defn offset-top [el] (+ (.-offsetTop (.. el -offsetParent)) (.-offsetTop el)))
(defn offset-left [el] (+ (.-offsetLeft (.. el -offsetParent)) (.-offsetLeft el)))
(defn element [id] (.getElementById js/document id))
(defn el-width [el] (.-offsetWidth el))
(defn el-height [el] (.-offsetHeight el))
